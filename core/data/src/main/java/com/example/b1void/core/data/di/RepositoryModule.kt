package com.example.b1void.core.data.di

import com.example.b1void.core.data.repository.PhotoRepositoryImpl
import com.example.b1void.core.data.repository.SyncRepositoryImpl
import com.example.b1void.core.domain.repository.PhotoRepository
import com.example.b1void.core.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPhotoRepository(
        photoRepositoryImpl: PhotoRepositoryImpl
    ): PhotoRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        syncRepositoryImpl: SyncRepositoryImpl
    ): SyncRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        folderRepositoryImpl: com.example.b1void.core.data.repository.FolderRepositoryImpl
    ): com.example.b1void.core.domain.repository.FolderRepository
}