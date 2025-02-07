
package com.example.b1void.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.dropbox.core.DbxException
import com.dropbox.core.InvalidAccessTokenException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.Metadata
import com.example.b1void.DBX.DropboxClient
import com.example.b1void.R
import com.example.b1void.tasks.CreateFolderTask
import com.example.b1void.tasks.ListFolderTask
import java.io.File

class ShowInspectionActivity : AppCompatActivity() {

    private lateinit var tvInspectorName: TextView
    private lateinit var tvInspectorCode: TextView
    private lateinit var ivInspectorPhoto: ImageView
    private var dropboxClient: DbxClientV2? = null
    private var accessToken: String? = null
    private var inspectorDropboxPath = ""
    private var inspectorName: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_inspection)

        tvInspectorName = findViewById(R.id.show_insp_name)
        tvInspectorCode = findViewById(R.id.show_insp_code)
        ivInspectorPhoto = findViewById(R.id.show_insp_photo)

        val intent = intent
        val inspectorId = intent.getStringExtra("inspectorId")
        inspectorName = intent.getStringExtra("inspectorName")
        val inspectorCode = intent.getStringExtra("inspectorCode")
        val inspectorPhoto = intent.getStringExtra("inspectorPhoto")

        tvInspectorName.text = inspectorName
        tvInspectorCode.text = inspectorCode

        if (inspectorPhoto != null) {
            Glide.with(this)
                .load(File(inspectorPhoto))
                .placeholder(R.drawable.def_insp_img)
                .error(R.drawable.def_insp_img)
                .into(ivInspectorPhoto)
        } else {
            ivInspectorPhoto.setImageResource(R.drawable.def_insp_img)
        }

        accessToken = retrieveAccessToken()
        if (accessToken == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        dropboxClient = DropboxClient.getClient(accessToken!!)
        inspectorName?.let { searchDropboxForInspectorFolder(it) }
    }

    private fun searchDropboxForInspectorFolder(inspectorName: String) {
        val rootPath = "" // Поиск в корневой директории Dropbox
        dropboxClient?.let { client ->
            ListFolderTask(client, object : ListFolderTask.TaskDelegate {
                override fun onFilesReceived(files: List<Metadata>) {
                    val folder = files.find { it.name == inspectorName }

                    if (folder != null) {
                        inspectorDropboxPath = folder.pathDisplay!!
                        Log.d("Dropbox", "Folder for $inspectorName found at $inspectorDropboxPath")
                        Toast.makeText(this@ShowInspectionActivity, "Папка инспектора найдена", Toast.LENGTH_SHORT).show()
                        // Дальнейшие действия, например, переход к файлам внутри папки
                        // Можно передать inspectorDropboxPath в другое активити для отображения контента
                    } else {
                        Log.d("Dropbox", "Folder for $inspectorName not found, creating...")
                        createDropboxFolder("/$inspectorName")
                    }

                }

                override fun onError(error: DbxException) {
                    if(error is InvalidAccessTokenException){
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                        redirectToLogin()
                    } else {
                        Log.e("Dropbox", "Error searching for inspector's folder", error)
                        Toast.makeText(
                            this@ShowInspectionActivity,
                            "Ошибка при поиске папки инспектора",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }, rootPath).execute()
        }
    }
    private fun createDropboxFolder(path: String) {
        dropboxClient?.let { client ->
            CreateFolderTask(client, object : CreateFolderTask.TaskDelegate {
                override fun onFolderCreated() {
                    Log.d("Dropbox", "Folder $path created successfully")
                    Toast.makeText(this@ShowInspectionActivity, "Папка инспектора создана", Toast.LENGTH_SHORT).show()
                    inspectorName?.let {
                        searchDropboxForInspectorFolder(it)
                    }

                }

                override fun onError(error: DbxException) {
                    if (error is CreateFolderErrorException) {
                        Log.d("Dropbox", "Folder $path already exists.")
                        inspectorName?.let {
                            searchDropboxForInspectorFolder(it)
                        }

                    }  else if (error is InvalidAccessTokenException){
                        Log.e("Dropbox", "Invalid access token: Redirecting to Login", error)
                        redirectToLogin()
                    } else {
                        Log.e("Dropbox", "Error creating folder", error)
                        Toast.makeText(
                            this@ShowInspectionActivity,
                            "Ошибка при создании папки в Dropbox",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }, path).execute()
        }
    }

    private fun retrieveAccessToken(): String? {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        return prefs.getString("access-token", null)
    }

    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
