package com.example.b1void.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.b1void.services.DropboxService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.LinkedList
import javax.inject.Inject

@HiltViewModel
class FileManagerViewModel @Inject constructor(
    private val dropboxService: DropboxService
) : ViewModel() {

    private val directoryStack: LinkedList<File> = LinkedList()
    private val _filesState = MutableLiveData<FilesState>(FilesState.Idle)
    val filesState: LiveData<FilesState> = _filesState

    sealed class FilesState {
        object Idle : FilesState()
        object Loading : FilesState()
        data class Success(val files: List<File>) : FilesState()
        data class Error(val message: String) : FilesState()
    }

    fun loadDirectoryContent(directory: File) {
        viewModelScope.launch {
            try {
                _filesState.value = FilesState.Loading
                
                val dropboxPath = dropboxService.getCurrentDropboxPath(directory, dropboxService.getAppDirectory())
                val files = withContext(Dispatchers.IO) {
                    dropboxService.loadDirectoryContent(directory, dropboxPath)
                }
                
                _filesState.value = FilesState.Success(files)
            } catch (e: Exception) {
                Timber.e(e, "Error loading directory content")
                _filesState.value = FilesState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun refreshFiles() {
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
            val previousDirectory = directoryStack.lastOrNull() ?: dropboxService.getAppDirectory()
            loadDirectoryContent(previousDirectory)
            true
        } else {
            false
        }
    }

    fun getCurrentDirectory(): File {
        return directoryStack.lastOrNull() ?: dropboxService.getAppDirectory()
    }

    fun getCurrentDirectoryName(): String {
        return getCurrentDirectory().name
    }

    fun createFolder(folderName: String, appDirectory: File) {
        viewModelScope.launch {
            try {
                val currentDirectory = getCurrentDirectory()
                val dropboxPath = dropboxService.getCurrentDropboxPath(currentDirectory, appDirectory)
                
                withContext(Dispatchers.IO) {
                    dropboxService.createFolder(folderName, currentDirectory, dropboxPath)
                }
                
                loadDirectoryContent(currentDirectory)
            } catch (e: Exception) {
                Timber.e(e, "Error creating folder")
                _filesState.value = FilesState.Error(e.message ?: "Error creating folder")
            }
        }
    }

    fun deleteFile(file: File) {
        viewModelScope.launch {
            try {
                val currentDirectory = getCurrentDirectory()
                val appDirectory = dropboxService.getAppDirectory()
                val dropboxPath = dropboxService.getCurrentDropboxPath(currentDirectory, appDirectory)
                
                withContext(Dispatchers.IO) {
                    dropboxService.deleteFile(file, dropboxPath)
                }
                
                loadDirectoryContent(currentDirectory)
            } catch (e: Exception) {
                Timber.e(e, "Error deleting file")
                _filesState.value = FilesState.Error(e.message ?: "Error deleting file")
            }
        }
    }
}

