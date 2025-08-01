package com.example.b1void.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import com.example.b1void.B1VoidApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageOptimizer {
    
    private const val TAG = "ImageOptimizer"
    
    /**
     * Оптимизирует изображение для слабых устройств
     */
    @WorkerThread
    suspend fun optimizeImage(
        context: Context,
        inputFile: File,
        outputFile: File,
        maxSize: Int = B1VoidApplication.MAX_IMAGE_SIZE,
        quality: Int = B1VoidApplication.IMAGE_COMPRESSION_QUALITY
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Проверяем доступную память
            if (!hasEnoughMemory(context)) {
                Log.w(TAG, "Недостаточно памяти для обработки изображения")
                return@withContext false
            }

            // Загружаем изображение с оптимизированными параметрами
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)

            // Вычисляем коэффициент масштабирования
            val scale = calculateInSampleSize(options, maxSize, maxSize)
            
            // Загружаем изображение с масштабированием
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.RGB_565 // Используем меньше памяти
                inPurgeable = true
                inInputShareable = true
            }

            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodeOptions)
                ?: return@withContext false

            // Сжимаем изображение
            val compressedBitmap = compressBitmap(bitmap, quality)
            
            // Сохраняем результат
            saveBitmapToFile(compressedBitmap, outputFile)
            
            // Очищаем память
            if (bitmap != compressedBitmap) {
                bitmap.recycle()
            }
            compressedBitmap.recycle()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка оптимизации изображения", e)
            false
        }
    }

    /**
     * Сжимает Bitmap с заданным качеством
     */
    private fun compressBitmap(bitmap: Bitmap, quality: Int): Bitmap {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        
        val bytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bitmap
    }

    /**
     * Вычисляет коэффициент масштабирования для загрузки изображения
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Сохраняет Bitmap в файл
     */
    private fun saveBitmapToFile(bitmap: Bitmap, file: File): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения изображения", e)
            false
        }
    }

    /**
     * Проверяет доступную память
     */
    private fun hasEnoughMemory(context: Context): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val availableMemory = maxMemory - usedMemory
        
        return availableMemory > B1VoidApplication.LOW_MEMORY_THRESHOLD
    }

    /**
     * Поворачивает изображение на заданный угол
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Изменяет размер изображения
     */
    fun resizeBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Очищает кэш изображений
     */
    fun clearImageCache(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val imageCacheDir = File(cacheDir, "glide_cache")
            if (imageCacheDir.exists()) {
                imageCacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка очистки кэша изображений", e)
        }
    }
} 