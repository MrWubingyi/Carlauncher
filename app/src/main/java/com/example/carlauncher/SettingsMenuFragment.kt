package com.example.carlauncher

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.carlauncher.databinding.FragmentSettingsMenuBinding

// FragmentManager 需要无参构造函数来恢复 Fragment。
open class SettingsMenuFragment : Fragment() {

    private var binding: FragmentSettingsMenuBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        Log.i(TAG, "onCreateView")
        binding = FragmentSettingsMenuBinding.inflate(inflater, container, false)
        return binding!!.getRoot()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "onViewCreated")

        binding!!.displaySettingsButton.setOnClickListener { openDetail("Display") }
        binding!!.networkSettingsButton.setOnClickListener { openDetail("Network") }
        binding!!.aboutSettingsButton.setOnClickListener { openDetail("About") }
    }

    private fun openDetail(title: String?) {
        Log.i(TAG, "Open detail: " + title)
        (requireActivity() as OnSettingSelectedListener).onSettingSelected(title)
    }

    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
    }

    override fun onPause() {
        Log.i(TAG, "onPause")
        super.onPause()
    }

    override fun onStop() {
        Log.i(TAG, "onStop")
        super.onStop()
    }

    override fun onDestroyView() {
        Log.i(TAG, "onDestroyView")
        binding = null
        super.onDestroyView()
    }

    interface OnSettingSelectedListener {
        fun onSettingSelected(title: String?)
    }

    companion object {
        const val TAG: String = "SETTINGS_MENU"
    }
}
