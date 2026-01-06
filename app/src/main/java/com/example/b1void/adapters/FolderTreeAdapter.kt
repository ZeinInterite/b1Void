package com.example.b1void.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.core.model.FolderNode

// Stubbed class to fix build
class FolderTreeAdapter(
    private val onFolderClicked: (FolderNode) -> Unit,
    private val onFolderLongClicked: (FolderNode) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        TODO("Not yet implemented")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }

    override fun getItemCount(): Int {
        return 0
    }
}