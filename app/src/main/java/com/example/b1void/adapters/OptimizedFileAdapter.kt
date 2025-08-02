package com.example.b1void.adapters

import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.b1void.R
import java.io.File
import android.preference.PreferenceManager
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener

class OptimizedFileAdapter(
    var files: List<File>,
    private val context: Context,
    private val onItemClickListener: (File) -> Unit,
    private val onItemLongClickListener: (File) -> Unit,
    private val onSwipeSelectionListener: (File) -> Unit,
    var isSelectionMode: Boolean = false,
    var selectedFiles: Set<File> = emptySet()
) : RecyclerView.Adapter<OptimizedFileAdapter.FileViewHolder>() {

    private var currentProgress = 0
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private var isSwipeSelectionActive = false
    private var lastTouchedPosition = -1

    companion object {
        private const val PREF_SCALE_FACTOR = "scale_factor"
    }

    fun setProgress(progress: Int) {
        currentProgress = progress
        val scaleFactor = 0.5f + (currentProgress / 100f) * 0.5f
        sharedPreferences.edit().putFloat(PREF_SCALE_FACTOR, scaleFactor).apply()
        notifyDataSetChanged()
    }

    fun startSwipeSelection() {
        isSwipeSelectionActive = true
        lastTouchedPosition = -1
    }

    fun stopSwipeSelection() {
        isSwipeSelectionActive = false
        lastTouchedPosition = -1
    }

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkbox)

        var originalImageWidth: Int = 85
        var originalImageHeight: Int = 85
        var originalTextSize: Float = 0f
        var isOriginalSizeSaved = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = files[position]

        if (file.isDirectory) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
            holder.fileName.text = file.name
        } else if (isImage(file)) {
            Glide.with(context)
                .load(file)
                .override(95, 95)
                .centerCrop()
                .placeholder(R.drawable.image_ic)
                .error(R.drawable.image_ic)
                .into(holder.fileIcon)
            holder.fileName.text = ""
        } else {
            holder.fileIcon.setImageResource(R.drawable.file_ic)
            holder.fileName.text = ""
        }

        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedFiles.contains(file)

        // Улучшенная визуализация выделения
        if (isSelectionMode) {
            holder.itemView.alpha = if (selectedFiles.contains(file)) 0.7f else 1.0f
            holder.itemView.setBackgroundResource(
                if (selectedFiles.contains(file)) R.drawable.selected_item_background 
                else android.R.color.transparent
            )
        } else {
            holder.itemView.alpha = 1.0f
            holder.itemView.setBackgroundResource(android.R.color.transparent)
        }

        // Настройка размеров
        if (!holder.isOriginalSizeSaved) {
            holder.originalImageWidth = holder.fileIcon.layoutParams.width
            holder.originalImageHeight = holder.fileIcon.layoutParams.height
            holder.originalTextSize = holder.fileName.textSize
            holder.isOriginalSizeSaved = true
        }

        val scaleFactor = sharedPreferences.getFloat(PREF_SCALE_FACTOR, 0.75f)

        val imageParams = holder.fileIcon.layoutParams
        imageParams.width = (holder.originalImageWidth * scaleFactor).toInt()
        imageParams.height = (holder.originalImageHeight * scaleFactor).toInt()
        holder.fileIcon.layoutParams = imageParams

        holder.fileName.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.originalTextSize * scaleFactor)

        // Настройка обработчиков событий
        setupTouchHandlers(holder, file, position)
    }

    private fun setupTouchHandlers(holder: FileViewHolder, file: File, position: Int) {
        val gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                onItemClickListener(file)
                return true
            }

            override fun onLongPress(e: MotionEvent?) {
                onItemLongClickListener(file)
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isSwipeSelectionActive && e2 != null) {
                    if (position != lastTouchedPosition) {
                        onSwipeSelectionListener(file)
                        lastTouchedPosition = position
                    }
                    return true
                }
                return false
            }
        })

        holder.itemView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    override fun getItemCount(): Int = files.size

    fun updateFiles(updatedFiles: List<File>) {
        val diffResult = DiffUtil.calculateDiff(FileDiffCallback(this.files, updatedFiles))
        this.files = updatedFiles
        diffResult.dispatchUpdatesTo(this)
    }

    internal fun isImage(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".gif") || fileName.endsWith(".bmp")
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