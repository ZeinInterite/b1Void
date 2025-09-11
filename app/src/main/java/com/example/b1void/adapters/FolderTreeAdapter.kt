package com.example.b1void.adapters

import android.graphics.Color
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R
import com.example.b1void.databinding.ItemFolderTreeBinding
import com.example.b1void.models.FolderNode
import com.example.b1void.utils.dpToPx

class FolderTreeAdapter(
    private val currentSourcePath: String,
    private val onFolderClick: (FolderNode) -> Unit,
    private val onFolderSelect: (FolderNode) -> Unit
) : ListAdapter<FolderNode, FolderTreeAdapter.FolderViewHolder>(FolderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = ItemFolderTreeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FolderViewHolder(private val binding: ItemFolderTreeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(node: FolderNode) {
            binding.folderName.text = node.file.name

            // Отступ для имитации вложенности
            val indent = (node.level * 24).dpToPx(itemView.context) // Используем extension function
            binding.root.setPadding(indent, 0, 0, 0)

            // Иконка expand/collapse
            binding.expandIcon.rotation = if (node.isExpanded) 90f else 0f

            // Подсветка текущей папки (откуда перемещаем)
            if (node.file.absolutePath == currentSourcePath) {
                binding.folderName.setTextColor(Color.GRAY)
                binding.root.isClickable = false
            } else {
                binding.folderName.setTextColor(Color.BLACK) // Используем жестко заданный цвет
                binding.root.isClickable = true
            }

            // Подсветка выбранной для перемещения папки
            if (node.isSelected) {
                binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selection_highlight))
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
            }

            binding.root.setOnClickListener {
                if (node.file.absolutePath != currentSourcePath) {
                    onFolderSelect(node)
                }
            }

            binding.expandIcon.setOnClickListener {
                onFolderClick(node)
            }
        }

        
    }
}

class FolderDiffCallback : DiffUtil.ItemCallback<FolderNode>() {
    override fun areItemsTheSame(oldItem: FolderNode, newItem: FolderNode): Boolean {
        return oldItem.file.absolutePath == newItem.file.absolutePath
    }

    override fun areContentsTheSame(oldItem: FolderNode, newItem: FolderNode): Boolean {
        return oldItem == newItem
    }
}
