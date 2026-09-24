package com.example.carlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.carlauncher.databinding.ActivityBroadcastLabBinding

open class BroadcastLabActivity : AppCompatActivity() {
    private var binding: ActivityBroadcastLabBinding? = null // 视图绑定
    private var statusText: TextView? = null
    private var receiverRegistered: Boolean = false

    private val powerReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val action = intent!!.getAction()

                Log.i(
                    TAG,
                    ("onReceive action=" + action + ", thread=" + Thread.currentThread().getName()),
                )

                if (Intent.ACTION_POWER_CONNECTED == action) {
                    statusText!!.setText(R.string.power_connected)
                } else if (Intent.ACTION_POWER_DISCONNECTED == action) {
                    statusText!!.setText(R.string.power_disconnected)
                }
            }
        }

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBroadcastLabBinding.inflate(getLayoutInflater()!!)
        setContentView(binding!!.getRoot())

        statusText = binding!!.broadcastStatusText

        Log.i(TAG, "BroadcastLabActivity onCreate")
    }

    protected override fun onStart() {
        super.onStart()
        if (binding == null) {
            return
        }
        if (receiverRegistered) {
            return
        }

        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)

        ContextCompat.registerReceiver(
            this,
            powerReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        receiverRegistered = true
        Log.i(TAG, "receiver registered")
    }

    protected override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(powerReceiver)
            receiverRegistered = false
            Log.i(TAG, "receiver unregistered")
        }
        super.onStop()
    }

    protected override fun onDestroy() {
        binding = null
        statusText = null
        super.onDestroy()
    }

    companion object {

        private const val TAG: String = "BROADCAST_LAB"
    }
}
