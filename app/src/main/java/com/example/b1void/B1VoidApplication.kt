package com.example.b1void

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.multidex.MultiDex
import androidx.work.Configuration // Явный импорт для Configuration
import androidx.work.Configuration.Provider as WorkConfigurationProvider // Явный импорт для Provider с псевдонимом
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator
import com.example.b1void.utils.DropboxClientFactory
import com.example.b1void.data.AppSettingsBootstrap
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class B1VoidApplication : Application(), WorkConfigurationProvider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Restore persisted settings at app startup
        AppSettingsBootstrap.init(this)
        setupOptimizedGlideConfiguration()
        DropboxClientFactory.init("YOUR_ACCESS_TOKEN")
    }

    private fun setupOptimizedGlideConfiguration() {
        val deviceMemoryClass = getDeviceMemoryClass()
        val isLowRamDevice = isLowRamDevice()

        android.util.Log.d("B1VoidApplication", "Device memory class: $deviceMemoryClass MB, Low RAM: $isLowRamDevice")

        Glide.init(this, GlideBuilder().apply {
            // Адаптивные настройки в зависимости от устройства
            val memoryMultiplier = when {
                isLowRamDevice -> 0.5f  // Для Android Go и слабых устройств
                deviceMemoryClass <= 128 -> 1.0f  // До 2GB RAM
                deviceMemoryClass <= 256 -> 1.5f  // 2-4GB RAM
                deviceMemoryClass <= 512 -> 2.0f  // 4-8GB RAM
                else -> 2.5f  // 8GB+ RAM
            }

            val calculator = MemorySizeCalculator.Builder(this@B1VoidApplication)
                .setMemoryCacheScreens(memoryMultiplier)
                .setBitmapPoolScreens(memoryMultiplier)
                .build()

            setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))

            // Адаптивный размер disk cache
            val diskCacheSize = when {
                isLowRamDevice -> 25 * 1024 * 1024L  // 25MB для слабых устройств
                deviceMemoryClass <= 128 -> 50 * 1024 * 1024L  // 50MB
                deviceMemoryClass <= 256 -> 100 * 1024 * 1024L  // 100MB
                else -> 150 * 1024 * 1024L  // 150MB для мощных устройств
            }

            setDiskCache(
                ExternalPreferredCacheDiskCacheFactory(
                    this@B1VoidApplication,
                    "glide_cache",
                    diskCacheSize
                )
            )

            setDefaultRequestOptions(
                com.bumptech.glide.request.RequestOptions()
                    .dontAnimate()
                    .apply {
                        // Для слабых устройств используем более агрессивное сжатие
                        if (isLowRamDevice) {
                            downsample(com.bumptech.glide.load.resource.bitmap.DownsampleStrategy.AT_MOST)
                            format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
                        }
                    }
            )

            android.util.Log.d("B1VoidApplication", "Glide configured: memory cache=${calculator.memoryCacheSize / 1024 / 1024}MB, " +
                    "disk cache=${diskCacheSize / 1024 / 1024}MB")
        })
    }

    private fun getDeviceMemoryClass(): Int {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.memoryClass
    }

    private fun isLowRamDevice(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.isLowRamDevice
        } else {
            getDeviceMemoryClass() <= 64 // Считаем устройства с <=64MB heap как low RAM
        }
    }

    // Изменено на свойство Kotlin
    override val workManagerConfiguration: Configuration
        get() {
            // Адаптируем WorkManager под ресурсы устройства
            val isLowRam = isLowRamDevice()

            return Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .setMaxSchedulerLimit(if (isLowRam) 2 else 3)
                .build()
        }

    companion object {
        // Динамический threshold в зависимости от устройства
        fun getLowMemoryThreshold(context: Context): Long {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryClass = activityManager.memoryClass

            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && activityManager.isLowRamDevice -> 20 * 1024 * 1024L  // 20MB
                memoryClass <= 64 -> 30 * 1024 * 1024L  // 30MB
                memoryClass <= 128 -> 50 * 1024 * 1024L  // 50MB
                memoryClass <= 256 -> 75 * 1024 * 1024L  // 75MB
                else -> 100 * 1024 * 1024L  // 100MB
            }
        }

        const val IMAGE_COMPRESSION_QUALITY = 80

        // Добавьте метод для получения оптимального размера изображения
        fun getOptimalImageSize(context: Context): Int {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val isLowRam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                activityManager.isLowRamDevice
            } else {
                activityManager.memoryClass <= 64
            }

            return when {
                isLowRam -> 512  // 512px для слабых устройств
                activityManager.memoryClass <= 128 -> 768  // 768px
                activityManager.memoryClass <= 256 -> 1024  // 1024px
                else -> 1536  // 1536px для мощных устройств
            }
        }

        @Deprecated("Use getOptimalImageSize() instead", ReplaceWith("getOptimalImageSize(context)"))
        const val MAX_IMAGE_SIZE = 1024
    }
}

