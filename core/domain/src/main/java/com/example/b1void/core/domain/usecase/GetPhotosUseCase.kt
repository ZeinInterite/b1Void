package com.example.b1void.core.domain.usecase

import com.example.b1void.core.domain.repository.PhotoRepository
import com.example.b1void.core.model.Photo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPhotosUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    operator fun invoke(): Flow<List<Photo>> {
        return photoRepository.getPhotos()
    }
}
