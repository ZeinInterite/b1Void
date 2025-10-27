package com.example.b1void.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * DynamicExposureSlider - появляется при long-press и позволяет регулировать EV вертикальным swipe
 *
 * UX поведение:
 * 1. Long-press на экране → появляется слайдер в месте нажатия
 * 2. Удерживая палец, двигаем вверх/вниз → изменяется EV
 * 3. Отпускаем палец → слайдер исчезает с задержкой 1.5с
 *
 * @param currentEv Текущее значение EV compensation
 * @param evRange Диапазон поддерживаемых EV значений
 * @param position Позиция где появится слайдер (точка long-press)
 * @param onEvChanged Callback при изменении EV
 * @param onDismiss Callback когда слайдер нужно скрыть
 * @param modifier Modifier
 */
@Composable
fun DynamicExposureSlider(
    currentEv: Float,
    evRange: ClosedFloatingPointRange<Float>,
    position: Offset,
    onEvChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Локальное состояние для drag
    var localEv by remember(currentEv) { mutableStateOf(currentEv) }
    var isDragging by remember { mutableStateOf(false) }

    // Автоматическое скрытие после отпускания пальца
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            kotlinx.coroutines.delay(1500) // 1.5 секунды
            onDismiss()
        }
    }

    // Анимация для плавного изменения
    val animatedEv by animateFloatAsState(
        targetValue = localEv,
        animationSpec = tween(durationMillis = 100),
        label = "ev_animation"
    )

    // Цвет в зависимости от значения
    val evColor = remember(animatedEv) {
        when {
            animatedEv < -0.1f -> Color(0xFFFF9800) // Orange
            animatedEv > 0.1f -> Color(0xFF2196F3)  // Blue
            else -> Color(0xFFFFFFFF)                // White
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDragEnd = {
                        isDragging = false
                        // Применяем финальное значение
                        onEvChanged(localEv)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()

                        // Вертикальное движение: вверх = +EV (светлее), вниз = -EV (темнее)
                        // Sensitivity: 150px ≈ 1 EV
                        val evDelta = -dragAmount.y / 150f
                        val newEv = (localEv + evDelta).coerceIn(evRange)

                        // Округляем до 0.1
                        localEv = (newEv * 10).roundToInt() / 10f

                        // Применяем в реальном времени
                        onEvChanged(localEv)
                    }
                )
            }
    ) {
        // Слайдер UI
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(200)) + scaleIn(tween(200)),
            exit = fadeOut(tween(300)) + scaleOut(tween(300))
        ) {
            ExposureSliderContent(
                currentEv = animatedEv,
                evRange = evRange,
                evColor = evColor,
                position = position,
                isDragging = isDragging
            )
        }
    }
}

@Composable
private fun ExposureSliderContent(
    currentEv: Float,
    evRange: ClosedFloatingPointRange<Float>,
    evColor: Color,
    position: Offset,
    isDragging: Boolean
) {
    // Вертикальный слайдер рядом с точкой нажатия
    Column(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (position.x + 60).roundToInt(), // Справа от точки нажатия
                    y = (position.y - 150).roundToInt() // Центрируем по вертикали
                )
            }
            .width(56.dp)
            .height(300.dp)
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Метка "EV"
        Text(
            text = "EV",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.7f)
        )

        // Текущее значение EV
        Text(
            text = formatEvValue(currentEv),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = evColor
        )

        // Визуальная шкала
        Box(
            modifier = Modifier
                .weight(1f)
                .width(4.dp)
                .background(
                    color = Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(2.dp)
                )
        ) {
            // Индикатор текущего положения
            val normalizedPosition = ((currentEv - evRange.start) /
                (evRange.endInclusive - evRange.start)).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = ((1f - normalizedPosition) * 236).dp) // 236dp ≈ height - padding
                    .size(12.dp)
                    .background(
                        color = evColor,
                        shape = CircleShape
                    )
            )
        }

        // Метки диапазона
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "+${evRange.endInclusive}",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "0",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = "${evRange.start}",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        // Индикатор активности
        if (isDragging) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = evColor.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
            )
        }
    }

    // Индикатор позиции long-press
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = position.x.roundToInt(),
                    y = position.y.roundToInt()
                )
            }
            .size(if (isDragging) 64.dp else 48.dp)
            .alpha(if (isDragging) 0.8f else 0.5f)
            .background(
                color = evColor.copy(alpha = 0.3f),
                shape = CircleShape
            )
    )
}

/**
 * Форматирует значение EV для отображения
 */
private fun formatEvValue(ev: Float): String {
    return when {
        ev > 0.05f -> "+%.1f".format(ev)
        ev < -0.05f -> "%.1f".format(ev)
        else -> "0.0"
    }
}
