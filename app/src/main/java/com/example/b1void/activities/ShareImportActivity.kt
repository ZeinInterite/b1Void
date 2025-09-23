package com.example.b1void.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.b1void.R
import com.example.b1void.utils.FileManagerUtils
import com.example.b1void.utils.ImageOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ShareImportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_import)
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableStream())
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableStreamList()
            else -> null
        }

        if (uris.isNullOrEmpty()) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            importUris(uris.filterNotNull())
        }
    }

    private suspend fun importUris(uris: List<Uri>) = withContext(Dispatchers.IO) {
        val directories = FileManagerUtils.createAppDirectories(this@ShareImportActivity)
        val targetDirectory = directories.appDirectory
        val savedFiles = FileManagerUtils.importUrisToDirectory(this@ShareImportActivity, targetDirectory, uris)
        ImageOptimizer.clearImageCache(this@ShareImportActivity)

        withContext(Dispatchers.Main) {
            if (savedFiles.isEmpty()) {
                Toast.makeText(this@ShareImportActivity, R.string.import_failed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this@ShareImportActivity,
                    getString(R.string.import_complete),
                    Toast.LENGTH_SHORT
                ).show()
            }
            openFileManager(targetDirectory)
            finish()
        }
    }

    private fun openFileManager(targetDirectory: File) {
        val intent = Intent(this, FileManagerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_DIRECTORY, targetDirectory.absolutePath)
        }
        startActivity(intent)
    }

    companion object {
        const val EXTRA_TARGET_DIRECTORY = "extra_target_directory"
    }

    private fun Intent.getParcelableStream(): Uri? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun Intent.getParcelableStreamList(): List<Uri>? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.toList()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList()
        }
    }
}
