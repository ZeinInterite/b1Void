package com.example.b1void.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.IOException

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileManagerUtils {
    
    fun createAppDirectories(context: Context): Pair<File, File> {
        val filesDir = context.filesDir
        val appDirectory = File(filesDir, "InspectorAppFolder")
        val zipDirectory = File(filesDir, "zipFolder")
        
        createDirectoryIfNotExists(appDirectory, "папка приложения", context)
        createDirectoryIfNotExists(zipDirectory, "папка zip-файлов", context)
        
        return Pair(appDirectory, zipDirectory)
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
    
    fun getSortedFiles(directory: File, ascending: Boolean): List<File> {
        val files = directory.listFiles()?.toList() ?: emptyList()
        return if (ascending) {
            files.sortedBy { it.lastModified() }
        } else {
            files.sortedByDescending { it.lastModified() }
        }
    }

    fun zipDirectory(directory: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            addFileToZip(directory, directory.name, zipOut)
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