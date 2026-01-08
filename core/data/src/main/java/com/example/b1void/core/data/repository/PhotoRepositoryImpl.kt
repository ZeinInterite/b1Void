package com.example.b1void.core.data.repository

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import com.example.b1void.core.data.database.dao.InspectorDao
import com.example.b1void.core.domain.repository.PhotoRepository
import com.example.b1void.core.model.Photo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotoRepositoryImpl @Inject constructor(
    private val application: Application,
    private val inspectorDao: InspectorDao
) : PhotoRepository {

    override fun getPhotos(): Flow<List<Photo>> = flow {
        val photos = mutableListOf<Photo>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        application.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photos.add(Photo(id, contentUri, displayName))
            }
        }
        emit(photos)
    }

    override suspend fun deletePhoto(photo: Photo): Result<Unit> {
        return try {
            val rowsDeleted = application.contentResolver.delete(photo.uri, null, null)
            if (rowsDeleted > 0) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete photo"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun savePhoto(uri: String): Result<Unit> {
        // TODO: Implement logic to save photo metadata to Room or other database if needed.
        // For now, the photo is already saved in the MediaStore by the CameraXController.
        return Result.success(Unit)
    }
}
