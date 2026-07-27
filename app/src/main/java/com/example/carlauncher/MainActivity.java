package com.example.carlauncher;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.carlauncher.databinding.ActivityMainBinding;
import com.example.carlauncher.network.VehicleTcpClient;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CAR_LAUNCHER";

    private ActivityMainBinding binding;
    private VehicleTcpClient tcpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tcpClient = new VehicleTcpClient(
                "192.168.31.248",
                19090
        );

        binding.connectButton.setOnClickListener(view ->
                connectToUbuntu()
        );

        binding.sendButton.setOnClickListener(view ->
                sendTestVehicleState()
        );
    }

    private void connectToUbuntu() {
        tcpClient.connect(new VehicleTcpClient.Callback() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    binding.connectionStatusText.setText("CONNECTED");

                    Toast.makeText(
                            MainActivity.this,
                            "TCP connected",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }

            @Override
            public void onError(Exception exception) {
                showError(exception);
            }
        });
    }

    private void sendTestVehicleState() {
        String message =
                "{\"version\":1,"
                        + "\"seq\":2,"
                        + "\"speedKph\":80,"
                        + "\"rpm\":2500,"
                        + "\"gear\":\"D\","
                        + "\"soc\":79}";

        tcpClient.sendLine(
                message,
                new VehicleTcpClient.Callback() {
                    @Override
                    public void onMessageSent(String sentMessage) {
                        runOnUiThread(() ->
                                binding.lastMessageText.setText(sentMessage)
                        );
                    }

                    @Override
                    public void onError(Exception exception) {
                        showError(exception);
                    }
                }
        );
    }

    private void showError(Exception exception) {
        Log.e(TAG, "TCP operation failed", exception);

        runOnUiThread(() -> {
            binding.connectionStatusText.setText("ERROR");

            Toast.makeText(
                    MainActivity.this,
                    exception.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    @Override
    protected void onDestroy() {
        if (tcpClient != null) {
            tcpClient.shutdown();
        }

        binding = null;
        super.onDestroy();
    }
}