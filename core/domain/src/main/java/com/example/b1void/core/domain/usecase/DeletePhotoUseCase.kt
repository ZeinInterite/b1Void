package com.example.b1void.core.domain.usecase

import com.example.b1void.core.domain.repository.PhotoRepository
import com.example.b1void.core.model.Photo
import javax.inject.Inject

class DeletePhotoUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    suspend operator fun invoke(photo: Photo) = photoRepository.deletePhoto(photo)
}
