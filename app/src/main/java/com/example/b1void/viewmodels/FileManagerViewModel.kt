package com.example.b1void.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.b1void.services.DropboxService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.LinkedList

class FileManagerViewModel(private val dropboxService: DropboxService) : ViewModel() {

    private val directoryStack: LinkedList<File> = LinkedList()
    private val _filesState = MutableStateFlow<FilesState>(FilesState.Idle)
    val filesState: StateFlow<FilesState> = _filesState

    sealed class FilesState {
        object Idle : FilesState()
        object Loading : FilesState()
        data class Success(val files: List<File>) : FilesState()
        data class Error(val message: String) : FilesState()
    }


    fun loadDirectoryContent(directory: File) {
        viewModelScope.launch {
            _filesState.value = FilesState.Loading
            try {
                val files = dropboxService.loadDirectoryContent(directory, getCurrentDropboxPath(directory))
                _filesState.value = FilesState.Success(files)
            } catch (e: Exception) {
                _filesState.value = FilesState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refreshFiles(appDirectory: File) {
        viewModelScope.launch {
            loadDirectoryContent(getCurrentDirectory())
        }
    }

    fun openDirectory(file: File) {
        if (directoryStack.isEmpty() || directoryStack.lastOrNull() != file) {
            directoryStack.add(file)
        }
        loadDirectoryContent(file)
    }

    fun onBackPressed(): Boolean {
        return if (directoryStack.isNotEmpty()) {
            directoryStack.removeLast()
            val previousDirectory = directoryStack.lastOrNull() ?: getInitialDirectory()
            loadDirectoryContent(previousDirectory)
            true
        } else {
            false
        }
    }

    internal fun getCurrentDirectory(): File {
        return directoryStack.lastOrNull() ?: getInitialDirectory()
    }

    fun getCurrentDirectoryName(): String {
        return getCurrentDirectory().name
    }

    fun createFolder(folderName: String, appDirectory: File) {
        viewModelScope.launch {
            try {                 dropboxService.createFolder(folderName, getCurrentDirectory(), getCurrentDropboxPath(getCurrentDirectory()))
                loadDirectoryContent(getCurrentDirectory())
            } catch (e: Exception) {
                _filesState.value = FilesState.Error(e.message ?: "Error creating folder")
            }
        }
    }

    fun deleteFile(file: File, appDirectory: File) {
        viewModelScope.launch {
            try {
                dropboxService.deleteFile(file, getCurrentDropboxPath(getCurrentDirectory()))
                loadDirectoryContent(getCurrentDirectory())
            } catch (e: Exception) {
                _filesState.value = FilesState.Error(e.message ?: "Error deleting file")
            }
        }
    }
    fun createDropboxFolder(path: String){
        viewModelScope.launch {
            try {
                dropboxService.createDropboxFolder(path)
            } catch (e: Exception) {
                _filesState.value = FilesState.Error(e.message ?: "Error creating dropbox folder")
            }
        }
    }


    private fun getInitialDirectory() = dropboxService.getAppDirectory()

    private fun getCurrentDropboxPath(directory: File) =
        dropboxService.getCurrentDropboxPath(directory, getInitialDirectory())


    class Factory(private val dropboxService: DropboxService) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FileManagerViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return FileManagerViewModel(dropboxService) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

