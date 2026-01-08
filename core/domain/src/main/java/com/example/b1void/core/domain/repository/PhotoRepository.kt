package com.example.b1void.core.domain.repository

import com.example.b1void.core.model.Photo
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    fun getPhotos(): Flow<List<Photo>>
    suspend fun deletePhoto(photo: Photo): Result<Unit>
    suspend fun savePhoto(uri: String): Result<Unit>
}
