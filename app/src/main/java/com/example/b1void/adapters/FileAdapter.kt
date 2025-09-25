package com.example.b1void.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.b1void.R
import java.io.File

class FileAdapter(
    var files: List<File>,
    private val context: Context,
    private val onItemClickListener: (File) -> Unit,
    private val onShowContextMenu: (File, View) -> Unit,
    var isSelectionMode: Boolean = false,
    var selectedFiles: MutableSet<File> = mutableSetOf()
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        val playIcon: ImageView = itemView.findViewById(R.id.play_icon)
        val selectionOverlay: View = itemView.findViewById(R.id.selection_overlay)
        val selectionBadge: TextView = itemView.findViewById(R.id.selection_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]

        holder.fileIcon.post {
            val layoutParams = holder.fileIcon.layoutParams
            layoutParams.height = holder.fileIcon.width
            holder.fileIcon.layoutParams = layoutParams
        }

        holder.playIcon.visibility = View.GONE
        holder.fileName.visibility = View.VISIBLE

        when {
            file.isDirectory -> {
                holder.fileIcon.setImageResource(R.drawable.ic_folder)
                holder.fileName.text = file.name
            }
            isImage(file) -> {
                Glide.with(context)
                    .load(file)
                    .centerCrop()
                    .placeholder(R.drawable.image_ic)
                    .error(R.drawable.image_ic)
                    .into(holder.fileIcon)
                holder.fileName.visibility = View.GONE
            }
            isVideo(file) -> {
                Glide.with(context)
                    .load(file)
                    .centerCrop()
                    .placeholder(R.drawable.ic_videocam)
                    .error(R.drawable.ic_videocam)
                    .into(holder.fileIcon)
                holder.playIcon.visibility = View.VISIBLE
                holder.fileName.visibility = View.GONE
            }
            else -> {
                holder.fileIcon.setImageResource(R.drawable.file_ic)
                holder.fileName.text = file.name
            }
        }

        updateSelectionState(holder, file)

        holder.itemView.setOnClickListener { onItemClickListener(file) }
        holder.itemView.setOnLongClickListener {
            onShowContextMenu(file, holder.itemView)
            true
        }
    }

    private fun updateSelectionState(holder: FileViewHolder, file: File) {
        if (isSelectionMode) {
            val isSelected = selectedFiles.contains(file)
            holder.selectionOverlay.isVisible = isSelected
            holder.selectionBadge.isVisible = isSelected
            holder.itemView.alpha = if (isSelected) 1f else 0.75f

            if (isSelected) {
                val order = selectionIndexOf(file)
                holder.selectionBadge.text = if (order >= 0) (order + 1).toString() else ""
            } else {
                holder.selectionBadge.text = ""
            }
        } else {
            holder.selectionOverlay.isVisible = false
            holder.selectionBadge.isVisible = false
            holder.selectionBadge.text = ""
            holder.itemView.alpha = 1f
        }
    }

    private fun selectionIndexOf(file: File): Int {
        var index = 0
        for (selected in selectedFiles) {
            if (selected == file) {
                return index
            }
            index++
        }
        return -1
    }

    override fun getItemCount(): Int = files.size

    fun updateFiles(updatedFiles: List<File>) {
        val diffResult = DiffUtil.calculateDiff(FileDiffCallback(this.files, updatedFiles))
        this.files = updatedFiles
        diffResult.dispatchUpdatesTo(this)
    }

    fun notifySelectionChanged(previousSelection: Set<File>, currentSelection: Set<File>) {
        val impacted = mutableSetOf<File>()
        impacted.addAll(previousSelection)
        impacted.addAll(currentSelection)
        impacted.forEach { file ->
            val index = files.indexOf(file)
            if (index != -1) {
                notifyItemChanged(index)
            }
        }
    }

    fun isImage(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".gif") || fileName.endsWith(".bmp")
    }

    fun isVideo(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".mp4") || fileName.endsWith(".mov") || fileName.endsWith(".avi")
    }

    class FileDiffCallback(
        private val oldList: List<File>,
        private val newList: List<File>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].absolutePath == newList[newItemPosition].absolutePath
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldFile = oldList[oldItemPosition]
            val newFile = newList[newItemPosition]
            return oldFile.lastModified() == newFile.lastModified() && oldFile.name == newFile.name
        }
    }
}
