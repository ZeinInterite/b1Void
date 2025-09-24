package com.example.b1void.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R
import com.example.b1void.databinding.ItemFolderTreeBinding
import com.example.b1void.models.FolderNode
import com.example.b1void.utils.dpToPx

class FolderTreeAdapter(
    private val currentSourcePath: String,
    private val rootFolderPath: String,
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

        private val baseStartPadding = ViewCompat.getPaddingStart(binding.root)
        private val baseTopPadding = binding.root.paddingTop
        private val baseEndPadding = ViewCompat.getPaddingEnd(binding.root)
        private val baseBottomPadding = binding.root.paddingBottom

        fun bind(node: FolderNode) {
            val context = itemView.context

            binding.folderName.text = if (node.file.absolutePath == rootFolderPath) {
                "Основная директория"
            } else {
                node.file.name
            }

            val indent = (node.level * 24).dpToPx(context)
            ViewCompat.setPaddingRelative(
                binding.root,
                baseStartPadding + indent,
                baseTopPadding,
                baseEndPadding,
                baseBottomPadding
            )

            binding.expandIcon.rotation = if (node.isExpanded) 90f else 0f

            val isSourceFolder = node.file.absolutePath == currentSourcePath
            val defaultTextColor = ContextCompat.getColor(context, R.color.move_folder_item_text)
            val disabledTextColor = ContextCompat.getColor(context, R.color.gray)
            val selectedTextColor = ContextCompat.getColor(context, R.color.delete_red)
            val defaultBackgroundColor = ContextCompat.getColor(context, R.color.move_folder_item_background)
            val selectedBackgroundColor = ContextCompat.getColor(context, R.color.move_folder_item_selected_background)

            binding.folderName.setTextColor(
                when {
                    isSourceFolder -> disabledTextColor
                    node.isSelected -> selectedTextColor
                    else -> defaultTextColor
                }
            )

            binding.root.setBackgroundColor(
                if (node.isSelected) selectedBackgroundColor else defaultBackgroundColor
            )
            binding.root.isClickable = !isSourceFolder

            binding.root.setOnClickListener {
                if (!isSourceFolder) {
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
