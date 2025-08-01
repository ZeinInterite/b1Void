package com.example.b1void.adapters

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.decode.DataSource
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation
import com.example.b1void.R
import com.example.b1void.activities.FileManagerActivity
import timber.log.Timber
import java.io.File

class FileAdapter(
    private val context: Context,
    private val onFileClickListener: (File) -> Unit
) : ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = getItem(position)
        holder.fileName.text = file.name

        when {
            file.isDirectory -> {
                holder.fileIcon.setImageResource(R.drawable.folder_ic)
            }
            isImage(file) -> {
                loadImageWithCoil(file, holder.fileIcon)
            }
            else -> {
                holder.fileIcon.setImageResource(R.drawable.file_ic)
            }
        }

        holder.itemView.setOnClickListener {
            onFileClickListener(file)
        }

        holder.itemView.setOnLongClickListener {
            showDeleteDialog(file)
            true
        }
    }

    private fun isImage(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".jpg") || 
               fileName.endsWith(".jpeg") || 
               fileName.endsWith(".png") || 
               fileName.endsWith(".gif") || 
               fileName.endsWith(".bmp") ||
               fileName.endsWith(".webp")
    }

    private fun loadImageWithCoil(file: File, imageView: ImageView) {
        val imageLoader = ImageLoader(context)
        
        val request = ImageRequest.Builder(context)
            .data(file)
            .target(imageView)
            .transformations(RoundedCornersTransformation(8f))
            .placeholder(R.drawable.image_ic)
            .error(R.drawable.image_ic)
            .fallback(R.drawable.image_ic)
            .size(200, 200) // Оптимизация размера
            .crossfade(true)
            .build()

        imageLoader.enqueue(request)
    }

    private fun showDeleteDialog(file: File) {
        AlertDialog.Builder(context)
            .setTitle(R.string.delete_title)
            .setMessage(context.getString(R.string.delete_message, file.name))
            .setPositiveButton(R.string.yes) { _, _ ->
                (context as? FileManagerActivity)?.deleteFileFromDropbox(file)
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.absolutePath == newItem.absolutePath
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.lastModified() == newItem.lastModified() &&
                   oldItem.length() == newItem.length()
        }
    }
}
