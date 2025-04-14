package com.example.b1void.activities
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.b1void.R

class NavigationApp : AppCompatActivity() {

    private val OPEN_FILE = 1

    private var imageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation_app)

        val takePhotoButton = findViewById<View>(R.id.takePhotoButton)
        val uploadButton = findViewById<View>(R.id.uploadButton)
        val createFolderButton = findViewById<View>(R.id.createFolderButton)

        takePhotoButton.setOnClickListener {
            val intent = Intent(
                this@NavigationApp,
                CameraV2Activity::class.java
            )
            startActivity(intent)
        }

        uploadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.setType("image/*")
            startActivityForResult(intent, OPEN_FILE)
        }

        createFolderButton.setOnClickListener {
            val intent = Intent(
                this@NavigationApp,
                FileManagerActivity::class.java
            )
            startActivity(intent)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OPEN_FILE && resultCode == Activity.RESULT_OK && data != null) {
            imageUri = data.data // Получаем URI выбранного изображения

            // Открываем SelectFolderActivity
            val intent = Intent(this, FileManagerActivity::class.java)
            intent.putExtra("imageUri", imageUri.toString()) // Передаем URI в другую Activity как String
            startActivity(intent)
        }
    }
//    private fun showCreateFolderDialog(parentDir: File) {
//        val builder = AlertDialog.Builder(this)
//        val input = EditText(this)
//        builder.setTitle("Создать новую папку")
//        builder.setView(input)
//
//        builder.setPositiveButton("Создать") { dialog, _ ->
//            val folderName = input.text.toString()
//            val newDir = File(parentDir, folderName)
//
//
//            if (newDir.mkdir()) {
//                loadDirectoryContent(parentDir)
//            } else {
//                Toast.makeText(this, "Ошибка при создании папки", Toast.LENGTH_SHORT).show()
//            }
//            dialog.dismiss()
//        }
//        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
//        builder.show()
//    }
}
