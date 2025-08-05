
package com.example.b1void.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import com.example.b1void.R
import com.example.b1void.adapters.ImagePagerAdapter
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var buttonsLayout: LinearLayout
    private lateinit var deleteButton: ImageButton
    private lateinit var shareButton: ImageButton

    private lateinit var imagePaths: MutableList<String>
    private var currentImageIndex: Int = 0
    private lateinit var pagerAdapter: ImagePagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        viewPager = findViewById(R.id.view_pager)
        buttonsLayout = findViewById(R.id.buttons_layout)
        deleteButton = findViewById(R.id.delete_button)
        shareButton = findViewById(R.id.share_button)

        imagePaths = intent.getStringArrayListExtra("image_paths")?.toMutableList() ?: mutableListOf()
        currentImageIndex = intent.getIntExtra("current_image_index", 0)

        if (imagePaths.isEmpty()) {
            Toast.makeText(this, "No images to display.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViewPager()
        setupButtonListeners()
    }

    private fun setupViewPager() {
        pagerAdapter = ImagePagerAdapter(imagePaths)
        viewPager.adapter = pagerAdapter
        viewPager.setCurrentItem(currentImageIndex, false)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentImageIndex = position
            }
        })
    }

    private fun setupButtonListeners() {
        deleteButton.setOnClickListener { confirmDelete() }
        shareButton.setOnClickListener { shareImage() }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Image")
            .setMessage("Are you sure you want to delete this image?")
            .setPositiveButton("Delete") { _, _ -> deleteImage() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteImage() {
        val imagePath = imagePaths[currentImageIndex]
        val file = File(imagePath)
        if (file.exists() && file.delete()) {
            imagePaths.removeAt(currentImageIndex)
            pagerAdapter.notifyItemRemoved(currentImageIndex)

            if (imagePaths.isEmpty()) {
                Toast.makeText(this, "No more images.", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // The ViewPager will automatically show the next/previous item.
            }
        } else {
            Toast.makeText(this, "Failed to delete image.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
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
            startActivity(Intent.createChooser(shareIntent, "Share Image"))
        } else {
            Toast.makeText(this, "Image not found.", Toast.LENGTH_SHORT).show()
        }
    }
}
