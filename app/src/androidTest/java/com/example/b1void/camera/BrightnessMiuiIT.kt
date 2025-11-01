package com.example.b1void.camera

import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.example.b1void.activities.CameraComposeActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrightnessMiuiIT {

    @Test
    fun cameraComposeActivity_setsExpectedScreenBrightness_onXiaomi() {
        ActivityScenario.launch(CameraComposeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val maker = Build.MANUFACTURER.lowercase()
                val br = activity.window.attributes?.screenBrightness ?: -1f
                if (maker.contains("xiaomi") || maker.contains("redmi")) {
                    // Expect forced full brightness on Xiaomi/Redmi
                    assertEquals("Expected screenBrightness=1f on Xiaomi/Redmi, actual=$br", 1f, br, 0.01f)
                } else {
                    // Non-Xiaomi: just ensure value is within valid range or default (-1f = follow system)
                    assertTrue("Brightness must be default(-1) or in [0,1], actual=$br", br == -1f || (br in 0f..1f))
                }
            }
        }
    }
}
