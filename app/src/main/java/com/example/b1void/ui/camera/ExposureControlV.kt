package com.example.b1void.ui.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private const val TAG = "TONEMAP_DEBUG"

@Composable
fun ExposureControlV(
    currentEv: Float,
    evRange: ClosedFloatingPointRange<Float> = -2.0f..2.0f,
    onEvChanged: (Float) -> Unit,
    onResetEv: () -> Unit = {},
    modifier: Modifier = Modifier,
    sliderHeight: Dp = 200.dp
) {
    val animatedEv by animateFloatAsState(
        targetValue = currentEv,
        animationSpec = tween(durationMillis = 150),
        label = "ev_animation"
    )

    val evColor = remember(animatedEv) {
        when {
            animatedEv < -0.1f -> Color(0xFFFF9800)
            animatedEv > 0.1f -> Color(0xFF2196F3)
            else -> Color(0xFFFFFFFF)
        }
    }

    Column(
        modifier = modifier
            // Restore previous panel height so the semi-transparent square
            // does not shrink after removing labels.
            .sizeIn(minHeight = sliderHeight + 48.dp)
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // All textual labels removed per UI request
        // Container to center slider within the panel
        Box(
            modifier = Modifier
                .weight(1f, fill = true),
            contentAlignment = Alignment.Center
        ) {

        // Trick to get a vertical slider using rotation:
        // 1) Make the container wide (sliderHeight) and short (48dp)
        // 2) Rotate the whole container by -90°, so perceived height == sliderHeight
        // Make the effective slider track slightly shorter so the thumb stays
        // inside the rounded container without shifting the panel itself.
        // Shrink by 24.dp which roughly equals twice the thumb radius + padding.
            Box(
                modifier = Modifier
                    .width(sliderHeight - 24.dp)
                    .height(48.dp)
                    .rotate(-90f),
                contentAlignment = Alignment.Center
            ) {
            // Crop the visual TOP by ~1 cm (≈ 64dp) using start padding before rotation mapping
            Slider(
                value = currentEv,
                onValueChange = { newValue ->
                    // CRITICAL: Validate input to prevent NaN/Infinity from reaching camera HAL
                    // which causes crashes in tonemap processing
                    if (!newValue.isFinite()) {
                        android.util.Log.e(TAG, "Invalid slider value: $newValue (not finite), ignoring")
                        return@Slider
                    }

                    val rounded = (newValue * 10).toInt() / 10f

                    // Additional safety check after rounding
                    if (!rounded.isFinite() || rounded !in evRange) {
                        android.util.Log.e(TAG, "Invalid rounded value: $rounded (expected in $evRange), ignoring")
                        return@Slider
                    }

                    onEvChanged(rounded)
                },
                valueRange = evRange,
                steps = 39,
                colors = SliderDefaults.colors(
                    thumbColor = evColor,
                    activeTrackColor = evColor.copy(alpha = 0.8f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }

        // Scale marks removed

        if (abs(currentEv) > 0.05f) {
            IconButton(
                onClick = onResetEv,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset EV",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        }
    }
}
