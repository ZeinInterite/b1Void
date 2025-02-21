
package com.example.b1void.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.b1void.R
import com.example.b1void.adapters.FileAdapter
import java.io.File
import java.util.LinkedList

class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var createFolderButton: Button
    private lateinit var fileAdapter: FileAdapter
    private val directoryStack: LinkedList<File> = LinkedList()
    private lateinit var appDirectory: File
    private lateinit var addInspBtn: Button
    private lateinit var showInspBtn: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        addInspBtn = findViewById(R.id.add_inspection_button)
        showInspBtn = findViewById(R.id.show_inspection_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

        addInspBtn.setOnClickListener {
            val intent = Intent(this, InspectionAddActivity::class.java)
            intent.putExtra("current_directory", getCurrentDirectory().absolutePath)
            startActivity(intent)
        }
        showInspBtn.setOnClickListener {
            val intent = Intent(this, WorkerActivity::class.java)
            startActivity(intent)
        }

        val gridLayoutManager = GridLayoutManager(this, 4)
        recyclerView.layoutManager = gridLayoutManager

        appDirectory = getExternalFilesDir(null)?.let { File(it, "InspectorAppFolder") }
            ?: File(filesDir, "InspectorAppFolder")

        if (!appDirectory.exists()) {
            if (appDirectory.mkdirs()) {
                Toast.makeText(this, "Папка приложения создана", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Не удалось создать папку приложения", Toast.LENGTH_SHORT).show()
            }
        }


        loadDirectoryContent(appDirectory)

        createFolderButton.setOnClickListener {
            showCreateFolderDialog(getCurrentDirectory())
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadDirectoryContent(getCurrentDirectory())
        }
    }


    private fun getCurrentDirectory(): File {
        return directoryStack.lastOrNull() ?: appDirectory
    }

    private fun loadDirectoryContent(directory: File) {
        swipeRefreshLayout.isRefreshing = true
        if (directory.exists() && directory.isDirectory) {
            val filesAndDirs = directory.listFiles()?.toList() ?: emptyList()


            if (!this::fileAdapter.isInitialized) {
                fileAdapter = FileAdapter(filesAndDirs, this) { file ->
                    if (file.isDirectory) {
                        openDirectory(file)
                    } else {
                        // Check if the file is an image, and open preview if so.
                        if (fileAdapter.isImage(file)) {
                            openImagePreview(file)
                        } else {
                            Toast.makeText(this, "Выбран файл: ${file.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                recyclerView.adapter = fileAdapter
            } else {
                fileAdapter.updateFiles(filesAndDirs)
            }
            title = directory.name
        } else {
            Toast.makeText(this, "Папка не найдена", Toast.LENGTH_SHORT).show()
        }
        swipeRefreshLayout.isRefreshing = false
    }

    // Method to open the image preview activity
    private fun openImagePreview(imageFile: File) {
        val intent = Intent(this, ImagePreviewActivity::class.java)
        intent.putExtra("image_path", imageFile.absolutePath)
        startActivity(intent)
    }


    private fun openDirectory(file: File) {
        if (directoryStack.isEmpty() || directoryStack.lastOrNull() != file) {
            directoryStack.add(file)
        }
        loadDirectoryContent(file)
    }

    override fun onBackPressed() {
        if (directoryStack.isNotEmpty()) {
            directoryStack.removeLast()
            val previousDirectory = directoryStack.lastOrNull() ?: appDirectory
            loadDirectoryContent(previousDirectory)
        } else {
            super.onBackPressed()
        }
    }

    private fun showCreateFolderDialog(parentDir: File) {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        builder.setTitle("Создать новую папку")
        builder.setView(input)

        builder.setPositiveButton("Создать") { dialog, _ ->
            val folderName = input.text.toString()
            val newDir = File(parentDir, folderName)


            if (newDir.mkdir()) {
                loadDirectoryContent(parentDir)
            } else {
                Toast.makeText(this, "Ошибка при создании папки", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }


    fun deleteFile(file: File) {
        if (file.delete()) {
            Log.d("File Manager", "File ${file.name} deleted successfully")
            loadDirectoryContent(getCurrentDirectory())
        } else {
            Log.e("File Manager", "Error deleting file ${file.name}")
            Toast.makeText(
                this@FileManagerActivity,
                "Ошибка при удалении файла",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

}