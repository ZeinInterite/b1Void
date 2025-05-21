
package com.example.b1void.adapters

import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R
import java.io.File
import android.preference.PreferenceManager

class FileAdapter(
    var files: List<File>,
    private val context: Context,
    private val onItemClickListener: (File) -> Unit,
    private val onItemLongClickListener: (File) -> Unit,
    var isSelectionMode: Boolean = false,
    var selectedFiles: Set<File> = emptySet()
) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    private var currentProgress = 0
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    companion object {
        private const val PREF_SCALE_FACTOR = "scale_factor"
    }

    // Добавьте функцию для обновления прогресса
    fun setProgress(progress: Int) {
        currentProgress = progress
        // Сохранение scaleFactor в SharedPreferences
        val scaleFactor = 0.5f + (currentProgress / 100f) * 0.5f
        sharedPreferences.edit().putFloat(PREF_SCALE_FACTOR, scaleFactor).apply()
        notifyDataSetChanged()
    }

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileName: TextView = itemView.findViewById(R.id.file_name)
        val fileIcon: ImageView = itemView.findViewById(R.id.file_icon)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkbox)

        // Сохраняем оригинальные размеры
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

        // Устанавливаем иконку файла
        if (file.isDirectory) {
            holder.fileIcon.setImageResource(R.drawable.ic_folder)
            holder.fileName.text = file.name // Отображаем имя только для папок
        } else if (isImage(file)) {
            val bitmap = loadImageIcon(file)
            bitmap?.let {
                holder.fileIcon.setImageBitmap(it)
            } ?: run {
                holder.fileIcon.setImageResource(R.drawable.image_ic)
            }
            holder.fileName.text = "" // Не отображаем имя для изображений
        } else {
            holder.fileIcon.setImageResource(R.drawable.file_ic)
            holder.fileName.text = "" // Не отображаем имя для других файлов
        }

        // Устанавливаем видимость CheckBox в зависимости от режима выделения
        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        holder.checkBox.isChecked = selectedFiles.contains(file)

        // Обработчики кликов
        holder.itemView.setOnClickListener {
            onItemClickListener(file)
        }

        holder.itemView.setOnLongClickListener {
            onItemLongClickListener(file)
            true
        }

        // Логика масштабирования
        if (!holder.isOriginalSizeSaved) {
            holder.originalImageWidth = holder.fileIcon.layoutParams.width
            holder.originalImageHeight = holder.fileIcon.layoutParams.height
            holder.originalTextSize = holder.fileName.textSize
            holder.isOriginalSizeSaved = true
        }

        // 2. Вычисляем scaleFactor на основе прогресса (от 0.5 до 1.0)
        val scaleFactor = sharedPreferences.getFloat(PREF_SCALE_FACTOR, 0.75f)
        currentProgress = ((scaleFactor - 0.5f) / 0.5f * 100).toInt()

        // 3. Изменяем размеры ImageView
        val imageParams = holder.fileIcon.layoutParams
        imageParams.width = (holder.originalImageWidth * scaleFactor).toInt()
        imageParams.height = (holder.originalImageHeight * scaleFactor).toInt()
        holder.fileIcon.layoutParams = imageParams

        // 4. Изменяем размер текста TextView
        holder.fileName.setTextSize(TypedValue.COMPLEX_UNIT_PX, holder.originalTextSize * scaleFactor)

    }

    // Проверка, является ли файл изображением
    internal fun isImage(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png") || fileName.endsWith(".gif") || fileName.endsWith(".bmp")
    }

    // Загрузка иконки изображения
    private fun loadImageIcon(file: File): android.graphics.Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getItemCount(): Int = files.size

    // Функция для обновления списка файлов
    fun updateFiles(updatedFiles: List<File>) {
        files = updatedFiles
        notifyDataSetChanged()
    }
}