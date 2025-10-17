package com.example.b1void.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Утилита для определения информации об устройстве и производителе
 * для адаптации приложения под различные OEM
 */
object DeviceInfo {
    val manufacturer: String = Build.MANUFACTURER.lowercase()
    val model: String = Build.MODEL.lowercase()
    val brand: String = Build.BRAND.lowercase()
    val androidVersion: Int = Build.VERSION.SDK_INT

    fun isSamsung() = manufacturer == "samsung"
    fun isXiaomi() = manufacturer in listOf("xiaomi", "redmi")
    fun isHuawei() = manufacturer in listOf("huawei", "honor")
    fun isOppo() = manufacturer in listOf("oppo", "realme")
    fun isOnePlus() = manufacturer == "oneplus"
    fun isVivo() = manufacturer == "vivo"
    fun isMotorola() = manufacturer in listOf("motorola", "lenovo")
    fun isSony() = manufacturer == "sony"

    fun isLowEndDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            activityManager.isLowRamDevice || activityManager.memoryClass <= 128
        } else {
            activityManager.memoryClass <= 64
        }
    }

    fun getMemoryClass(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.memoryClass
    }

    fun getDeviceName(): String {
        return "$manufacturer $model"
    }

    fun logDeviceInfo(context: Context): String {
        return """
            |Manufacturer: $manufacturer
            |Model: $model
            |Brand: $brand
            |Android Version: $androidVersion (API ${Build.VERSION.SDK_INT})
            |Memory Class: ${getMemoryClass(context)}MB
            |Low RAM Device: ${isLowEndDevice(context)}
        """.trimMargin()
    }
}
