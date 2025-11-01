package com.example.b1void.activities

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import com.example.b1void.ui.camera.CameraScreen
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class CameraComposeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply orientation preference prior to content
        try {
            val mgr = com.example.b1void.data.CameraSettingsManager(this)
            // Default to landscape; then apply stored preference
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            lifecycleScope.launchWhenCreated {
                val lock = mgr.isOrientationLockEnabled().first()
                requestedOrientation = if (lock) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
        } catch (_: Throwable) { }
        // Keep screen on and boost brightness for Xiaomi, if enabled
        try {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val maker = android.os.Build.MANUFACTURER.lowercase()
            val mgr = com.example.b1void.data.CameraSettingsManager(this)
            lifecycleScope.launchWhenCreated {
                val boost = runCatching { mgr.getXiaomiBrightnessBoostEnabled().first() }
                    .getOrElse { maker.contains("xiaomi") }
                if (boost || maker.contains("xiaomi") || maker.contains("redmi")) {
                    val lp = window.attributes
                    lp.screenBrightness = 1f
                    window.attributes = lp
                }
            }
        } catch (_: Throwable) {}
        setContent {
            MaterialTheme {
                CameraScreen(leftHanded = false)
            }
        }
    }
}
