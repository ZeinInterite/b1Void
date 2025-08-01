package com.example.b1void

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import androidx.work.Configuration // Явный импорт для Configuration
import androidx.work.Configuration.Provider as WorkConfigurationProvider // Явный импорт для Provider с псевдонимом
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.load.engine.cache.MemorySizeCalculator

class B1VoidApplication : Application(), WorkConfigurationProvider { // Используем псевдоним

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        setupGlideOptimization()
    }

    private fun setupGlideOptimization() {
        Glide.init(this, GlideBuilder().apply {
            val calculator = MemorySizeCalculator.Builder(this@B1VoidApplication)
                .setMemoryCacheScreens(2f)
                .build()
            setMemoryCache(LruResourceCache(calculator.memoryCacheSize.toLong()))
            setDiskCache(ExternalPreferredCacheDiskCacheFactory(this@B1VoidApplication, "glide_cache", 50 * 1024 * 1024))
            setDefaultRequestOptions(
                com.bumptech.glide.request.RequestOptions()
                    .dontAnimate()
            )
        })
    }

    // Изменено на свойство Kotlin
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder() // Используем импортированный Configuration
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .setMaxSchedulerLimit(3)
            .build()

    companion object {
        const val LOW_MEMORY_THRESHOLD = 50 * 1024 * 1024
        const val IMAGE_COMPRESSION_QUALITY = 80
        const val MAX_IMAGE_SIZE = 1024
    }
}
