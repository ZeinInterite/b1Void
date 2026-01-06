package com.example.b1void.core.camera.quirks

import android.os.Build
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Менеджер для изоляции и централизации специфических для устройства "особенностей" (quirks) и обходных решений.
 * Это предотвращает загрязнение бизнес-логики проверками производителя устройства.
 */
@Singleton
class DeviceQuirksManager @Inject constructor() {

    /**
     * Проверяет, является ли текущее устройство устройством Xiaomi, Redmi или Poco.
     * Эти устройства исторически имели специфические проблемы с камерой,
     * которые требовали обходных решений.
     *
     * @return true, если устройство от Xiaomi, Redmi или Poco, иначе false.
     */
    fun isXiaomiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer == "xiaomi" || manufacturer == "redmi" || manufacturer == "poco"
    }

    // Дополнительные функции для специфической логики обхода проблем могут быть добавлены здесь
    // Например, для получения смещения EV, ISO и т.д.
    // Пример:
    // fun getEvCompensationBoost(): Float {
    //     return if (isXiaomiDevice()) 1.0f else 0.0f
    // }
}
