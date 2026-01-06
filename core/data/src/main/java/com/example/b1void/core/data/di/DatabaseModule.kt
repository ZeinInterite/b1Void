package com.example.b1void.core.data.di

import android.content.Context
import androidx.room.Room
import com.example.b1void.core.data.database.B1VoidDatabase
import com.example.b1void.core.data.database.dao.InspectorDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): B1VoidDatabase {
        return Room.databaseBuilder(
            context,
            B1VoidDatabase::class.java,
            "b1void-database"
        ).build()
    }

    @Provides
    fun provideInspectorDao(database: B1VoidDatabase): InspectorDao {
        return database.inspectorDao()
    }
}
