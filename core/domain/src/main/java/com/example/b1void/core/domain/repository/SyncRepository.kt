package com.example.b1void.core.domain.repository

interface SyncRepository {
    suspend fun syncPendingFiles(): Result<Unit>
}
