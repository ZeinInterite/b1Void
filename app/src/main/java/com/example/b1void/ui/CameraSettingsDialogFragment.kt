package com.example.b1void.ui

import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private var resolutionAdapter: ResolutionAdapter? = null

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

        val flashRadioGroup = view.findViewById<RadioGroup>(R.id.flash_mode_group)
        val resolutionRecyclerView = view.findViewById<RecyclerView>(R.id.resolution_recycler_view)

        // Setup resolution RecyclerView
        resolutionRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Load current settings and update UI
        lifecycleScope.launch {
            val currentFlashMode = settingsManager.getFlashMode().first()
            when (currentFlashMode) {
                0 -> flashRadioGroup.check(R.id.flash_mode_off)
                1 -> flashRadioGroup.check(R.id.flash_mode_on)
                2 -> flashRadioGroup.check(R.id.flash_mode_auto)
            }

            // Setup resolution selection
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
                resolutionAdapter = ResolutionAdapter(
                    supportedResolutions,
                    currentResolution
                ) { selectedResolution ->
                    lifecycleScope.launch {
                        settingsManager.setResolution("${selectedResolution.width}x${selectedResolution.height}")
                        // Update the adapter to show the new selection
                        resolutionAdapter?.updateSelectedResolution(selectedResolution)
                        onResolutionChangedCallback?.invoke(selectedResolution)
                    }
                }
                resolutionRecyclerView.adapter = resolutionAdapter
            }
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
    }
}
