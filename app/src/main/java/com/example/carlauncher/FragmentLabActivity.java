package com.example.carlauncher;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

public class FragmentLabActivity extends AppCompatActivity {
    private static final String TAG = "FRAGMENT_LAB";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "Activity onCreate, restored=" + (savedInstanceState != null));
        setContentView(R.layout.activity_fragment_lab);

        // FragmentManager 会在配置重建时自动恢复 Fragment。
        // 只有首次创建 Activity 时才安装初始页面，避免覆盖恢复结果。
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.fragmentContainer,
                            new SettingsMenuFragment(),
                            SettingsMenuFragment.TAG
                    )
                    .commit();
        }
    }
}
