package com.example.carlauncher;

import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsDetailFragment extends Fragment {
    public static final String TAG = "SETTINGS_DETAIL";
    private static final String ARG_TITLE = "title";

    private String title = "Unknown";

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
        return inflater.inflate(R.layout.fragment_settings_detail, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);
        Log.i(TAG, "onViewCreated, title=" + title);

        TextView titleView = view.findViewById(R.id.detailTitleText);
        titleView.setText(getString(R.string.fragment_detail_title, title));

        view.findViewById(R.id.detailBackButton)
                .setOnClickListener(v ->
                        getParentFragmentManager().popBackStack()
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
        super.onDestroyView();
    }
}
