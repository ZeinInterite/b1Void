package com.example.b1void.feature.camera.di

import com.example.b1void.core.camera.CameraXControllerProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CameraProviderEntryPoint {
    fun cameraXControllerProvider(): CameraXControllerProvider
}
