package com.example.b1void.ui

import android.app.Dialog
import android.util.Log
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
import android.widget.Toast
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

        view.findViewById<Button>(R.id.video_quality_settings_button).setOnClickListener {
            showVideoQualitySettingsSubmenu()
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
                runCatching { settingsManager.setFlashMode(mode) }
                    .onFailure { Log.e("CameraSettings", "Failed to save flash mode", it); Toast.makeText(requireContext(), R.string.error_saving_settings, Toast.LENGTH_SHORT).show() }
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
                        runCatching {
                            settingsManager.setResolution("${selectedResolution.width}x${selectedResolution.height}")
                            onResolutionChangedCallback?.invoke(selectedResolution)
                            dialog.dismiss()
                        }.onFailure { Log.e("CameraSettings", "Failed to save resolution", it); Toast.makeText(requireContext(), R.string.error_saving_settings, Toast.LENGTH_SHORT).show() }
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
                runCatching {
                    settingsManager.setVideoRecordDelay(delayMs)
                    dialog.dismiss()
                }.onFailure { Log.e("CameraSettings", "Failed to save video delay", it); Toast.makeText(requireContext(), R.string.error_saving_settings, Toast.LENGTH_SHORT).show() }
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

    private fun showVideoQualitySettingsSubmenu() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_video_quality_settings)

        val group = dialog.findViewById<RadioGroup>(R.id.video_quality_group)

        var isInitializing = true

        group.setOnCheckedChangeListener { _, checkedId ->
            if (isInitializing) return@setOnCheckedChangeListener
            val quality = when (checkedId) {
                R.id.quality_uhd -> 2160
                R.id.quality_fhd -> 1080
                R.id.quality_hd -> 720
                R.id.quality_sd -> 480
                else -> 720
            }
            lifecycleScope.launch {
                runCatching {
                    settingsManager.setVideoQuality(quality)
                    dialog.dismiss()
                }.onFailure { Log.e("CameraSettings", "Failed to save video quality", it); Toast.makeText(requireContext(), R.string.error_saving_settings, Toast.LENGTH_SHORT).show() }
            }
        }

        lifecycleScope.launch {
            val current = settingsManager.getVideoQuality().first()
            when (current) {
                2160 -> group.check(R.id.quality_uhd)
                1080 -> group.check(R.id.quality_fhd)
                480 -> group.check(R.id.quality_sd)
                else -> group.check(R.id.quality_hd)
            }
            isInitializing = false
        }

        dialog.show()
    }
}
