package com.example.carlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.carlauncher.databinding.ActivityBroadcastLabBinding;

import org.jspecify.annotations.Nullable;

public class BroadcastLabActivity extends AppCompatActivity {

    private static final String TAG = "BROADCAST_LAB";
    private ActivityBroadcastLabBinding binding; // 视图绑定
    private TextView statusText;
    private boolean receiverRegistered;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityBroadcastLabBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        statusText = binding.broadcastStatusText;

        Log.i(TAG, "BroadcastLabActivity onCreate");
    }

    private final BroadcastReceiver powerReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();

                    Log.i(
                            TAG,
                            "onReceive action=" + action
                                    + ", thread="
                                    + Thread.currentThread().getName()
                    );

                    if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                        statusText.setText("Power connected");
                    } else if (
                            Intent.ACTION_POWER_DISCONNECTED.equals(action)
                    ) {
                        statusText.setText("Power disconnected");
                    }
                }
            };

    @Override
    protected void onStart() {
        super.onStart();
        if (binding == null) {
            return;
        }
        if (receiverRegistered) {
            return;
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);

        ContextCompat.registerReceiver(
                this,
                powerReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
        );

        receiverRegistered = true;
        Log.i(TAG, "receiver registered");
    }

    @Override
    protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(powerReceiver);
            receiverRegistered = false;
            Log.i(TAG, "receiver unregistered");
        }
        binding = null;
        super.onStop();
    }
}