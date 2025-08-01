package com.example.b1void

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class B1VoidApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Инициализация логирования
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
} 