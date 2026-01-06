package com.example.b1void.core.domain.usecase

import com.example.b1void.core.domain.repository.FolderRepository
import java.io.File
import javax.inject.Inject

class CreateFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(folder: File) = folderRepository.createFolder(folder)
}
