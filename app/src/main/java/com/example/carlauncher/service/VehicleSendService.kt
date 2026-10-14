package com.example.carlauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.carlauncher.ui.home.ComposeMainActivity
import com.example.carlauncher.R
import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.VehicleState
import com.example.vehicle.someip.transport.NativeVehicleTransport
import com.example.vehicle.someip.adapter.SomeipConnectionMonitor
import com.example.carlauncher.data.someip.VSomeIpDataSource
import com.example.vehicle.someip.android.VSomeIpNativeTransport
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Foreground owner of the remote vehicle Event subscription and latest UI snapshot. */
open class VehicleSendService : Service() {

    private val binder: IBinder = LocalBinder()
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    // vSomeIP 生命周期专用单线程执行器，避免 native start/stop 阻塞主线程。
    private var vsomeipExecutor: ExecutorService? = null
    private val someipMonitor: SomeipConnectionMonitor = SomeipConnectionMonitor()
    private var nativeTransport: NativeVehicleTransport? = null
    private var vsomeipDataSource: VSomeIpDataSource? = null

    open val someipStatus: SomeipConnectionMonitor.Snapshot?
        get() {
            return if (vsomeipDataSource != null) vsomeipDataSource!!.connectionMonitor!!.snapshot()
            else someipMonitor.snapshot()
        }

    @Volatile private var stopping: Boolean = false
    @Volatile private var latestVehicleState: VehicleState? = null
    @Volatile private var sourceStatus: DataSourceStatus? = DataSourceStatus.STOPPED
    private var stateListener: StateListener? = null

    /** 车辆数据源监听器，处理数据更新和状态变更 */
    private val dataListener: VehicleDataSource.Listener =
        object : VehicleDataSource.Listener {
            override fun onStateChanged(state: VehicleState?) {
                if (stopping || state == null) {
                    return
                }

                latestVehicleState = state
                notifyStateChanged()
            }

            override fun onSourceStatusChanged(status: DataSourceStatus?) {
                if (stopping || status == null) {
                    return
                }

                sourceStatus = status
                if (!stopping) {
                    notifyStateChanged()
                }
            }

            override fun onError(exception: Exception?) {
                if (stopping) return
                Log.e(TAG, "Vehicle data source error", exception)
                sourceStatus = DataSourceStatus.ERROR
                if (!stopping) {
                    notifyStateChanged()
                }
            }
        }

    open val dataValidity: DataValidity?
        get() {
            val state = getLatestVehicleState()
            if (null != state) {
                return state!!.validity
            }
            return DataValidity.INCOMPLETE
        }

    // The native client is process-wide; serialize lifecycle across Service recreation too.
    private class SomeipLifecycle {
        companion object {
            internal val EXECUTOR: ExecutorService? = Executors.newSingleThreadExecutor()
        }
    }

    /** 服务状态监听接口 */
    fun interface StateListener {
        /** 当连接状态或数据源状态发生变化时回调 */
        fun onStateChanged()
    }

    /** 用于与 Activity 绑定的 Binder 类 */
    open inner class LocalBinder : Binder() {
        open val service: VehicleSendService?
            get() {
                return this@VehicleSendService
            }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate id=" + System.identityHashCode(this))

        // 创建通知渠道并启动前台服务
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 初始化 vSomeIP Event 数据源。
        vsomeipExecutor = SomeipLifecycle.EXECUTOR
        sourceStatus = DataSourceStatus.CONNECTING

        // 固定生命周期所有权：Service 创建时初始化对象，但不重复启动
        nativeTransport = VSomeIpNativeTransport(this)
        vsomeipDataSource = VSomeIpDataSource(nativeTransport, someipMonitor)

        startVsomeipClient()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被系统杀死后尝试重启
        return Service.START_STICKY
    }

    /** 调度 vSomeIP 启动到专用单线程执行器（不占主线程；native start 内部不再阻塞）。 */
    private fun startVsomeipClient() {
        if (stopping) return
        notifyStateChanged()
        val executor = vsomeipExecutor
        val source = vsomeipDataSource
        if (executor == null || executor.isShutdown || source == null) {
            handleStartFailure(IllegalStateException("Vehicle source or lifecycle executor unavailable"))
            return
        }
        try {
            executor.execute {
                if (stopping) return@execute
                val failure = try {
                    source.start(dataListener)
                    check(source.isRunning) { "Vehicle data source did not start" }
                    null
                } catch (error: Exception) {
                    error
                } catch (error: LinkageError) {
                    error
                }
                if (failure != null) {
                    // Cleanup stays on the lifecycle executor, before any subsequent start.
                    try {
                        source.stop()
                    } catch (cleanupError: Exception) {
                        if (cleanupError !== failure) failure.addSuppressed(cleanupError)
                    } catch (cleanupError: LinkageError) {
                        if (cleanupError !== failure) failure.addSuppressed(cleanupError)
                    }
                    handleStartFailure(failure)
                } else if (!stopping) {
                    Log.i(TAG, "Vehicle data source started (on lifecycle executor)")
                    notifyStateChanged()
                }
            }
        } catch (exception: RejectedExecutionException) {
            handleStartFailure(exception)
        }
    }

    /** One terminal Service result for scheduling, initialization and native-load failures. */
    private fun handleStartFailure(error: Throwable) {
        if (stopping) return
        Log.e(TAG, "Vehicle data source start failed", error)
        latestVehicleState = null
        sourceStatus = DataSourceStatus.ERROR
        // Scheduling can fail before the adapter starts; cleanup can also reset the monitor.
        if (someipMonitor.snapshot().state == SomeipConnectionMonitor.State.STOPPED) {
            someipMonitor.start()
        }
        someipMonitor.startFailed()
        notifyStateChanged()
    }

    /** 获取最新的车辆状态 */
    open fun getLatestVehicleState(): VehicleState? {
        if (latestVehicleState != null) {
            return latestVehicleState
        }
        return if (vsomeipDataSource != null) vsomeipDataSource!!.latestState else null
    }

    /** 获取数据源状态 */
    open fun getSourceStatus(): DataSourceStatus? {
        // Source callbacks update this value; Service startup failures must not be shadowed
        // by a data source left in CONNECTING after throwing during initialization.
        return sourceStatus
    }

    /** 设置状态监听器 */
    open fun setStateListener(listener: StateListener?) {
        stateListener = listener
        notifyStateChanged()
    }

    /** 清除状态监听器 */
    open fun clearStateListener(listener: StateListener?) {
        if (stateListener === listener) {
            stateListener = null
        }
    }

    /** 通知状态已变更，在主线程执行 */
    private fun notifyStateChanged() {
        if (stopping) {
            return
        }

        mainHandler.post {
            val listener = stateListener
            if (!stopping && listener != null) {
                listener!!.onStateChanged()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.i(TAG, "Service onBind id=" + System.identityHashCode(this))
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "Service onUnbind id=" + System.identityHashCode(this))
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.i(TAG, "Service onDestroy id=" + System.identityHashCode(this))
        stopping = true
        someipMonitor.stop()
        stateListener = null
        mainHandler.removeCallbacksAndMessages(null)

        // 停止 vSomeIP：排队到同一单线程执行器（与 start 保持先后顺序），
        // 避免主线程被 native stop 的 join 阻塞；native stop 幂等（未启动则直接返回）。
        val vsomeipExecutorToStop = vsomeipExecutor
        vsomeipExecutor = null
        if (vsomeipExecutorToStop != null) {
            try {
                vsomeipExecutorToStop!!.execute {
                    try {
                        if (vsomeipDataSource != null) {
                            vsomeipDataSource!!.stop()
                        }
                        Log.i(TAG, "SOMEIP_RELEASED id=" + System.identityHashCode(this))
                    } catch (exception: Exception) {
                        Log.e(TAG, "vsomeip stop failed", exception)
                    } catch (error: LinkageError) {
                        // Native loading can fail during cleanup; VM failures must propagate.
                        Log.e(TAG, "vsomeip stop failed", error)
                    }
                }
            } catch (exception: RejectedExecutionException) {
                Log.e(TAG, "schedule vsomeip stop rejected", exception)
            }
        }

        sourceStatus = DataSourceStatus.STOPPED
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** 创建前台服务通知 */
    private fun createNotification(): Notification? {
        val notificationIntent = Intent(this, ComposeMainActivity::class.java)
        notificationIntent.setFlags(
            (Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )

        val contentIntent =
            PendingIntent.getActivity(
                this,
                1001,
                notificationIntent,
                (PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vehicle data service")
            .setContentText("Subscribing to vehicle state events")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** 创建通知渠道（Android 8.0+） */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Vehicle data",
                NotificationManager.IMPORTANCE_LOW,
            )

        val manager = getSystemService<NotificationManager>(NotificationManager::class.java)
        if (manager != null) {
            manager!!.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG: String = "VEHICLE_SERVICE"
        private const val CHANNEL_ID: String = "vehicle_send_channel"
        private const val NOTIFICATION_ID: Int = 1001
    }
}
