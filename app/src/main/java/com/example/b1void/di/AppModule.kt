package com.example.b1void.di

import android.content.Context
import com.example.b1void.data.CameraSettingsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideCameraSettingsManager(@ApplicationContext context: Context): CameraSettingsManager =
        CameraSettingsManager(context)
}

