package com.example.carlauncher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsMenuFragment extends Fragment {
    public static final String TAG = "SETTINGS_MENU";

    // FragmentManager 需要无参构造函数来恢复 Fragment。
    public SettingsMenuFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Log.i(TAG, "onCreateView");
        return inflater.inflate(R.layout.fragment_settings_menu, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        Log.i(TAG, "onViewCreated");

        view.findViewById(R.id.displaySettingsButton)
                .setOnClickListener(v -> openDetail("Display"));
        view.findViewById(R.id.networkSettingsButton)
                .setOnClickListener(v -> openDetail("Network"));
        view.findViewById(R.id.aboutSettingsButton)
                .setOnClickListener(v -> openDetail("About"));
    }

    private void openDetail(String title) {
        Log.i(TAG, "Open detail: " + title);
        ((OnSettingSelectedListener) requireActivity())
                .onSettingSelected(title);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i(TAG, "onStart");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.i(TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        Log.i(TAG, "onDestroyView");
        super.onDestroyView();
    }
    public interface OnSettingSelectedListener {
        void onSettingSelected(String title);
    }

}
