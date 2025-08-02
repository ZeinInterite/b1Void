package com.example.b1void.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.example.b1void.activities.ImagePreviewActivity
import java.io.File

class FullscreenImageManager(private val context: Context) {
    
    fun openImagePreview(imageFile: File) {
        val intent = Intent(context, ImagePreviewActivity::class.java)
        val imagePaths = arrayListOf(imageFile.absolutePath)
        intent.putStringArrayListExtra("image_paths", imagePaths)
        intent.putExtra("current_image_index", 0)
        intent.putExtra("show_action_buttons", true) // Флаг для показа кнопок действий
        context.startActivity(intent)
    }
    
    fun deleteImage(imageFile: File, onDeleteComplete: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Удалить изображение?")
            .setMessage("Вы уверены, что хотите удалить изображение ${imageFile.name}?")
            .setPositiveButton("Да") { _, _ ->
                try {
                    if (imageFile.delete()) {
                        Toast.makeText(context, "Изображение удалено", Toast.LENGTH_SHORT).show()
                        onDeleteComplete()
                    } else {
                        Toast.makeText(context, "Ошибка при удалении изображения", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка при удалении: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    fun shareImage(imageFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context, 
                "${context.packageName}.fileprovider", 
                imageFile
            )
            
            ShareCompat.IntentBuilder(context)
                .setStream(uri)
                .setType("image/*")
                .setChooserTitle("Поделиться изображением")
                .startChooser()
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, "Ошибка при обмене изображением", Toast.LENGTH_SHORT).show()
        }
    }
} 