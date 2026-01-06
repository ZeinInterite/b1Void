package com.example.b1void.core.domain.usecase

import com.example.b1void.core.domain.repository.SyncRepository
import javax.inject.Inject

class SyncUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return syncRepository.syncPendingFiles()
    }
}
