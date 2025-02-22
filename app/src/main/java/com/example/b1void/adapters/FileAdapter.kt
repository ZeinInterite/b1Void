package com.example.b1void.adapters;

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R
import java.io.File

class FileAdapter(
    private var files: List<File>,
    private val context: Context,
    private val onFileClickListener: (File) -> Unit,
    private val onFileLongClickListener: (File) -> Unit,
    private val onSelectionChanged: (File, Boolean) -> Unit,
    private val onShowOptionsClickListener: (File) -> Unit,
    private val selectedFiles: Set<File> // Pass selectedFiles from Activity
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {


    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkbox)
        val showOption: ImageView = itemView.findViewById(R.id.show_option)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]
        holder.fileName.text = file.name

        if (file.isDirectory) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
        } else if (isImage(file)) {
            val bitmap = loadImageIcon(file)
            bitmap?.let {
                holder.fileIcon.setImageBitmap(it)
            } ?: run {
                holder.fileIcon.setImageResource(R.drawable.image_ic)
            }
        } else {
            holder.fileIcon.setImageResource(R.drawable.file_ic)
        }

        val isSelectionMode = selectedFiles.isNotEmpty()
        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedFiles.contains(file)

        holder.itemView.setOnClickListener {
            onFileClickListener(file)
        }

        holder.itemView.setOnLongClickListener {
            onFileLongClickListener(file)
            true
        }

        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            onSelectionChanged(file, isChecked)
        }
        holder.showOption.setOnClickListener {
            onShowOptionsClickListener(file)
        }
    }

    fun isImage(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".gif") || fileName.endsWith(".bmp")
    }

    private fun loadImageIcon(file: File): android.graphics.Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getItemCount(): Int = files.size

    fun updateFiles(updatedFiles: List<File>) {
        files = updatedFiles
        notifyDataSetChanged()
    }

}
