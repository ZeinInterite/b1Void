package com.example.b1void.ui

import android.app.Dialog
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R
import com.example.b1void.adapters.ResolutionAdapter
import com.example.b1void.data.CameraSettingsManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CameraSettingsDialogFragment : BottomSheetDialogFragment() {

    private lateinit var settingsManager: CameraSettingsManager
    private var supportedResolutions: List<Size> = emptyList()
    private var onResolutionChangedCallback: ((Size) -> Unit)? = null

    fun setSupportedResolutions(resolutions: List<Size>, callback: (Size) -> Unit) {
        supportedResolutions = resolutions
        onResolutionChangedCallback = callback
    }

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

        // Setup button click listeners to open submenus
        view.findViewById<Button>(R.id.flash_settings_button).setOnClickListener {
            showFlashSettingsSubmenu()
        }

        view.findViewById<Button>(R.id.resolution_settings_button).setOnClickListener {
            showResolutionSettingsSubmenu()
        }

        view.findViewById<Button>(R.id.video_delay_settings_button).setOnClickListener {
            showVideoDelaySettingsSubmenu()
        }
    }

    private fun showFlashSettingsSubmenu() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_flash_settings)

        val flashRadioGroup = dialog.findViewById<RadioGroup>(R.id.flash_mode_group)

        // Prevent initial programmatic selection from triggering the listener
        var isInitializing = true

        // Set listener with guard first
        flashRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isInitializing) return@setOnCheckedChangeListener

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

        // Load current flash mode and set selection
        lifecycleScope.launch {
            val currentFlashMode = settingsManager.getFlashMode().first()
            when (currentFlashMode) {
                0 -> flashRadioGroup.check(R.id.flash_mode_off)
                1 -> flashRadioGroup.check(R.id.flash_mode_on)
                2 -> flashRadioGroup.check(R.id.flash_mode_auto)
            }
            isInitializing = false
        }

        dialog.show()
    }

    private fun showResolutionSettingsSubmenu() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_resolution_settings)

        val resolutionRecyclerView = dialog.findViewById<RecyclerView>(R.id.resolution_recycler_view)
        resolutionRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Load current resolution
        lifecycleScope.launch {
            val currentResolutionString = settingsManager.getResolution().first()
            val currentResolution = if (!currentResolutionString.isNullOrEmpty()) {
                val parts = currentResolutionString.split("x")
                if (parts.size == 2) {
                    Size(parts[0].toInt(), parts[1].toInt())
                } else {
                    supportedResolutions.firstOrNull()
                }
            } else {
                supportedResolutions.firstOrNull()
            }

            if (supportedResolutions.isNotEmpty()) {
                val adapter = ResolutionAdapter(
                    supportedResolutions,
                    currentResolution
                ) { selectedResolution ->
                    lifecycleScope.launch {
                        settingsManager.setResolution("${selectedResolution.width}x${selectedResolution.height}")
                        onResolutionChangedCallback?.invoke(selectedResolution)
                        dialog.dismiss()
                    }
                }
                resolutionRecyclerView.adapter = adapter
            }
        }

        dialog.show()
    }

    private fun showVideoDelaySettingsSubmenu() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_video_delay_settings)

        val videoDelayGroup = dialog.findViewById<RadioGroup>(R.id.video_delay_group)

        // Prevent initial programmatic selection from triggering the listener (and dismiss)
        var isInitializing = true

        // Set listener with guard first
        videoDelayGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isInitializing) return@setOnCheckedChangeListener

            val delayMs = when (checkedId) {
                R.id.delay_600ms -> 600
                R.id.delay_800ms -> 800
                R.id.delay_1000ms -> 1000
                R.id.delay_2000ms -> 2000
                else -> 800
            }
            lifecycleScope.launch {
                settingsManager.setVideoRecordDelay(delayMs)
                dialog.dismiss()
            }
        }

        // Load current video delay and set selection
        lifecycleScope.launch {
            val currentDelay = settingsManager.getVideoRecordDelay().first()
            when (currentDelay) {
                600 -> videoDelayGroup.check(R.id.delay_600ms)
                800 -> videoDelayGroup.check(R.id.delay_800ms)
                1000 -> videoDelayGroup.check(R.id.delay_1000ms)
                2000 -> videoDelayGroup.check(R.id.delay_2000ms)
                else -> videoDelayGroup.check(R.id.delay_800ms) // Default 0.8s
            }
            isInitializing = false
        }

        dialog.show()
    }
}
