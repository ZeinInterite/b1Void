package com.example.b1void.camera.di

import com.example.b1void.camera.focus.FocusCoordinator
import com.example.b1void.camera.focus.FocusProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object FocusModule {
    interface Factory {
        fun create(
            params: Params
        ): FocusCoordinator
    }

    data class Params(
        val previewView: androidx.camera.view.PreviewView,
        val camera: androidx.camera.core.Camera,
        val mainExecutor: java.util.concurrent.Executor,
        val callbacks: FocusCoordinator.Callbacks,
        val config: FocusCoordinator.Config = FocusCoordinator.Config()
    )

    @Provides
    fun provideFactory(): Factory = object : Factory {
        override fun create(params: Params): FocusCoordinator =
            FocusProvider.create(
                previewView = params.previewView,
                camera = params.camera,
                mainExecutor = params.mainExecutor,
                callbacks = params.callbacks,
                config = params.config
            )
    }
}

