package com.example.b1void.models

import java.io.File

/**
 * Узел в древовидной структуре папок.
 * @param file Ссылка на сам файл папки.
 * @param level Уровень вложенности (0 для корневых папок).
 * @param isExpanded Раскрыт ли узел в дереве.
 * @param isSelected Выбран ли узел как папка назначения.
 * @param children Список дочерних узлов.
 */
data class FolderNode(
    val file: File,
    val level: Int = 0,
    var isExpanded: Boolean = false,
    var isSelected: Boolean = false,
    val children: MutableList<FolderNode> = mutableListOf()
)

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
