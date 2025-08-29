package com.example.b1void.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.lifecycle.lifecycleScope
import com.example.b1void.R
import com.example.b1void.data.CameraSettingsManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CameraSettingsDialogFragment : BottomSheetDialogFragment() {

    private lateinit var settingsManager: CameraSettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settingsManager = CameraSettingsManager(requireContext())
        return inflater.inflate(R.layout.bottom_sheet_camera_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val flashRadioGroup = view.findViewById<RadioGroup>(R.id.flash_mode_group)
        val timestampSwitch = view.findViewById<SwitchMaterial>(R.id.timestamp_switch)

        // Load current settings and update UI
        lifecycleScope.launch {
            val currentFlashMode = settingsManager.getFlashMode().first()
            when (currentFlashMode) {
                0 -> flashRadioGroup.check(R.id.flash_mode_off)
                1 -> flashRadioGroup.check(R.id.flash_mode_on)
                2 -> flashRadioGroup.check(R.id.flash_mode_auto)
            }

            timestampSwitch.isChecked = settingsManager.isTimestampEnabled().first()
        }

        // Save settings on change
        flashRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.flash_mode_off -> 0
                R.id.flash_mode_on -> 1
                R.id.flash_mode_auto -> 2
                else -> 0
            }
            lifecycleScope.launch {
                settingsManager.setFlashMode(mode)
            }
        }

        timestampSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsManager.setTimestampEnabled(isChecked)
            }
        }
    }
}
