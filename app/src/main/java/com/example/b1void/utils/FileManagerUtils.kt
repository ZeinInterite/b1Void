package com.example.b1void.utils

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileManagerUtils {

    data class AppDirectories(
        val appDirectory: File,
        val zipDirectory: File,
        val trashDirectory: File
    )

    /**
     * Проверяет доступность external storage
     */
    fun isStorageAvailable(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Проверяем доступ к external files dir
                val dir = context.getExternalFilesDir(null)
                dir != null && (dir.exists() || dir.mkdirs())
            } else {
                @Suppress("DEPRECATION")
                val state = Environment.getExternalStorageState()
                state == Environment.MEDIA_MOUNTED
            }
        } catch (e: Exception) {
            Log.e("FileManagerUtils", "Error checking storage availability", e)
            false
        }
    }

    fun createAppDirectories(context: Context): AppDirectories {
        // Определяем правильное расположение в зависимости от версии Android
        val baseDirectory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (Scoped Storage)
            // Используем getExternalFilesDir для приватного хранилища с возможностью sharing
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: context.filesDir // Fallback на internal storage
        } else {
            // Android 9 и ниже - можем использовать public directory
            @Suppress("DEPRECATION")
            val publicDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
                publicDir
            } else {
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
            }
        }

        val appDirectory = File(baseDirectory, "InspectorAppFolder")
        val zipDirectory = File(baseDirectory, "zipFolder")
        val trashDirectory = File(appDirectory, "Trash")

        createDirectoryIfNotExists(appDirectory, "папка приложения", context)
        createDirectoryIfNotExists(zipDirectory, "папка zip-файлов", context)
        createDirectoryIfNotExists(trashDirectory, "Trash", context)

        Log.d("FileManagerUtils", "App directories created at: ${appDirectory.absolutePath}")

        return AppDirectories(appDirectory, zipDirectory, trashDirectory)
    }

    fun importUrisToDirectory(context: Context, directory: File, uris: List<Uri>): List<File> {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val resolver = context.contentResolver
        val savedFiles = mutableListOf<File>()

        uris.forEach { uri ->
            try {
                val mimeType = resolver.getType(uri)
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                    ?: run {
                        val path = uri.lastPathSegment ?: ""
                        val dotIndex = path.lastIndexOf('.')
                        if (dotIndex >= 0 && dotIndex < path.length - 1) {
                            path.substring(dotIndex + 1)
                        } else {
                            "jpg"
                        }
                    }
                val prefix = if (mimeType?.startsWith("video") == true) "Video" else "Image"
                val fileName = "$prefix-${System.currentTimeMillis()}.$extension"
                val targetFile = File(directory, fileName)

                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("Не удалось открыть поток: $uri")

                savedFiles.add(targetFile)
            } catch (e: Exception) {
                Log.e("FileManager", "Ошибка импорта $uri: ${e.message}", e)
            }
        }

        return savedFiles
    }

    fun importUrisToDirectoryModern(context: Context, directory: File, uris: List<Uri>): List<File> {
        // Проверяем доступность storage
        if (!isStorageAvailable(context)) {
            Log.e("FileManagerUtils", "Storage not available")
            Toast.makeText(context, "Хранилище недоступно", Toast.LENGTH_SHORT).show()
            return emptyList()
        }

        if (!directory.exists()) {
            val created = directory.mkdirs()
            if (!created) {
                Log.e("FileManagerUtils", "Failed to create directory: ${directory.absolutePath}")
                return emptyList()
            }
        }

        val resolver = context.contentResolver
        val savedFiles = mutableListOf<File>()

        uris.forEach { uri ->
            try {
                // Проверяем, что URI доступен
                val canRead = try {
                    resolver.openInputStream(uri)?.use { true } ?: false
                } catch (e: SecurityException) {
                    Log.e("FileManagerUtils", "No permission to read URI: $uri")
                    false
                }

                if (!canRead) {
                    Log.w("FileManagerUtils", "Skipping inaccessible URI: $uri")
                    return@forEach
                }

                val mimeType = resolver.getType(uri)

                val originalName: String? = try {
                    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                } catch (e: Exception) {
                    Log.w("FileManagerUtils", "Failed to query file name for $uri", e)
                    null
                }

                val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                val extFromPath = (uri.lastPathSegment ?: "").let { path ->
                    val i = path.lastIndexOf('.')
                    if (i >= 0 && i < path.length - 1) path.substring(i + 1) else null
                }

                val baseName = originalName?.takeIf { it.isNotBlank() } ?: run {
                    val ext = extFromMime ?: extFromPath ?: "bin"
                    "File-${System.currentTimeMillis()}.$ext"
                }

                var targetFile = File(directory, baseName)
                if (targetFile.exists()) {
                    val dot = baseName.lastIndexOf('.')
                    val nameOnly = if (dot > 0) baseName.substring(0, dot) else baseName
                    val ext = if (dot > 0) baseName.substring(dot) else ""
                    var idx = 1
                    while (targetFile.exists() && idx < 1000) { // Защита от бесконечного цикла
                        targetFile = File(directory, "$nameOnly ($idx)$ext")
                        idx++
                    }
                }

                // Копируем файл с обработкой ошибок
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output, bufferSize = 8192)
                    }
                } ?: throw IOException("Не удалось открыть поток: $uri")

                // Проверяем, что файл действительно создан и не пустой
                if (targetFile.exists() && targetFile.length() > 0) {
                    savedFiles.add(targetFile)
                    Log.d("FileManagerUtils", "Successfully imported: ${targetFile.name} (${targetFile.length()} bytes)")
                } else {
                    Log.w("FileManagerUtils", "File created but empty or missing: ${targetFile.name}")
                    targetFile.delete()
                }

            } catch (e: SecurityException) {
                Log.e("FileManagerUtils", "Security error importing $uri: ${e.message}", e)
                Toast.makeText(context, "Нет доступа к файлу", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("FileManagerUtils", "IO error importing $uri: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("FileManagerUtils", "Unexpected error importing $uri: ${e.message}", e)
            }
        }

        return savedFiles
    }

    private fun createDirectoryIfNotExists(directory: File, directoryName: String, context: Context) {
        if (!directory.exists()) {
            try {
                val created = directory.mkdirs()
                if (created) {
                    Log.d("FileManagerUtils", "$directoryName создана: ${directory.absolutePath}")

                    // Для Android 10+ создаем .nomedia файл чтобы медиасканер не индексировал
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val nomediaFile = File(directory, ".nomedia")
                            if (!nomediaFile.exists()) {
                                nomediaFile.createNewFile()
                            }
                        } catch (e: IOException) {
                            Log.w("FileManagerUtils", "Failed to create .nomedia file", e)
                        }
                    }
                } else {
                    Log.e("FileManagerUtils", "Не удалось создать $directoryName")
                    Toast.makeText(context, "Ошибка создания $directoryName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManagerUtils", "SecurityException creating directory: ${e.message}")
                Toast.makeText(
                    context,
                    "Недостаточно прав для создания $directoryName. Проверьте разрешения.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: IOException) {
                Log.e("FileManagerUtils", "IOException creating directory: ${e.message}")
                Toast.makeText(
                    context,
                    "Ошибка ввода/вывода при создании $directoryName",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun moveToTrash(target: File, trashDirectory: File): Boolean {
        return try {
            if (!trashDirectory.exists() && !trashDirectory.mkdirs()) {
                Log.e("FileManager", "Failed to create trash directory at ${trashDirectory.absolutePath}")
                return false
            }

            val trashPath = try {
                trashDirectory.canonicalPath
            } catch (e: IOException) {
                Log.w("FileManager", "Unable to resolve trash canonical path: ${e.message}")
                trashDirectory.absolutePath
            }

            val targetPath = try {
                target.canonicalPath
            } catch (e: IOException) {
                Log.w("FileManager", "Unable to resolve target canonical path: ${e.message}")
                target.absolutePath
            }

            if (targetPath == trashPath || targetPath.startsWith("$trashPath${File.separator}")) {
                return if (target.isDirectory) {
                    deleteDirectory(target)
                } else {
                    target.delete()
                }
            }

            var destination = File(trashDirectory, target.name)
            if (destination.exists()) {
                val baseName = if (target.isFile) target.nameWithoutExtension else target.name
                val extension = if (target.isFile && target.extension.isNotEmpty()) ".${target.extension}" else ""
                destination = File(trashDirectory, "${baseName}_${System.currentTimeMillis()}$extension")
            }

            if (target.renameTo(destination)) {
                true
            } else {
                if (target.isDirectory) {
                    val copied = target.copyRecursively(destination, overwrite = true)
                    copied && deleteDirectory(target)
                } else {
                    target.copyTo(destination, overwrite = true)
                    target.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("FileManager", "Failed to move ${target.absolutePath} to trash: ${e.message}", e)
            false
        }
    }

    fun clearTrash(trashDirectory: File): Boolean {
        return try {
            if (!trashDirectory.exists()) {
                val created = trashDirectory.mkdirs()
                if (!created) {
                    Log.e("FileManager", "Failed to recreate trash directory at ${trashDirectory.absolutePath}")
                }
                return created
            }

            var success = true
            val children = trashDirectory.listFiles() ?: return true
            for (child in children) {
                val deleted = if (child.isDirectory) {
                    deleteDirectory(child)
                } else {
                    child.delete()
                }
                if (!deleted) {
                    Log.e("FileManager", "Failed to remove ${child.absolutePath} from trash")
                    success = false
                }
            }
            success
        } catch (e: Exception) {
            Log.e("FileManager", "Failed to clear trash: ${e.message}", e)
            false
        }
    }

    fun deleteDirectory(directory: File): Boolean {
        val files = directory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    if (!file.delete()) {
                        Log.e("FileManager", "Failed to delete file: " + file.absolutePath)
                        return false
                    }
                }
            }
        }
        return directory.delete()
    }
    
    fun isImageFile(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".jpg") || 
               fileName.endsWith(".jpeg") || 
               fileName.endsWith(".png") || 
               fileName.endsWith(".gif") || 
               fileName.endsWith(".bmp")
    }

    fun isVideoFile(file: File): Boolean {
        val fileName = file.name.lowercase()
        return fileName.endsWith(".mp4") ||
               fileName.endsWith(".mov") ||
               fileName.endsWith(".mkv") ||
               fileName.endsWith(".avi") ||
               fileName.endsWith(".3gp") ||
               fileName.endsWith(".webm")
    }
    
    fun getSortedFiles(directory: File, ascending: Boolean): List<File> {
        val files = directory.listFiles()?.toList() ?: emptyList()
        return if (ascending) {
            files.sortedBy { getCreationTimeMillis(it) }
        } else {
            files.sortedByDescending { getCreationTimeMillis(it) }
        }
    }

    fun getCreationTimeMillis(file: File): Long {
        // Prefer EXIF original date for images; otherwise fallback to file timestamps
        if (file.isFile && isImageFile(file)) {
            try {
                // EXIF DateTimeOriginal or DateTime fallback
                val exif = androidx.exifinterface.media.ExifInterface(file.absolutePath)
                val dateStr = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME)
                if (!dateStr.isNullOrBlank()) {
                    // EXIF format: "yyyy:MM:dd HH:mm:ss"
                    val parts = dateStr.trim()
                    val sdf = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    val date = sdf.parse(parts)
                    if (date != null) return date.time
                }
            } catch (_: Exception) {
                // Ignore and fallback
            }
        }

        if (file.isDirectory) {
            // For directories, approximate creation as the earliest timestamp among the
            // directory itself and its immediate children (cheap heuristic).
            var best = file.lastModified()
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    val t = if (child.isFile && isImageFile(child)) getCreationTimeMillis(child) else child.lastModified()
                    if (t > 0 && t < best) best = t
                }
            }
            return if (best > 0) best else file.lastModified()
        }

        // Fallback for non-image files
        return file.lastModified()
    }

    fun zipDirectory(directory: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            val children = directory.listFiles()
            children?.forEach { childFile ->
                addFileToZip(childFile, childFile.name, zipOut)
            }
        }
    }

    private fun addFileToZip(fileToZip: File, fileName: String, zipOut: ZipOutputStream) {
        // Пропускаем скрытые файлы (.nomedia и другие)
        if (fileToZip.isHidden || fileToZip.name.startsWith(".")) {
            return
        }
        if (fileToZip.isDirectory) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(ZipEntry(fileName))
                zipOut.closeEntry()
            } else {
                zipOut.putNextEntry(ZipEntry("$fileName/"))
                zipOut.closeEntry()
            }
            val children = fileToZip.listFiles()
            children?.let {
                for (childFile in it) {
                    addFileToZip(childFile, "$fileName/${childFile.name}", zipOut)
                }
            }
            return
        }
        BufferedInputStream(FileInputStream(fileToZip)).use { bis ->
            val zipEntry = ZipEntry(fileName).apply {
                time = fileToZip.lastModified()
                method = ZipEntry.DEFLATED
            }
            zipOut.putNextEntry(zipEntry)
            val bytes = ByteArray(8192)
            var length: Int
            while (bis.read(bytes).also { length = it } >= 0) {
                zipOut.write(bytes, 0, length)
            }
            zipOut.closeEntry()
        }
    }
} 
