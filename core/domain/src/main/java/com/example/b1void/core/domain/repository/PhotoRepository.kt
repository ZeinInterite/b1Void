package com.example.b1void.core.domain.repository

import com.example.b1void.core.model.Photo
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    suspend fun takePhoto(): Result<Unit> // Returns a Result indicating success or failure
    fun getPhotos(): Flow<List<Photo>>
    suspend fun deletePhoto(photo: Photo): Result<Unit>
}
