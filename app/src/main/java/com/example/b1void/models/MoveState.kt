package com.example.b1void.models

import com.example.b1void.core.model.FolderNode
import java.io.File

/**
 * Состояние UI для экрана перемещения.
 */
sealed class MoveUiState {
    object Loading : MoveUiState()
    data class Success(val folderTree: List<FolderNode>) : MoveUiState()
    data class Error(val message: String) : MoveUiState()
}

/**
 * Состояние выбранной папки (для обновления кнопки и breadcrumbs).
 */
data class SelectedFolderState(
    val breadcrumbs: String = "Выберите папку",
    val isMoveButtonEnabled: Boolean = false
)
