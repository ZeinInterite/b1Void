package com.example.b1void.ui.camera

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.roundToInt

/**
 * iPhone-like Zoom control: preset chips + continuous rail with draggable handle.
 * Works in both portrait (horizontal rail) and landscape (vertical rail).
 */
@Composable
fun ZoomControl(
    zoomRatio: Float,
    minZoom: Float,
    maxZoom: Float,
    availablePresets: List<Float>,
    modifier: Modifier = Modifier,
    leftHanded: Boolean = false,
    onZoomChanged: (Float) -> Unit,
    onPresetSelected: (Float) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val haptics = LocalHapticFeedback.current

    // Normalized position in [0, 1] for the handle along the rail.
    val position by remember(zoomRatio, minZoom, maxZoom) {
        val t = if (maxZoom > minZoom) (zoomRatio - minZoom) / (maxZoom - minZoom) else 0f
        mutableStateOf(t.coerceIn(0f, 1f))
    }

    // Haptic ticks when crossing key presets: 0.5, 1, 2, 3
    val tickPoints = remember(availablePresets) { availablePresets.sorted().toFloatArray() }
    var lastTickIndex by remember { mutableStateOf(-1) }
    LaunchedEffect(zoomRatio, tickPoints) {
        // Find closest preset within small epsilon
        val idx = tickPoints.indexOfFirst { kotlin.math.abs(zoomRatio - it) < 0.03f }
        if (idx != -1 && idx != lastTickIndex) {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            lastTickIndex = idx
        }
    }

    val railThickness = 4.dp
    val handleSize = 24.dp
    val spacing = 16.dp
    val containerShape = RoundedCornerShape(20.dp)
    val containerPadding = 12.dp

    val containerModifier = Modifier
        .clip(containerShape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))

    if (isPortrait) {
        Column(
            modifier = modifier
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("ZoomRail"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Horizontal rail + compact zoom label on the right
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZoomRail(
                    position = position,
                    length = 240.dp,
                    thickness = railThickness,
                    handle = handleSize,
                    orientation = Orientation.Horizontal,
                    onPositionChanged = { t ->
                        onZoomChanged(lerp(minZoom, maxZoom, t))
                    },
                    contentDescription = "Zoom"
                )
                Spacer(Modifier.width(8.dp))
                ZoomLabel(current = zoomRatio)
            }
        }
    } else {
        // Landscape: stack rail and chips vertically; anchor to lower long edge
        val railAlignment = if (leftHanded) Alignment.BottomStart else Alignment.BottomEnd
        Column(
            modifier = modifier
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = if (leftHanded) Alignment.Start else Alignment.End
        ) {
            // Landscape side rail (minimal footprint): only rail + tiny label, no chips
            Box(
                modifier = Modifier
                    .padding(containerPadding)
                    .offset(x = 18.9.dp, y = (-31.5).dp), // net: 0.3cm right -> 0.2cm left from previous (≈18.9dp)
                contentAlignment = railAlignment
            ) {
                ZoomRail(
                    position = position,
                    length = 240.dp,
                    thickness = railThickness,
                    handle = handleSize,
                    orientation = Orientation.Vertical,
                    onPositionChanged = { t -> onZoomChanged(lerp(minZoom, maxZoom, t)) },
                    contentDescription = "Zoom"
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-31.5).dp) // keep label above rail
                        .padding(6.dp)
                ) {
                    ZoomLabel(current = zoomRatio)
                }
            }
        }
    }
}

@Composable
private fun ZoomLabel(current: Float) {
    val text = when {
        current < 1f -> String.format("%.1fx", current)
        current < 10f -> String.format("%.1fx", current)
        else -> String.format("%dx", current.toInt())
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("ZoomLabel")
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
@Preview(showBackground = true, widthDp = 360, heightDp = 740)
private fun ZoomControlPortraitPreview() {
    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            ZoomControl(
                zoomRatio = 1.2f,
                minZoom = 0.5f,
                maxZoom = 5f,
                availablePresets = listOf(0.5f, 1f, 2f, 3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                onZoomChanged = {},
                onPresetSelected = {}
            )
        }
    }
}

@Composable
@Preview(showBackground = true, widthDp = 740, heightDp = 360)
private fun ZoomControlLandscapePreview() {
    MaterialTheme {
        Box(Modifier.fillMaxSize()) {
            ZoomControl(
                zoomRatio = 2.2f,
                minZoom = 0.7f,
                maxZoom = 8f,
                availablePresets = listOf(1f, 2f, 3f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                leftHanded = false,
                onZoomChanged = {},
                onPresetSelected = {}
            )
        }
    }
}

// Preset chips removed by request — compact control only.

@Composable
private fun ZoomRail(
    position: Float,
    length: Dp,
    thickness: Dp,
    handle: Dp,
    orientation: Orientation,
    onPositionChanged: (Float) -> Unit,
    contentDescription: String
) {
    val density = LocalDensity.current
    var normPos by remember { mutableStateOf(position.coerceIn(0f, 1f)) }
    // Keep internal position in sync with external state (e.g., preset taps or pinch)
    LaunchedEffect(position) {
        normPos = position.coerceIn(0f, 1f)
    }
    val dragState = rememberDraggableState { delta ->
        val lengthPx = with(density) { length.toPx() }.coerceAtLeast(1f)
        val d = when (orientation) {
            Orientation.Horizontal -> delta / lengthPx
            Orientation.Vertical -> -delta / lengthPx
        }.coerceIn(-0.2f, 0.2f)
        val newPos = (normPos + d).coerceIn(0f, 1f)
        if (newPos != normPos) {
            normPos = newPos
            onPositionChanged(newPos)
        }
    }

    // Smooth spring animation for handle
    val animPos by animateFloatAsState(
        targetValue = normPos.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow), label = "zoomPos"
    )

    var railSize by remember { mutableStateOf(IntSize.Zero) }
    val railModifier = Modifier
        .testTag("ZoomHandle")
        .semantics {
            // Announce control as adjustable range with current value for TalkBack
            this.contentDescription = contentDescription
            this.progressBarRangeInfo = ProgressBarRangeInfo(
                current = animPos,
                range = 0f..1f,
                steps = 0
            )
        }
        .onSizeChanged { railSize = it }
        .pointerInput(railSize, orientation) {
            // Jump to tap position on press
            detectTapGestures(
                onPress = { offset: Offset ->
                    val t = when (orientation) {
                        Orientation.Horizontal -> if (railSize.width > 0) (offset.x / railSize.width).coerceIn(0f, 1f) else 0f
                        Orientation.Vertical -> if (railSize.height > 0) (1f - (offset.y / railSize.height)).coerceIn(0f, 1f) else 0f
                    }
                    normPos = t
                    onPositionChanged(t)
                }
            )
        }
        .draggable(
            state = dragState,
            orientation = orientation,
            onDragStopped = { /* no-op */ }
        )

    if (orientation == Orientation.Horizontal) {
        Box(
            modifier = railModifier
                .width(length)
                .height(handle)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Transparent),
            contentAlignment = Alignment.CenterStart
        ) {
            // Rail line
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(thickness)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
            )
            // Handle
            Box(
                Modifier
                    .offset(x = ((length - handle) * animPos))
                    .size(handle)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
    } else {
        Box(
            modifier = railModifier
                .height(length)
                .width(handle)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Transparent),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Rail line
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(thickness)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f))
            )
            // Handle
            Box(
                Modifier
                    .offset(y = -((length - handle) * animPos))
                    .size(handle)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}


private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
