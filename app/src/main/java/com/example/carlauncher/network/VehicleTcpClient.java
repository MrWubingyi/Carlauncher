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

public class VehicleTcpClient {
    private static final String TAG = "VEHICLE_TCP";

    private final String host;
    private final int port;

    public VehicleTcpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private Socket socket;
    private BufferedWriter writer;

    public void connect(Callback callback) {
        executor.execute(() -> {
            try {
                closeInternal();

                Socket newSocket = new Socket();
                newSocket.connect(
                        new InetSocketAddress(host, port),
                        3000
                );

                BufferedWriter newWriter = new BufferedWriter(
                        new OutputStreamWriter(
                                newSocket.getOutputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                socket = newSocket;
                writer = newWriter;

                Log.i(TAG, "Connected to " + host + ":" + port);
                callback.onConnected();
            } catch (IOException exception) {
                Log.e(TAG, "Connect failed", exception);
                closeInternal();
                callback.onError(exception);
            }
        });
    }

    public void sendLine(String message, Callback callback) {
        executor.execute(() -> {
            if (socket == null
                    || writer == null
                    || socket.isClosed()
                    || !socket.isConnected()) {
                callback.onError(
                        new IllegalStateException("TCP is not connected")
                );
                return;
            }

            try {
                writer.write(message);
                writer.newLine();
                writer.flush();

                Log.i(TAG, "Sent: " + message);
                callback.onMessageSent(message);
            } catch (IOException exception) {
                Log.e(TAG, "Send failed", exception);
                closeInternal();
                callback.onError(exception);
            }
        });
    }

    public void close() {
        executor.execute(this::closeInternal);
    }

    public void shutdown() {
        close();
        executor.shutdown();
    }

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

    public interface Callback {

        default void onConnected() {
        }

        default void onMessageSent(String message) {
        }

        default void onError(Exception exception) {
        }
    }
}
