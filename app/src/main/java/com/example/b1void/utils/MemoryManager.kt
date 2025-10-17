package com.example.b1void.utils

import android.app.ActivityManager
import android.content.Context
import android.content.ComponentCallbacks2
import android.util.Log
import com.example.b1void.B1VoidApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object MemoryManager {
    
    private const val TAG = "MemoryManager"
    
    /**
     * Проверяет доступную память
     */
    fun getAvailableMemory(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }
    
    /**
     * Проверяет, является ли устройство слабым
     */
    fun isLowEndDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Устройство считается слабым, если доступно меньше 100MB памяти
        return memoryInfo.availMem < 100 * 1024 * 1024
    }
    
    /**
     * Проверяет, достаточно ли памяти для операции
     */
    fun hasEnoughMemory(context: Context, requiredMemory: Long): Boolean {
        val availableMemory = getAvailableMemory(context)
        return availableMemory > requiredMemory
    }
    
    /**
     * Очищает память при необходимости
     */
    fun clearMemoryIfNeeded(context: Context) {
        val availableMemory = getAvailableMemory(context)

        if (availableMemory < B1VoidApplication.getLowMemoryThreshold(context)) {
            Log.w(TAG, "Мало памяти, очищаем кэш")

            CoroutineScope(Dispatchers.IO).launch {
                // Очищаем кэш изображений
                ImageOptimizer.clearImageCache(context)

                // Принудительно вызываем сборщик мусора
                System.gc()
            }
        }
    }
    
    /**
     * Обработчик событий памяти
     */
    fun onTrimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                Log.d(TAG, "UI скрыт, очищаем UI кэш")
                // Очищаем UI кэш
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> {
                Log.d(TAG, "Умеренное давление памяти")
                // Очищаем ненужные ресурсы
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                Log.w(TAG, "Низкое давление памяти")
                // Очищаем больше ресурсов
                System.gc()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                Log.e(TAG, "Критическое давление памяти")
                // Очищаем все возможные ресурсы
                System.gc()
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                Log.d(TAG, "Приложение в фоне")
                // Очищаем фоновые ресурсы
            }
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                Log.d(TAG, "Умеренное давление памяти в фоне")
                // Очищаем больше фоновых ресурсов
            }
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                Log.w(TAG, "Полное давление памяти в фоне")
                // Очищаем все ресурсы
                System.gc()
            }
        }
    }
    
    /**
     * Получает информацию о памяти устройства
     */
    fun getMemoryInfo(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val availableMB = memoryInfo.availMem / (1024 * 1024)
        val totalMB = memoryInfo.totalMem / (1024 * 1024)
        val thresholdMB = memoryInfo.threshold / (1024 * 1024)
        
        return "Доступно: ${availableMB}MB, Всего: ${totalMB}MB, Порог: ${thresholdMB}MB"
    }
    
    /**
     * Оптимизирует настройки для слабых устройств
     */
    fun getOptimizedSettings(context: Context): Map<String, Any> {
        val isLowEnd = isLowEndDevice(context)
        
        return mapOf(
            "image_quality" to if (isLowEnd) 70 else 90,
            "max_image_size" to if (isLowEnd) 800 else 1024,
            "cache_size" to if (isLowEnd) 25 * 1024 * 1024 else 50 * 1024 * 1024, // 25MB vs 50MB
            "enable_animations" to !isLowEnd,
            "enable_background_processing" to !isLowEnd
        )
    }
} 