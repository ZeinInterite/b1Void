package com.example.b1void.activities

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import com.example.b1void.R

// Stubbed class to fix build
class CameraUIController(
    private val rootView: View,
    private val listener: UIActionListener,
    private val context: Context
) {
    interface UIActionListener {
        fun onCaptureClicked()
        fun onSwitchCameraClicked()
        fun onToggleFlashClicked()
        fun onSettingsClicked()
    }

    fun setupViews() {
        // Empty
    }
}