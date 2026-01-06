package com.example.b1void.core.network.di

import android.content.Context
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.example.b1void.core.network.storage.CloudStorage
import com.example.b1void.core.network.storage.impl.DropboxStorageSource
import com.example.b1void.core.network.storage.impl.FirebaseStorageSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FirebaseStorageQualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DropboxStorageQualifier

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    @Singleton
    @FirebaseStorageQualifier
    abstract fun bindFirebaseCloudStorage(
        firebaseStorageSource: FirebaseStorageSource
    ): CloudStorage

    @Binds
    @Singleton
    @DropboxStorageQualifier
    abstract fun bindDropboxCloudStorage(
        dropboxStorageSource: DropboxStorageSource
    ): CloudStorage

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

        @Provides
        @Singleton
        fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

        @Provides
        @Singleton
        fun provideDbxRequestConfig(@ApplicationContext context: Context): DbxRequestConfig {
            return DbxRequestConfig("b1Void/1.0", "en_US").also {
                // Дополнительная конфигурация, если требуется
            }
        }

        @Provides
        @Singleton
        fun provideDbxClientV2(requestConfig: DbxRequestConfig): DbxClientV2 {
            // Токен доступа Dropbox. В реальном приложении это должно быть безопасно получено.
            // Например, из настроек пользователя после аутентификации.
            val accessToken = "YOUR_DROPBOX_ACCESS_TOKEN" // ЗАМЕНИТЬ НА РЕАЛЬНЫЙ ТОКЕН
            return DbxClientV2(requestConfig, accessToken)
        }
    }
}
