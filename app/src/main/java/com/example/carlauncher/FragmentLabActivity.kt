package com.example.carlauncher

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity

open class FragmentLabActivity :
    AppCompatActivity(), SettingsMenuFragment.OnSettingSelectedListener {
    private var isTwoPane: Boolean = false
    private var lastSettingClickTime: Long = 0
    private var selectedTitle: String? = null

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Activity onCreate, restored=" + (savedInstanceState != null))
        setContentView(R.layout.activity_fragment_lab)

        isTwoPane = findViewById<View>(R.id.detail_container) != null

        // FragmentManager 会在配置重建时自动恢复 Fragment。
        // 只有首次创建 Activity 时才安装初始页面，避免覆盖恢复结果。
        if (savedInstanceState == null) {
            getSupportFragmentManager()!!
                .beginTransaction()
                .replace(
                    R.id.menu_container,
                    SettingsMenuFragment(),
                    SettingsMenuFragment.TAG,
                )!!
                .commit()

            if (isTwoPane) {
                selectedTitle = "Display"
                showDetail("Display")
            }
        }
    }

    override fun onSettingSelected(title: String?) {
        val now = SystemClock.elapsedRealtime()

        if (now - lastSettingClickTime < CLICK_DEBOUNCE_MS) {
            Log.i(TAG, "Ignore rapid click: " + title)
            return
        }

        if (title == selectedTitle) {
            Log.i(TAG, "Ignore duplicate selection: " + title)
            return
        }

        lastSettingClickTime = now
        selectedTitle = title
        if (isTwoPane) {
            showDetail(title)
        } else {
            showSinglePaneDetail(title)
        }
    }

    private fun showSinglePaneDetail(title: String?) {
        getSupportFragmentManager()!!
            .beginTransaction()
            .replace(
                R.id.menu_container,
                SettingsDetailFragment.newInstance(title),
                "settings_detail",
            )!!
            .addToBackStack("settings_detail")
            .commit()
    }

    private fun showDetail(title: String?) {
        getSupportFragmentManager()!!
            .beginTransaction()
            .replace(
                R.id.detail_container,
                SettingsDetailFragment.newInstance(title),
                "settings_detail",
            )!!
            .commit()
    }

    companion object {
        private const val TAG: String = "FRAGMENT_LAB"
        private const val CLICK_DEBOUNCE_MS: Long = 500L
    }
}
