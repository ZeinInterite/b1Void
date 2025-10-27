package com.example.b1void.ui.camera

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * ExposureControl - UI компонент для ручной настройки экспозиции (EV compensation)
 *
 * Особенности:
 * - Вертикальный Slider для регулировки EV от -2.0 до +2.0
 * - Цветовая индикация: негативные (оранжевый), позитивные (синий), ноль (белый)
 * - Крупная touch-область для использования в перчатках (минимум 48dp)
 * - Анимированное изменение значений
 * - Кнопка Reset для быстрого сброса на 0.0
 *
 * @param currentEv Текущее значение EV compensation
 * @param evRange Диапазон поддерживаемых EV значений камерой
 * @param onEvChanged Callback при изменении EV значения
 * @param onResetEv Callback при нажатии кнопки Reset
 * @param modifier Modifier для кастомизации
 */
@Composable
fun ExposureControl(
    currentEv: Float,
    evRange: ClosedFloatingPointRange<Float> = -2.0f..2.0f,
    onEvChanged: (Float) -> Unit,
    onResetEv: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Анимация для плавного изменения отображаемого значения
    val animatedEv by animateFloatAsState(
        targetValue = currentEv,
        animationSpec = tween(durationMillis = 150),
        label = "ev_animation"
    )

    // Цветовая схема в зависимости от значения EV
    val evColor = remember(animatedEv) {
        when {
            animatedEv < -0.1f -> EvNegativeColor // Оранжевый для негативных значений
            animatedEv > 0.1f -> EvPositiveColor  // Синий для позитивных значений
            else -> EvNeutralColor                 // Белый для нуля
        }
    }

    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Текст с текущим значением EV
        Text(
            text = formatEvValue(animatedEv),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = evColor
        )

        // Метка "EV"
        Text(
            text = "EV",
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = Color.White.copy(alpha = 0.7f)
        )

        // Вертикальный Slider
        Box(
            modifier = Modifier
                .height(200.dp)
                .width(48.dp), // Минимум 48dp для touch target
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = currentEv,
                onValueChange = { newValue ->
                    // Округляем до 0.1 для удобства
                    val rounded = (newValue * 10).toInt() / 10f
                    onEvChanged(rounded)
                },
                valueRange = evRange,
                steps = 39, // 40 шагов для диапазона -2.0..2.0 с шагом 0.1
                colors = SliderDefaults.colors(
                    thumbColor = evColor,
                    activeTrackColor = evColor.copy(alpha = 0.8f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(48.dp)
            )
        }

        // Индикаторы диапазона
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "+${evRange.endInclusive}",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Text(
                text = "0",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f)
            )

            Text(
                text = "${evRange.start}",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Кнопка Reset
        if (abs(currentEv) > 0.05f) {
            IconButton(
                onClick = onResetEv,
                modifier = Modifier.size(40.dp)
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

/**
 * Форматирует значение EV для отображения
 * Примеры: "+1.5 EV", "0.0 EV", "-0.5 EV"
 */
private fun formatEvValue(ev: Float): String {
    return when {
        ev > 0.05f -> "+%.1f EV".format(ev)
        ev < -0.05f -> "%.1f EV".format(ev)
        else -> "0.0 EV"
    }
}

// Цветовая схема
private val EvNegativeColor = Color(0xFFFF9800) // Orange для уменьшения экспозиции
private val EvPositiveColor = Color(0xFF2196F3) // Blue для увеличения экспозиции
private val EvNeutralColor = Color(0xFFFFFFFF)  // White для нейтрального значения
