package com.example.b1void.core.camera.di

import com.example.b1void.core.camera.CameraControllerProvider
import com.example.b1void.core.camera.CameraXControllerProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    @Binds
    abstract fun bindCameraControllerProvider(
        impl: CameraXControllerProvider
    ): CameraControllerProvider
}