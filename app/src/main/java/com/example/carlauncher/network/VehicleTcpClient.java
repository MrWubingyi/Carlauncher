package com.example.carlauncher.network;

import android.util.Log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 车辆 TCP 客户端，用于与车载系统进行通信。
 */
public class VehicleTcpClient {
    private static final String TAG = "VEHICLE_TCP";

    private final String host; // 服务器地址
    private final int port;    // 服务器端口

    /**
     * 构造函数。
     *
     * @param host 服务器 IP 地址
     * @param port 服务器端口号
     */
    public VehicleTcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // 使用单线程线程池处理所有网络操作，确保线程安全并避免阻塞主线程
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Socket socket;
    private BufferedWriter writer;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    public boolean isConnected() {
        return connected.get();
    }

    /**
     * 连接到服务器。
     *
     * @param callback 连接状态回调
     */
    public void connect(Callback callback) {
        executor.execute(() -> {
            try {
                closeInternal(); // 连接前先关闭旧连接

                Socket newSocket = new Socket();
                // 设置连接超时时间为 3 秒
                newSocket.connect(new InetSocketAddress(host, port), 3000);

                BufferedWriter newWriter = new BufferedWriter(new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8));

                socket = newSocket;
                writer = newWriter;
                connected.set(true);

                Log.i(TAG, "Connected to " + host + ":" + port);
                callback.onConnected();
            } catch (IOException exception) {
                connected.set(false);
                Log.e(TAG, "Connect failed", exception);
                closeInternal();
                callback.onError(exception);
            }
        });
    }

    /**
     * 发送一行字符串数据。
     *
     * @param message  要发送的消息
     * @param callback 发送状态回调
     */
    public void sendLine(String message, Callback callback) {
        executor.execute(() -> {
            if (!connected.get() || socket == null || writer == null || socket.isClosed() || !socket.isConnected()) {
                callback.onError(new IllegalStateException("TCP 未连接"));
                return;
            }

            try {
                writer.write(message);
                writer.newLine(); // 添加换行符
                writer.flush();   // 刷新缓冲区，确保数据发出

//                Log.i(TAG, "Sent: " + message);
                callback.onMessageSent(message);
            } catch (IOException exception) {
                Log.e(TAG, "Send failed", exception);
                closeInternal();
                callback.onError(exception);
            }
        });
    }

    /**
     * 关闭连接。
     */
    public void close() {
        connected.set(false);

        executor.execute(this::closeInternal);
    }

    /**
     * 关闭连接并关闭线程池。
     * 通常在 Activity 销毁时调用。
     */
    public void shutdown() {
        close();
        executor.shutdown();
    }

    /**
     * 内部关闭逻辑，不抛出异常。
     */
    private void closeInternal() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException exception) {
                Log.w(TAG, "Close writer failed", exception);
            }

            writer = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException exception) {
                Log.w(TAG, "Close socket failed", exception);
            }

            socket = null;
        }
    }

    /**
     * TCP 客户端回调接口。
     */
    public interface Callback {

        /**
         * 连接成功回调。
         */
        default void onConnected() {
        }

        /**
         * 消息发送成功回调。
         *
         * @param message 已发送的消息
         */
        default void onMessageSent(String message) {
        }

        /**
         * 发生错误时的回调。
         *
         * @param exception 异常对象
         */
        default void onError(Exception exception) {
        }
    }
}
