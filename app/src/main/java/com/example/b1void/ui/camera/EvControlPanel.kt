package com.example.b1void.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * EvControlPanel – button with current EV value and a vertical slider like OpenCamera.
 * Shows a compact button; tapping toggles a vertical EV slider.
 */
@Composable
fun EvControlPanel(
    ev: Float,
    evRange: ClosedFloatingPointRange<Float>,
    onEvChanged: (Float) -> Unit,
    onResetEv: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var lastChangeTick by remember { mutableStateOf(0L) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center
    ) {
        // EV value button (toggles slider)
        Button(onClick = { expanded = !expanded; lastChangeTick = System.currentTimeMillis() }) {
            Text(text = formatEvForButton(ev))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .sizeIn(minWidth = 56.dp)
            ) {
                // Reuse dedicated vertical EV slider UI (elongated in landscape)
                val cfg = LocalConfiguration.current
                val isLandscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val tall = if (isLandscape) 340.dp else 220.dp
                ExposureControlV(
                    currentEv = ev,
                    evRange = evRange,
                    onEvChanged = {
                        onEvChanged(it)
                        lastChangeTick = System.currentTimeMillis()
                    },
                    onResetEv = {
                        onResetEv()
                        // Keep panel open so the user sees the reset effect
                    },
                    modifier = Modifier,
                    sliderHeight = tall
                )
            }
        }
    }

    // Auto-hide after 1.5s of inactivity when expanded
    if (expanded) {
        androidx.compose.runtime.LaunchedEffect(lastChangeTick, expanded) {
            kotlinx.coroutines.delay(1500)
            if (expanded && System.currentTimeMillis() - lastChangeTick >= 1400) {
                expanded = false
            }
        }
    }
}

private fun formatEvForButton(ev: Float): String {
    return when {
        ev > 0.05f -> "+%.1f EV".format(ev)
        ev < -0.05f -> "%.1f EV".format(ev)
        else -> "0.0 EV"
    }
}
