package com.example.b1void

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import androidx.work.Configuration
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator

class B1VoidApplication : Application(), Configuration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        
        // Оптимизация Glide для слабых устройств
        setupGlideOptimization()
        
        // Инициализация WorkManager
        WorkManager.initialize(this, workManagerConfiguration)
    }

    private fun setupGlideOptimization() {
        Glide.init(this, GlideBuilder().apply {
            // Уменьшаем размер кэша в памяти для слабых устройств
            val calculator = MemorySizeCalculator.Builder(this@B1VoidApplication)
                .setMemoryCacheScreens(2f) // Уменьшаем с 4 до 2 экранов
                .build()
            
            setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
            
            // Настраиваем диск кэш
            setDiskCache(ExternalPreferredCacheDiskCacheFactory(this@B1VoidApplication, "glide_cache", 50 * 1024 * 1024)) // 50MB
            
            // Отключаем анимации для экономии ресурсов
            setDefaultRequestOptions(
                com.bumptech.glide.request.RequestOptions()
                    .dontAnimate()
            )
        })
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setMaxSchedulerLimit(3) // Ограничиваем количество одновременных задач
            .build()
    }

    companion object {
        // Константы для оптимизации
        const val LOW_MEMORY_THRESHOLD = 50 * 1024 * 1024 // 50MB
        const val IMAGE_COMPRESSION_QUALITY = 80
        const val MAX_IMAGE_SIZE = 1024 // Максимальный размер изображения
    }
} 