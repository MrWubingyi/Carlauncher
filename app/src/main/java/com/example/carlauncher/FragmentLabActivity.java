package com.example.carlauncher;
import android.os.SystemClock;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class FragmentLabActivity extends AppCompatActivity
        implements SettingsMenuFragment.OnSettingSelectedListener
{
    private static final String TAG = "FRAGMENT_LAB";
    private boolean isTwoPane;
    private static final long CLICK_DEBOUNCE_MS = 500L;
    private long lastSettingClickTime;
    private String selectedTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Activity onCreate, restored=" + (savedInstanceState != null));
        setContentView(R.layout.activity_fragment_lab);


        isTwoPane = findViewById(R.id.detail_container) != null;

        // FragmentManager 会在配置重建时自动恢复 Fragment。
        // 只有首次创建 Activity 时才安装初始页面，避免覆盖恢复结果。
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.menu_container,
                            new SettingsMenuFragment(),
                            SettingsMenuFragment.TAG
                    )
                    .commit();

            if(isTwoPane){
                selectedTitle = "Display";
                showDetail("Display");
            }
        }
    }

    public void onSettingSelected(String title) {
        long now = SystemClock.elapsedRealtime();

        if (now - lastSettingClickTime < CLICK_DEBOUNCE_MS) {
            Log.i(TAG, "Ignore rapid click: " + title);
            return;
        }

        if (title.equals(selectedTitle)) {
            Log.i(TAG, "Ignore duplicate selection: " + title);
            return;
        }

        lastSettingClickTime = now;
        selectedTitle = title;
        if (isTwoPane) {
            showDetail(title);
        } else {
            showSinglePaneDetail(title);
        }
    }
    private void showSinglePaneDetail(String title) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.menu_container,
                        SettingsDetailFragment.newInstance(title),
                        "settings_detail"
                )
                .addToBackStack("settings_detail")
                .commit();
    }
    private void showDetail(String title) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        R.id.detail_container,
                        SettingsDetailFragment.newInstance(title),
                        "settings_detail"
                )
                .commit();
    }

}

