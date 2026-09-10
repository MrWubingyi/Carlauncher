package com.example.carlauncher.someip;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.InetAddress;

/**
 * W37 WP1 第三步草稿 · vSomeIP Client JNI 桥。
 *
 * 角色：Android = vSomeIP Client + 数据发送方；调用 Ubuntu Service 0x1111/0x2222 的
 * Method 0x1001 SetVehicleState（payload 16 B，见 {@link SomeipPayloadCodec}）。
 *
 * 生命周期约定（WP1 第三步）：由调用方 Service 管理 —— Service 绑定/启动后
 * {@link #start(String)}，销毁时 {@link #stop()}；10 Hz 发送由现有 DataSource 触发
 * {@link #buildAndSend(int, long, int, int, int)}。回调经主线程 Handler 分发。
 *
 * 接入前：config json 需从 assets 拷到 filesDir 后传入 start()；插件 .so 随 APK 打包。
 */
public final class VsomeipClient {

    private static final String TAG = "VSOMEIP_JNI";

    static {
        // CMake links the monolithic core containing configuration and service discovery.
        loadQuietly("vsomeip3");
        System.loadLibrary("carlauncher");
        Log.i(TAG, "System.loadLibrary ok");
    }

    private static void loadQuietly(String name) {
        try {
            System.loadLibrary(name);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "loadLibrary(" + name + ") failed", e);
        }
    }

    // ---- native 回调 kind（与 carlauncher.cpp 的 JavaEventKind 对齐）----
    private static final int EVT_REGISTERED    = 0;
    private static final int EVT_AVAILABLE     = 1;
    private static final int EVT_UNAVAILABLE   = 2;
    private static final int EVT_RESPONSE_OK   = 3;
    private static final int EVT_RESPONSE_ERROR = 4;
    private static final int EVT_STOPPED       = 5;

    public interface Listener {
        default void onRegistered() {}
        void onAvailable(boolean available);
        void onResponse(boolean ok, int returnCode);
        default void onStopped() {}
    }

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static volatile Listener sListener;
    private static AndroidNetworkMonitor networkMonitor;
    private static WifiManager.MulticastLock multicastLock;

    private VsomeipClient() {}

    // ---- JNI ----
    public static native boolean nativeStart(String configPath);
    public static native void nativeStop();
    /** True means submitted to vSomeIP, not delivered or acknowledged by the peer. */
    public static native boolean nativeSendState(byte[] payload);
    public static native boolean nativeIsAvailable();
    static native void nativeNetworkState(String address, String iface, boolean available, boolean multicastRoute);

    // ---- 高层入口 ----
    /** Service 绑定后调用；configPath = filesDir 下的 vsomeip-client.json 绝对路径。 */
    public static synchronized boolean start(String configPath) {
        return nativeStart(configPath);
    }

    /** Starts a platform observer before native routing. Repeated starts retain one observer. */
    public static synchronized boolean start(Context context, String configPath) throws Exception {
        if (networkMonitor != null) return nativeStart(configPath);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (FileInputStream in = new FileInputStream(configPath)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
        }
        JSONObject config = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        JSONObject sd = config.optJSONObject("service-discovery");
        boolean discoveryEnabled = sd == null || sd.optBoolean("enable", true);
        InetAddress multicast = discoveryEnabled
                ? InetAddress.getByName(sd == null ? "224.244.224.245" : sd.optString("multicast", "224.244.224.245")) : null;
        AndroidNetworkMonitor monitor = new AndroidNetworkMonitor(context.getApplicationContext(),
                config.getString("unicast"), multicast, VsomeipClient::nativeNetworkState);
        WifiManager.MulticastLock lease = null;
        boolean started = false;
        try {
            if (discoveryEnabled) {
                WifiManager wifi = context.getApplicationContext().getSystemService(WifiManager.class);
                if (wifi != null) {
                    lease = wifi.createMulticastLock("carlauncher-someip-sd");
                    lease.setReferenceCounted(false);
                    lease.acquire();
                    Log.i(TAG, "SD multicast lock acquired");
                }
            }
            monitor.start();
            if (!nativeStart(configPath)) return false;
            networkMonitor = monitor;
            multicastLock = lease;
            started = true;
            return true;
        } finally {
            if (!started) {
                try { monitor.close(); }
                finally { if (lease != null && lease.isHeld()) lease.release(); }
            }
        }
    }

    public static synchronized void stop() {
        try {
            if (networkMonitor != null) networkMonitor.close();
        } finally {
            networkMonitor = null;
            try { nativeStop(); }
            finally {
                if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
                multicastLock = null;
                Log.i(TAG, "SD multicast lock released");
            }
        }
    }

    /** 由 10 Hz DataSource 线程调用：编码 16 B payload 并发送。 */
    public static boolean buildAndSend(int seq, long timestampMs, int speedKph,
                                    int gear, int dataStatus) {
        byte[] payload = SomeipPayloadCodec.encode(seq, timestampMs, speedKph, gear, dataStatus);
        boolean submitted = nativeSendState(payload);
        Log.d(TAG, (submitted ? "submitted" : "skipped") + " seq=" + Integer.toUnsignedLong(seq) + " speed=" + speedKph
                + " gear=" + gear + " dataStatus=" + dataStatus);
        return submitted;
    }

    public static boolean isAvailable() {
        return nativeIsAvailable();
    }

    public static synchronized void setListener(Listener l) {
        sListener = l;
    }

    public static synchronized void clearListener(Listener listener) {
        if (sListener == listener) sListener = null;
    }

    /** native 线程回调入口；只做转发，不碰 UI。 */
    @SuppressWarnings("unused")
    public static void onNativeEvent(int kind, int arg) {
        final Listener l = sListener;
        switch (kind) {
            case EVT_REGISTERED:
                Log.i(TAG, "native: registered");
                UI.post(() -> { if (l != null && l == sListener) l.onRegistered(); });
                break;
            case EVT_AVAILABLE:
                UI.post(() -> { if (l != null && l == sListener) l.onAvailable(true); });
                break;
            case EVT_UNAVAILABLE:
                UI.post(() -> { if (l != null && l == sListener) l.onAvailable(false); });
                break;
            case EVT_RESPONSE_OK:
                UI.post(() -> { if (l != null && l == sListener) l.onResponse(true, arg); });
                break;
            case EVT_RESPONSE_ERROR:
                Log.w(TAG, "native: response error code=" + arg);
                UI.post(() -> { if (l != null && l == sListener) l.onResponse(false, arg); });
                break;
            case EVT_STOPPED:
                Log.i(TAG, "native: stopped");
                UI.post(() -> { if (l != null && l == sListener) l.onStopped(); });
                break;
            default:
                Log.w(TAG, "native: unknown event kind=" + kind);
        }
    }
}
