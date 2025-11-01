package com.example.b1void.camera

import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.b1void.activities.CameraComposeActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertNotNull

@RunWith(AndroidJUnit4::class)
class CameraXSmokeIT {

    @Test
    fun activityLaunches_andContainsPreviewView() {
        ActivityScenario.launch(CameraComposeActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.window?.decorView as View
                val preview = findPreviewView(root)
                assertNotNull("PreviewView should be present inside CameraScreen", preview)
            }
        }
    }

    private fun findPreviewView(view: View): PreviewView? {
        if (view is PreviewView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                val found = findPreviewView(child)
                if (found != null) return found
            }
        }
        return null
    }
}
