package com.example.b1void.utils

import android.content.Context
import android.net.Uri
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

    
    fun createAppDirectories(context: Context): AppDirectories {
        val filesDir = context.filesDir
        val appDirectory = File(filesDir, "InspectorAppFolder")
        val zipDirectory = File(filesDir, "zipFolder")
        val trashDirectory = File(appDirectory, "Trash")
        
        createDirectoryIfNotExists(appDirectory, "папка приложения", context)
        createDirectoryIfNotExists(zipDirectory, "папка zip-файлов", context)
        createDirectoryIfNotExists(trashDirectory, "Trash", context)
        
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

    private fun createDirectoryIfNotExists(directory: File, directoryName: String, context: Context) {
        if (!directory.exists()) {
            try {
                if (directory.mkdirs()) {
                    Toast.makeText(context, "$directoryName создана", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Не удалось создать $directoryName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating directory: ${e.message}")
                Toast.makeText(context, "Ошибка: Недостаточно прав для создания $directoryName", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating directory: ${e.message}")
                Toast.makeText(context, "Ошибка ввода/вывода при создании $directoryName", Toast.LENGTH_SHORT).show()
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
        if (fileToZip.isHidden) {
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
        FileInputStream(fileToZip).use { fis ->
            val zipEntry = ZipEntry(fileName)
            zipOut.putNextEntry(zipEntry)
            val bytes = ByteArray(1024)
            var length: Int
            while (fis.read(bytes).also { length = it } >= 0) {
                zipOut.write(bytes, 0, length)
            }
        }
    }
} 
