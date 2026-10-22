package com.zqautomotive.launcher.service

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
import com.zqautomotive.vehicle.control.*
import com.zqautomotive.launcher.ui.home.ComposeMainActivity
import com.zqautomotive.launcher.R
import com.zqautomotive.launcher.data.DataSourceStatus
import com.zqautomotive.launcher.data.VehicleDataSource
import com.zqautomotive.launcher.data.VehicleEventSourceFactory
import com.zqautomotive.launcher.data.VehicleServiceDependencies
import com.zqautomotive.launcher.model.DataValidity
import com.zqautomotive.launcher.model.VehicleState
import com.zqautomotive.vehicle.someip.adapter.SomeipConnectionMonitor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/** Foreground owner of the remote vehicle Event subscription and latest UI snapshot. */
open class VehicleSendService : Service() {

    private val binder: IBinder = LocalBinder()
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    // Serialize source lifecycle without blocking the main thread.
    private var sourceExecutor: ExecutorService? = null
    private val someipMonitor: SomeipConnectionMonitor = SomeipConnectionMonitor()
    @Volatile private var dataSource: VehicleDataSource? = null

    open val someipStatus: SomeipConnectionMonitor.Snapshot?
        get() = someipMonitor.snapshot()

    @Volatile private var stopping: Boolean = false
    @Volatile private var latestVehicleState: VehicleState? = null
    @Volatile private var sourceStatus: DataSourceStatus? = DataSourceStatus.STOPPED
    private var stateListener: StateListener? = null

    /** 车辆数据源监听器，处理数据更新和状态变更 */
    private val dataListener: VehicleDataSource.Listener =
        object : VehicleDataSource.Listener {
            override fun onControlChanged() = notifyStateChanged()

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
                if (status != DataSourceStatus.CONNECTED) latestVehicleState = null
                if (!stopping) {
                    notifyStateChanged()
                }
            }

            override fun onError(exception: Exception?) {
                if (stopping) return
                Log.e(TAG, "Vehicle data source error", exception)
                sourceStatus = DataSourceStatus.ERROR
                latestVehicleState = null
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

    // Preserve lifecycle ordering across Service recreation, including queued cleanup.
    private class SourceLifecycle {
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

        try {
            val dependencies = application as VehicleServiceDependencies
            startDataSource(dependencies.vehicleEventSourceFactory, SourceLifecycle.EXECUTOR)
        } catch (error: Exception) {
            handleStartFailure(error)
        } catch (error: LinkageError) {
            handleStartFailure(error)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 服务被系统杀死后尝试重启
        return Service.START_STICKY
    }

    /** Inject the composition boundary; creation and lifecycle run on the same serial executor. */
    internal fun startDataSource(factory: VehicleEventSourceFactory, executor: ExecutorService?) {
        if (stopping) return
        sourceExecutor = executor
        sourceStatus = DataSourceStatus.CONNECTING
        notifyStateChanged()
        if (executor == null || executor.isShutdown) {
            handleStartFailure(IllegalStateException("Vehicle lifecycle executor unavailable"))
            return
        }
        try {
            executor.execute {
                if (stopping) return@execute
                var source: VehicleDataSource? = null
                val failure = try {
                    source = factory.create(this, someipMonitor)
                    dataSource = source
                    if (stopping) return@execute
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
                        source?.stop()
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

    open fun controlSnapshot(): VehicleControlSnapshot =
        if (stopping) VehicleControlSnapshot() else (dataSource as? VehicleControl)?.controlSnapshot() ?: VehicleControlSnapshot()

    /** Runs on the same executor as source start/stop; the source rechecks all gates at send time. */
    open fun requestControl(action: VehicleAction) {
        if (stopping) return
        try {
            sourceExecutor?.execute {
                if (!stopping) {
                    (dataSource as? VehicleControl)?.requestControl(action)
                    notifyStateChanged()
                }
            }
        } catch (error: RejectedExecutionException) {
            Log.e(TAG, "Control executor unavailable", error)
        }
    }

    /** 获取最新的车辆状态 */
    open fun getLatestVehicleState(): VehicleState? = latestVehicleState

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

        // Queue release after any in-flight factory/start task, including Service recreation.
        val executorToStop = sourceExecutor
        sourceExecutor = null
        if (executorToStop != null) {
            try {
                executorToStop.execute {
                    try {
                        dataSource?.stop()
                        Log.i(TAG, "SOMEIP_RELEASED id=" + System.identityHashCode(this))
                    } catch (exception: Exception) {
                        Log.e(TAG, "Vehicle source stop failed", exception)
                    } catch (error: LinkageError) {
                        // Native loading can fail during cleanup; VM failures must propagate.
                        Log.e(TAG, "Vehicle source stop failed", error)
                    } finally {
                        dataSource = null
                    }
                }
            } catch (exception: RejectedExecutionException) {
                Log.e(TAG, "Schedule vehicle source stop rejected", exception)
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
