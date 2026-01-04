
package com.example.b1void.activities

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.b1void.R
import com.example.b1void.adapters.ImagePagerAdapter
import com.example.b1void.ui.MoveFilesBottomSheet
import com.example.b1void.utils.FileManagerUtils
import com.example.b1void.utils.ImageOptimizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var buttonsLayout: LinearLayout
    private lateinit var deleteButton: ImageButton
    private lateinit var moveButton: ImageButton
    private lateinit var shareButton: ImageButton
    private lateinit var resolutionBadge: TextView

    private lateinit var imagePaths: MutableList<String>
    private var currentImageIndex: Int = 0
    private lateinit var pagerAdapter: ImagePagerAdapter

    private lateinit var appDirectory: File
    private lateinit var trashDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        viewPager = findViewById(R.id.view_pager)
        buttonsLayout = findViewById(R.id.buttons_layout)
        deleteButton = findViewById(R.id.delete_button)
        moveButton = findViewById(R.id.move_button)
        shareButton = findViewById(R.id.share_button)
        resolutionBadge = findViewById(R.id.resolution_badge)

        imagePaths = intent.getStringArrayListExtra("image_paths")?.toMutableList() ?: mutableListOf()
        currentImageIndex = intent.getIntExtra("current_image_index", 0)

        if (imagePaths.isEmpty()) {
            Toast.makeText(this, R.string.no_images_to_display, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupDirectories()
        setupViewPager()
        setupButtonListeners()
        setupMoveResultListener()
        updateResolutionBadge(imagePaths[currentImageIndex])
    }

    private fun setupViewPager() {
        pagerAdapter = ImagePagerAdapter(this, imagePaths)
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(currentImageIndex, false)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentImageIndex = position
                updateResolutionBadge(imagePaths[position])
            }
        })
    }

    private fun updateResolutionBadge(imagePath: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(imagePath)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val width = options.outWidth
                val height = options.outHeight
                val sizeInMB = file.length() / (1024.0 * 1024.0)
                val decimalFormat = DecimalFormat("#.##")
                val formattedSize = decimalFormat.format(sizeInMB)

                val resolutionText = "$width x $height • $formattedSize MB"

                withContext(Dispatchers.Main) {
                    resolutionBadge.text = resolutionText
                    resolutionBadge.visibility = View.VISIBLE
                }
            } else {
                withContext(Dispatchers.Main) {
                    resolutionBadge.visibility = View.GONE
                }
            }
        }
    }

    private fun setupDirectories() {
        val (appDir, _, trashDir) = FileManagerUtils.createAppDirectories(this)
        appDirectory = appDir
        trashDirectory = trashDir
    }

    private fun setupButtonListeners() {
        deleteButton.setOnClickListener { confirmDelete() }
        moveButton.setOnClickListener { showMoveDialog() }
        shareButton.setOnClickListener { shareImage() }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_image_dialog_title)
            .setMessage(R.string.delete_image_dialog_message)
            .setPositiveButton(R.string.delete_button_text) { _, _ -> deleteImage() }
            .setNegativeButton(R.string.cancel_button_text, null)
            .show()
    }

    private fun deleteImage() {
        if (imagePaths.isEmpty() || currentImageIndex < 0 || currentImageIndex >= imagePaths.size) {
            return
        }
        val imagePath = imagePaths[currentImageIndex]
        val file = File(imagePath)
        if (file.exists() && file.delete()) {
            imagePaths.removeAt(currentImageIndex)
            pagerAdapter.notifyItemRemoved(currentImageIndex)


            if (imagePaths.isEmpty()) {
                finish()
            } else {
                if (currentImageIndex >= imagePaths.size) {
                    currentImageIndex = imagePaths.size - 1
                }
                // ViewPager automatically handles displaying the next item
            }
        } else {
            Toast.makeText(this, R.string.delete_image_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        if (imagePaths.isEmpty() || currentImageIndex < 0 || currentImageIndex >= imagePaths.size) {
            return
        }
        val imagePath = imagePaths[currentImageIndex]
        val imageFile = File(imagePath)
        if (imageFile.exists()) {
            val uri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", imageFile)
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "image/jpeg"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_image_chooser_title)))
        } else {
            Toast.makeText(this, R.string.image_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMoveDialog() {
        if (imagePaths.isEmpty() || currentImageIndex < 0 || currentImageIndex >= imagePaths.size) {
            return
        }
        val imagePath = imagePaths[currentImageIndex]
        val imageFile = File(imagePath)

        if (!imageFile.exists()) {
            Toast.makeText(this, R.string.image_preview_file_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        val sourceFolderPath = imageFile.parentFile?.absolutePath ?: appDirectory.absolutePath
        val rootFolderPath = appDirectory.absolutePath

        MoveFilesBottomSheet.newInstance(
            filePaths = listOf(imagePath),
            sourceFolderPath = sourceFolderPath,
            rootFolderPath = rootFolderPath,
            trashFolderPath = trashDirectory.absolutePath
        ).show(supportFragmentManager, MoveFilesBottomSheet.TAG)
    }

    private fun setupMoveResultListener() {
        supportFragmentManager.setFragmentResultListener(MoveFilesBottomSheet.REQUEST_KEY, this) { _, bundle ->
            val moved = bundle.getBoolean(MoveFilesBottomSheet.RESULT_MOVED)
            if (moved) {
                Toast.makeText(this, R.string.image_preview_move_success, Toast.LENGTH_SHORT).show()

                if (imagePaths.isEmpty() || currentImageIndex < 0 || currentImageIndex >= imagePaths.size) {
                    return@setFragmentResultListener
                }
                // Remove the current image from the list after successful move
                imagePaths.removeAt(currentImageIndex)
                pagerAdapter.notifyItemRemoved(currentImageIndex)


                // If the list of images is empty, close the activity
                if (imagePaths.isEmpty()) {
                    finish()
                } else {
                    // Adjust the index if necessary
                    if (currentImageIndex >= imagePaths.size) {
                        currentImageIndex = imagePaths.size - 1
                    }
                    viewPager.setCurrentItem(currentImageIndex, false)
                    updateResolutionBadge(imagePaths[currentImageIndex])
                }
            }
        }
    }
}
