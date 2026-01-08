package com.example.b1void.core.domain.usecase

import com.example.b1void.core.domain.repository.PhotoRepository
import javax.inject.Inject

class CapturePhotoUseCase @Inject constructor(
    private val photoRepository: PhotoRepository
) {
    suspend operator fun invoke(uri: String): Result<Unit> {
        return photoRepository.savePhoto(uri)
    }
}
