package com.example.carlauncher;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.carlauncher.databinding.FragmentSettingsDetailBinding;
import com.google.android.material.snackbar.Snackbar;

public class SettingsDetailFragment extends Fragment {
    public static final String TAG = "SETTINGS_DETAIL";
    private static final String ARG_TITLE = "title";

    private FragmentSettingsDetailBinding binding;
    private String title = "Unknown";

    // FragmentManager 需要无参构造函数来恢复 Fragment。
    public SettingsDetailFragment() {
    }

    public static SettingsDetailFragment newInstance(String title) {
        SettingsDetailFragment fragment = new SettingsDetailFragment();
        Bundle arguments = new Bundle();
        arguments.putString(ARG_TITLE, title);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            title = getArguments().getString(ARG_TITLE, "Unknown");
        }

        Log.i(TAG, "onCreate, title=" + title);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        Log.i(TAG, "onCreateView, title=" + title);
        binding = FragmentSettingsDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        Log.i(TAG, "onViewCreated, title=" + title);

        binding.detailTitleText.setText(
                getString(R.string.fragment_detail_title, title)
        );

        if ("Display".equals(title)) {
            setupThemeSpinner();
            setupSwitchCompat();
            setupResetSettingsButton();
        }

        boolean isTwoPane =
                requireActivity().findViewById(R.id.detail_container) != null;

        binding.detailBackButton.setVisibility(
                isTwoPane ? View.GONE : View.VISIBLE
        );
        if (!isTwoPane) {
            binding.detailBackButton.setOnClickListener(v ->
                    getParentFragmentManager().popBackStack()
            );
        }

    }
    private void setupResetSettingsButton() {
        binding.resetSettingsButton.setVisibility(View.VISIBLE);

        binding.resetSettingsButton.setOnClickListener(view -> {
            ThemePreferences.resetToDefaults(requireContext());

            // 避免 setChecked() 被当成用户操作。
            binding.welcomeSwitch.setOnCheckedChangeListener(null);

            String defaultTheme =
                    ThemePreferences.getColorTheme(requireContext());
            boolean defaultWelcome =
                    ThemePreferences.isWelcomeEnabled(requireContext());

            binding.themeSpinner.setSelection(
                    ThemePreferences.themeToPosition(defaultTheme),
                    false
            );
            binding.welcomeSwitch.setChecked(defaultWelcome);

            setupWelcomeSwitchListener();

            Log.i(
                    TAG,
                    "Reset UI applied, theme="
                            + defaultTheme
                            + ", welcomeEnabled="
                            + defaultWelcome
            );

            Snackbar.make(
                    binding.getRoot(),
                    "已恢复默认设置",
                    Snackbar.LENGTH_LONG
            ).show();

            // 最后应用主题；如果主题发生变化，Activity 可能重建。
            ThemePreferences.applySavedTheme(requireContext());
        });
    }
    private void setupWelcomeSwitchListener() {
        binding.welcomeSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    Log.i(TAG, "Welcome switch changed=" + isChecked);

                    ThemePreferences.setWelcomeEnabled(
                            requireContext(),
                            isChecked
                    );
                }
        );
    }
    private void setupSwitchCompat() {
        binding.welcomeSwitch.setVisibility(View.VISIBLE);
        // 先恢复保存值，再注册监听器，避免初始化时被误判为用户操作。
        boolean savedEnabled =
                ThemePreferences.isWelcomeEnabled(requireContext());

        binding.welcomeSwitch.setChecked(savedEnabled);
        setupWelcomeSwitchListener();
    }

    private void setupThemeSpinner() {
        binding.themeLabelText.setVisibility(View.VISIBLE);
        binding.themeSpinner.setVisibility(View.VISIBLE);

        String savedTheme = ThemePreferences.getColorTheme(requireContext());
        binding.themeSpinner.setSelection(
                ThemePreferences.themeToPosition(savedTheme),
                false
        );
        binding.themeSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View selectedView,
                            int position,
                            long id
                    ) {
                        String selectedTheme =
                                ThemePreferences.positionToColorTheme(position);
                        String currentTheme =
                                ThemePreferences.getColorTheme(requireContext());
                        if (!selectedTheme.equals(currentTheme)) {
                            Log.i(TAG, "Theme selected=" + selectedTheme);
                            ThemePreferences.saveAndApplyColorTheme(
                                    requireContext(),
                                    selectedTheme
                            );
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // Keep the persisted value when no item is selected.
                    }
                }
        );
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
        binding.themeSpinner.setOnItemSelectedListener(null);
        binding.welcomeSwitch.setOnCheckedChangeListener(null);
        binding = null;
        super.onDestroyView();
    }
}
