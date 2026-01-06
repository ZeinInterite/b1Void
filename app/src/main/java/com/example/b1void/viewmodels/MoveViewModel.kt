package com.example.b1void.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.b1void.data.FolderRepository
import com.example.b1void.core.model.FolderNode
import com.example.b1void.models.MoveUiState
import com.example.b1void.models.SelectedFolderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MoveViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    // В реальном приложении репозиторий бы инжектировался через Hilt/Koin
    private val folderRepository = FolderRepository()

    private val _uiState = MutableStateFlow<MoveUiState>(MoveUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _selectedFolderState = MutableStateFlow(SelectedFolderState())
    val selectedFolderState = _selectedFolderState.asStateFlow()

    private var fullTreeCache: List<FolderNode>? = null
    private var selectedNode: FolderNode? = null

    private val filesToMovePaths: List<String> = savedStateHandle.get<ArrayList<String>>("arg_file_ids") ?: emptyList()
    private val sourceFolderPath: String = savedStateHandle.get<String>("arg_source_folder_path") ?: ""
    private val rootFolderPath: String = savedStateHandle.get<String>("arg_root_folder_path") ?: ""
    private val trashFolderPath: String = savedStateHandle.get<String>("arg_trash_folder_path") ?: ""

    init {
        fetchFolderTree()
    }

    private fun fetchFolderTree() {
        viewModelScope.launch {
            _uiState.value = MoveUiState.Loading
            try {
                val tree = fullTreeCache ?: folderRepository.getFolderTree(File(rootFolderPath))
                fullTreeCache = tree
                _uiState.value = MoveUiState.Success(buildVisibleList(tree))
            } catch (e: Exception) {
                _uiState.value = MoveUiState.Error("Не удалось загрузить папки: ${e.message}")
            }
        }
    }

    fun toggleFolderExpansion(folderNode: FolderNode) {
        folderNode.isExpanded = !folderNode.isExpanded
        fullTreeCache?.let {
            _uiState.value = MoveUiState.Success(buildVisibleList(it))
        }
    }

    fun selectFolder(folderNode: FolderNode) {
        if (folderNode.file.absolutePath == sourceFolderPath || shouldSkipNode(folderNode)) return

        selectedNode?.isSelected = false
        folderNode.isSelected = true
        selectedNode = folderNode

        fullTreeCache?.let {
            _uiState.value = MoveUiState.Success(buildVisibleList(it))
        }

        _selectedFolderState.value = SelectedFolderState(
            breadcrumbs = generateBreadcrumbs(folderNode),
            isMoveButtonEnabled = true
        )
    }

    fun onSearchQueryChanged(query: String) {
        val tree = fullTreeCache ?: return
        val filteredList = if (query.isBlank()) {
            buildVisibleList(tree)
        } else {
            filterTree(tree, query)
        }
        _uiState.value = MoveUiState.Success(filteredList)
    }

    suspend fun moveSelectedFiles(): Boolean {
        val destination = selectedNode?.file ?: return false
        val files = filesToMovePaths.map { File(it) }
        return folderRepository.moveFiles(files, destination)
    }

    private fun buildVisibleList(nodes: List<FolderNode>): List<FolderNode> {
        val visibleList = mutableListOf<FolderNode>()
        nodes.forEach { node ->
            addNodeToList(node, visibleList)
        }
        return visibleList
    }

    private fun addNodeToList(node: FolderNode, list: MutableList<FolderNode>) {
        if (shouldSkipNode(node)) return
        list.add(node)
        if (node.isExpanded) {
            node.children.forEach { child ->
                addNodeToList(child, list)
            }
        }
    }

    private fun filterTree(nodes: List<FolderNode>, query: String): List<FolderNode> {
        val filtered = mutableListOf<FolderNode>()
        for (node in nodes) {
            if (shouldSkipNode(node)) continue
            val childrenMatch = filterTree(node.children, query)
            if (node.file.name.contains(query, ignoreCase = true) || childrenMatch.isNotEmpty()) {
                // Создаем копию, чтобы не изменять кэш
                val newNode = node.copy(children = childrenMatch.toMutableList(), isExpanded = true)
                filtered.add(newNode)
            }
        }
        return filtered
    }

    private fun generateBreadcrumbs(node: FolderNode): String {
        val rootPath = File(rootFolderPath).parentFile?.absolutePath ?: ""
        return node.file.absolutePath.removePrefix(rootPath).removePrefix("/")
    }

    private fun shouldSkipNode(node: FolderNode): Boolean {
        if (trashFolderPath.isBlank()) return false
        return node.file.absolutePath == trashFolderPath
    }
}
