package com.example.carlauncher.someip;

import android.content.Context;
import android.util.Log;

import com.example.carlauncher.model.VehicleState;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * NativeVehicleTransport 的 vSomeIP JNI 实现类。
 * 持有 VsomeipClient，实现 JNI 回调转发、Event 解码校验与状态通知。
 */
public class VSomeIpNativeTransport implements NativeVehicleTransport, VsomeipClient.Listener {

    private static final String TAG = "VSOMEIP_TRANSPORT";

    private final Context context;
    private volatile Listener listener;
    private volatile boolean stopping;
    private long lastEventTimestamp;
    private long lastEventSequence = -1;

    public VSomeIpNativeTransport(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    @Override
    public synchronized boolean start() {
        stopping = false;
        try {
            File config = prepareConfigFile();
            VsomeipClient.setListener(this);
            boolean ok = VsomeipClient.start(context, config.getAbsolutePath());
            Log.i(TAG, "VSomeIpNativeTransport start ok=" + ok);
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start VSomeIpNativeTransport", e);
            Listener l = listener;
            if (l != null) {
                l.onError(e);
            }
            return false;
        }
    }

    @Override
    public synchronized void stop() {
        stopping = true;
        try {
            VsomeipClient.clearListener(this);
            VsomeipClient.stop();
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native library not available during stop", e);
        } finally {
            lastEventTimestamp = 0;
            lastEventSequence = -1;
            Log.i(TAG, "VSomeIpNativeTransport stopped");
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            return VsomeipClient.isAvailable();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    @Override
    public synchronized void setListener(Listener listener) {
        this.listener = listener;
    }

    // ---- VsomeipClient.Listener 回调实现 ----

    @Override
    public void onAvailable(boolean available) {
        if (stopping) {
            return;
        }
        if (!available) {
            lastEventTimestamp = 0;
            lastEventSequence = -1;
        }
        Listener l = listener;
        if (l != null) {
            l.onAvailabilityChanged(available);
        }
    }

    @Override
    public void onVehicleEvent(byte[] payload) {
        if (stopping) {
            return;
        }
        Listener l = listener;
        if (l == null) {
            return;
        }
        try {
            VehicleState state = VehicleEventCodec.decode(payload);
            // 时间戳与序列号校验，防止乱序或重复数据重放
            if (state.getTimestampMs() < lastEventTimestamp
                    || (state.getTimestampMs() == lastEventTimestamp
                        && state.getSequence() <= lastEventSequence)) {
                return;
            }
            lastEventTimestamp = state.getTimestampMs();
            lastEventSequence = state.getSequence();
            l.onVehicleState(state);
        } catch (Exception e) {
            Log.w(TAG, "Invalid vehicle event payload", e);
            l.onError(e);
        }
    }

    @Override
    public void onResponse(boolean ok, int returnCode) {
        if (stopping) {
            return;
        }
        if (!ok) {
            Listener l = listener;
            if (l != null) {
                l.onError(new Exception("SOME/IP response error code=" + returnCode));
            }
        }
    }

    @Override
    public void onStopped() {
        if (stopping) {
            return;
        }
        Listener l = listener;
        if (l != null) {
            l.onAvailabilityChanged(false);
        }
    }

    // ---- 配置与网络工具方法 ----

    private File prepareConfigFile() throws Exception {
        final String configName = "vsomeip-client.json";
        final File dir = new File(context.getFilesDir(), "vsomeip");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("mkdir vsomeip dir failed");
        }
        final File config = new File(dir, configName);
        if (!config.exists()) {
            copyAssetToFile(configName, config);
        }

        final String localIp = findLocalIpv4();
        if (localIp == null) {
            throw new IllegalStateException("no usable IPv4 address");
        }
        rewriteUnicast(config, localIp);
        return config;
    }

    private void copyAssetToFile(String assetName, File target) throws Exception {
        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    static void rewriteUnicast(File config, String ip) throws Exception {
        final String content;
        try (FileInputStream in = new FileInputStream(config);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                bytes.write(buf, 0, n);
            }
            content = bytes.toString(StandardCharsets.UTF_8.name());
        }
        JSONObject json = new JSONObject(content);
        if (ip.equals(json.optString("unicast"))) return;
        json.put("unicast", ip);
        try (FileOutputStream out = new FileOutputStream(config);
             OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            writer.write(json.toString(2));
        }
    }

    private String findLocalIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }
            String fallback = null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) {
                        continue;
                    }
                    String ip = address.getHostAddress();
                    if (ip == null) {
                        continue;
                    }
                    if (ip.startsWith("192.168.")) {
                        return ip;
                    }
                    if (fallback == null) {
                        fallback = ip;
                    }
                }
            }
            return fallback;
        } catch (SocketException e) {
            Log.e(TAG, "findLocalIpv4 failed", e);
            return null;
        }
    }
}
