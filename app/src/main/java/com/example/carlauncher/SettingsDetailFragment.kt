package com.example.carlauncher

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import com.example.carlauncher.databinding.FragmentSettingsDetailBinding
import com.google.android.material.snackbar.Snackbar

// FragmentManager 需要无参构造函数来恢复 Fragment。
open class SettingsDetailFragment : Fragment() {

    private var binding: FragmentSettingsDetailBinding? = null
    private var title: String? = "Unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (getArguments() != null) {
            title = requireArguments().getString(ARG_TITLE, "Unknown")
        }

        Log.i(TAG, "onCreate, title=" + title)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        Log.i(TAG, "onCreateView, title=" + title)
        binding = FragmentSettingsDetailBinding.inflate(inflater, container, false)
        return binding!!.getRoot()
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "onViewCreated, title=" + title)

        binding!!
            .detailTitleText
            .setText(getString(R.string.fragment_detail_title, localizedTitle()))

        if ("Display" == title) {
            setupThemeSpinner()
            setupSwitchCompat()
            setupResetSettingsButton()
        }

        val isTwoPane = requireActivity()!!.findViewById<View?>(R.id.detail_container) != null

        binding!!.detailBackButton.setVisibility(if (isTwoPane) View.GONE else View.VISIBLE)
        if (!isTwoPane) {
            binding!!.detailBackButton.setOnClickListener {
                getParentFragmentManager().popBackStack()
            }
        }
    }

    private fun localizedTitle(): String? {
        when (title) {
            "Display" -> return getString(R.string.settings_display)
            "Network" -> return getString(R.string.settings_network)
            "About" -> return getString(R.string.settings_about)
            else -> return title
        }
    }

    private fun setupResetSettingsButton() {
        binding!!.resetSettingsButton.setVisibility(View.VISIBLE)

        binding!!.resetSettingsButton.setOnClickListener {
            ThemePreferences.resetToDefaults(requireContext())

            // 避免 setChecked() 被当成用户操作。
            binding!!.welcomeSwitch.setOnCheckedChangeListener(null)

            val defaultTheme = ThemePreferences.getColorTheme(requireContext())
            val defaultWelcome = ThemePreferences.isWelcomeEnabled(requireContext())

            binding!!
                .themeSpinner
                .setSelection(
                    ThemePreferences.themeToPosition(defaultTheme),
                    false,
                )
            binding!!.welcomeSwitch.setChecked(defaultWelcome)

            setupWelcomeSwitchListener()

            Log.i(
                TAG,
                ("Reset UI applied, theme=" + defaultTheme + ", welcomeEnabled=" + defaultWelcome),
            )

            Snackbar.make(
                    binding!!.getRoot(),
                    getString(R.string.settings_reset)!!,
                    Snackbar.LENGTH_LONG,
                )
                .show()

            // 最后应用主题；如果主题发生变化，Activity 可能重建。
            ThemePreferences.applySavedTheme(requireContext())
        }
    }

    private fun setupWelcomeSwitchListener() {
        binding!!.welcomeSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            Log.i(TAG, "Welcome switch changed=" + isChecked)

            ThemePreferences.setWelcomeEnabled(
                requireContext(),
                isChecked,
            )
        }
    }

    private fun setupSwitchCompat() {
        binding!!.welcomeSwitch.setVisibility(View.VISIBLE)
        // 先恢复保存值，再注册监听器，避免初始化时被误判为用户操作。
        val savedEnabled = ThemePreferences.isWelcomeEnabled(requireContext())

        binding!!.welcomeSwitch.setChecked(savedEnabled)
        setupWelcomeSwitchListener()
    }

    private fun setupThemeSpinner() {
        binding!!.themeLabelText.setVisibility(View.VISIBLE)
        binding!!.themeSpinner.setVisibility(View.VISIBLE)

        val savedTheme = ThemePreferences.getColorTheme(requireContext())
        binding!!
            .themeSpinner
            .setSelection(
                ThemePreferences.themeToPosition(savedTheme),
                false,
            )
        binding!!
            .themeSpinner
            .setOnItemSelectedListener(
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        selectedView: View?,
                        position: Int,
                        id: Long,
                    ) {
                        val selectedTheme = ThemePreferences.positionToColorTheme(position)
                        val currentTheme = ThemePreferences.getColorTheme(requireContext())
                        if (selectedTheme != currentTheme) {
                            Log.i(TAG, "Theme selected=" + selectedTheme)
                            ThemePreferences.saveAndApplyColorTheme(
                                requireContext(),
                                selectedTheme,
                            )
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        // Keep the persisted value when no item is selected.
                    }
                }
            )
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
        binding!!.themeSpinner.setOnItemSelectedListener(null)
        binding!!.welcomeSwitch.setOnCheckedChangeListener(null)
        binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG: String = "SETTINGS_DETAIL"
        private const val ARG_TITLE: String = "title"

        @JvmStatic
        fun newInstance(title: String?): SettingsDetailFragment {
            val fragment = SettingsDetailFragment()
            val arguments = Bundle()
            arguments.putString(ARG_TITLE, title)
            fragment.setArguments(arguments)
            return fragment
        }
    }
}
