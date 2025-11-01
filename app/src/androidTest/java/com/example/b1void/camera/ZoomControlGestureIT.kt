package com.example.b1void.camera

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.b1void.ui.camera.ZoomControl
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class ZoomControlGestureIT {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dragRail_changesZoomRatio_inExpectedDirection() {
        var zoom by mutableStateOf(1.0f)
        val minZ = 0.5f
        val maxZ = 5.0f

        composeRule.setContent {
            MaterialTheme {
                ZoomControl(
                    zoomRatio = zoom,
                    minZoom = minZ,
                    maxZoom = maxZ,
                    availablePresets = listOf(0.5f, 1f, 2f, 3f),
                    modifier = androidx.compose.ui.Modifier.testTag("ZoomRoot"),
                    leftHanded = false,
                    onZoomChanged = { ratio -> zoom = ratio },
                    onPresetSelected = { preset -> zoom = preset }
                )
            }
        }

        // Ensure control is on screen
        composeRule.onNodeWithTag("ZoomRoot").assertIsDisplayed()
        composeRule.onNodeWithTag("ZoomLabel").assertIsDisplayed()

        // Perform gesture on the rail. Use contentDescription("Zoom") to be orientation-agnostic.
        val rail = composeRule.onNode(hasContentDescriptionExactly("Zoom"), useUnmergedTree = true)

        val startZoom = zoom
        // Try horizontal drag to the right (portrait rail)
        rail.performTouchInput {
            val c = this.center
            down(Offset(c.x - 150f, c.y))
            moveTo(Offset(c.x + 250f, c.y))
            up()
        }
        // Wait briefly; if no change, try vertical drag up (landscape rail)
        composeRule.waitForIdle()
        val changedHoriz = composeRule.runOnIdle { zoom } > startZoom + 0.01f
        if (!changedHoriz) {
            rail.performTouchInput {
                val c = this.center
                down(Offset(c.x, c.y + 180f))
                moveTo(Offset(c.x, c.y - 280f))
                up()
            }
        }

        composeRule.waitUntil(timeoutMillis = 7000) { zoom > startZoom + 0.01f }
        assertTrue("Zoom should increase after swipe; start=$startZoom actual=$zoom", zoom > startZoom + 0.01f)

        // Now try to decrease zoom in opposite direction
        val incZoom = zoom
        rail.performTouchInput {
            val c = this.center
            // Opposite drag on horizontal axis
            down(Offset(c.x + 150f, c.y))
            moveTo(Offset(c.x - 250f, c.y))
            up()
        }
        composeRule.waitForIdle()
        val changedBackHoriz = composeRule.runOnIdle { zoom } < incZoom - 0.01f
        if (!changedBackHoriz) {
            rail.performTouchInput {
                val c = this.center
                down(Offset(c.x, c.y - 180f))
                moveTo(Offset(c.x, c.y + 280f))
                up()
            }
        }
        composeRule.waitUntil(timeoutMillis = 7000) { zoom < incZoom - 0.01f }
        assertTrue("Zoom should decrease after swipe back; before=$incZoom actual=$zoom", zoom < incZoom - 0.01f)
    }
}
