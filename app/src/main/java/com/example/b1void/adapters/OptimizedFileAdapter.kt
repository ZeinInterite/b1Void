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
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.b1void.R
import com.example.b1void.utils.MemoryManager
import java.io.File
import android.preference.PreferenceManager
import android.widget.ImageButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OptimizedFileAdapter(
    var files: List<File>,
    private val context: Context,
    private val onItemClickListener: (File) -> Unit,
    private val onItemLongClickListener: (File) -> Unit,
    var isSelectionMode: Boolean = false,
    var selectedFiles: Set<File> = emptySet(),
    private val onMoreOptionsClickListener: (File) -> Unit
) : RecyclerView.Adapter<OptimizedFileAdapter.FileViewHolder>() {

    private var currentProgress = 0
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val optimizedSettings = MemoryManager.getOptimizedSettings(context)
    private val isLowEndDevice = MemoryManager.isLowEndDevice(context)

    companion object {
        private const val PREF_SCALE_FACTOR = "scale_factor"
        private const val PAGE_SIZE = 20 // Пагинация для слабых устройств
    }

    fun setProgress(progress: Int) {
        currentProgress = progress
        val scaleFactor = 0.5f + (currentProgress / 100f) * 0.5f
        sharedPreferences.edit().putFloat(PREF_SCALE_FACTOR, scaleFactor).apply()
        notifyDataSetChanged()
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

        // Проверяем память перед загрузкой изображения
        if (MemoryManager.hasEnoughMemory(context, 10 * 1024 * 1024)) { // 10MB
            bindFileData(holder, file)
        } else {
            // Если мало памяти, показываем только иконки
            bindFileDataLowMemory(holder, file)
        }

        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedFiles.contains(file)

        holder.itemView.setOnClickListener { onItemClickListener(file) }
        holder.itemView.setOnLongClickListener {
            onItemLongClickListener(file)
            true
        }

        // Оптимизированное масштабирование
        applyOptimizedScaling(holder)

        val moreOptionsButton = holder.itemView.findViewById<ImageButton>(R.id.more_option_button)
        moreOptionsButton.visibility = View.VISIBLE
        moreOptionsButton.setOnClickListener {
            onMoreOptionsClickListener(file)
        }
    }

    private fun bindFileData(holder: FileViewHolder, file: File) {
        if (file.isDirectory) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
            holder.fileName.text = file.name
        } else if (isImage(file)) {
            // Оптимизированная загрузка изображений
            val imageSize = if (isLowEndDevice) 60 else 95
            
            Glide.with(context)
                .load(file)
                .apply(getOptimizedRequestOptions())
                .override(imageSize, imageSize)
                .centerCrop()
                .placeholder(R.drawable.image_ic)
                .error(R.drawable.image_ic)
                .into(holder.fileIcon)
            holder.fileName.text = ""
        } else {
            holder.fileIcon.setImageResource(R.drawable.file_ic)
            holder.fileName.text = ""
        }
    }

    private fun bindFileDataLowMemory(holder: FileViewHolder, file: File) {
        if (file.isDirectory) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
            holder.fileName.text = file.name
        } else if (isImage(file)) {
            holder.fileIcon.setImageResource(R.drawable.image_ic)
            holder.fileName.text = ""
        } else {
            holder.fileIcon.setImageResource(R.drawable.file_ic)
            holder.fileName.text = ""
        }
    }

    private fun getOptimizedRequestOptions(): RequestOptions {
        return RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .skipMemoryCache(false)
            .dontAnimate() // Отключаем анимации для экономии ресурсов
    }

    private fun applyOptimizedScaling(holder: FileViewHolder) {
        if (!holder.isOriginalSizeSaved) {
            holder.originalImageWidth = holder.fileIcon.layoutParams.width
            holder.originalImageHeight = holder.fileIcon.layoutParams.height
            holder.originalTextSize = holder.fileName.textSize
            holder.isOriginalSizeSaved = true
        }

        val scaleFactor = sharedPreferences.getFloat(PREF_SCALE_FACTOR, 0.75f)
        val optimizedScaleFactor = if (isLowEndDevice) scaleFactor * 0.8f else scaleFactor

        val imageParams = holder.fileIcon.layoutParams
        imageParams.width = (holder.originalImageWidth * optimizedScaleFactor).toInt()
        imageParams.height = (holder.originalImageHeight * optimizedScaleFactor).toInt()
        holder.fileIcon.layoutParams = imageParams

        holder.fileName.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.originalTextSize * optimizedScaleFactor)
    }

    override fun getItemCount(): Int = files.size

    fun updateFiles(updatedFiles: List<File>) {
        // Очищаем память перед обновлением
        MemoryManager.clearMemoryIfNeeded(context)
        
        val diffResult = DiffUtil.calculateDiff(OptimizedFileDiffCallback(this.files, updatedFiles))
        this.files = updatedFiles
        diffResult.dispatchUpdatesTo(this)
    }

    fun getPagedFiles(page: Int): List<File> {
        val startIndex = page * PAGE_SIZE
        val endIndex = minOf(startIndex + PAGE_SIZE, files.size)
        return if (startIndex < files.size) {
            files.subList(startIndex, endIndex)
        } else {
            emptyList()
        }
    }

    internal fun isImage(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || 
               fileName.endsWith(".png") || fileName.endsWith(".gif") || 
               fileName.endsWith(".bmp")
    }

    class OptimizedFileDiffCallback(
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