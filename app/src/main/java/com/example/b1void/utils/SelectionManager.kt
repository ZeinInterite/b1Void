package com.example.b1void.utils

import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.LinearLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.b1void.R
import com.example.b1void.adapters.FileAdapter
import java.io.File

class SelectionManager(
    private val buttonContainer: LinearLayout,
    private val selectionToolbar: LinearLayout,
    private val shareButton: Button,
    private val deleteButton: Button,
    private val moveButton: Button,
    private val selectAllButton: Button,
    private val clearSelectionButton: Button,
    private val swipeRefreshLayout: SwipeRefreshLayout,
    private val fileAdapter: FileAdapter
) {
    
    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()
    
    fun startSelectionMode() {
        isSelectionMode = true
        fileAdapter.isSelectionMode = true
        
        showSelectionUI()
        enableSelectionMode()
        
        // Анимация появления
        val slideIn = AnimationUtils.loadAnimation(buttonContainer.context, android.R.anim.slide_in_left)
        buttonContainer.startAnimation(slideIn)
        selectionToolbar.startAnimation(slideIn)
    }
    
    fun clearSelection() {
        isSelectionMode = false
        selectedFiles.clear()
        fileAdapter.selectedFiles = selectedFiles
        fileAdapter.isSelectionMode = false
        
        hideSelectionUI()
        disableSelectionMode()
        
        // Анимация исчезновения
        val slideOut = AnimationUtils.loadAnimation(buttonContainer.context, android.R.anim.slide_out_right)
        buttonContainer.startAnimation(slideOut)
        selectionToolbar.startAnimation(slideOut)
    }
    
    fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        
        fileAdapter.selectedFiles = selectedFiles
        val position = fileAdapter.files.indexOf(file)
        if (position != -1) {
            fileAdapter.notifyItemChanged(position)
        }
        
        updateSelectionButtons()
        
        if (selectedFiles.isEmpty()) {
            clearSelection()
        }
    }
    
    fun selectAllFiles() {
        selectedFiles.clear()
        selectedFiles.addAll(fileAdapter.files)
        fileAdapter.selectedFiles = selectedFiles
        fileAdapter.notifyDataSetChanged()
        updateSelectionButtons()
    }
    
    fun getSelectedFiles(): Set<File> = selectedFiles.toSet()
    
    fun isInSelectionMode(): Boolean = isSelectionMode
    
    private fun showSelectionUI() {
        buttonContainer.visibility = View.VISIBLE
        selectionToolbar.visibility = View.VISIBLE
        shareButton.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE
        moveButton.visibility = View.VISIBLE
    }
    
    private fun hideSelectionUI() {
        buttonContainer.visibility = View.GONE
        selectionToolbar.visibility = View.GONE
        shareButton.visibility = View.GONE
        deleteButton.visibility = View.GONE
        moveButton.visibility = View.GONE
    }
    
    private fun enableSelectionMode() {
        swipeRefreshLayout.isEnabled = false
    }
    
    private fun disableSelectionMode() {
        swipeRefreshLayout.isEnabled = true
    }
    
    private fun updateSelectionButtons() {
        val allSelected = selectedFiles.size == fileAdapter.files.size
        selectAllButton.text = if (allSelected) "Отменить все" else "Выделить все"
        clearSelectionButton.text = "Отменить (${selectedFiles.size})"
    }
} 