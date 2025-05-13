package com.example.b1void.activities

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.b1void.R
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var imagePaths: ArrayList<String>
    private var currentImageIndex: Int = 0
    private lateinit var imageView: ImageView
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        imageView = findViewById(R.id.image_preview)
        prevButton = findViewById(R.id.prev_button)
        nextButton = findViewById(R.id.next_button)

        imagePaths = intent.getStringArrayListExtra("image_paths") ?: ArrayList()
        currentImageIndex = intent.getIntExtra("current_image_index", 0)

        if (imagePaths.isEmpty()) {
            Toast.makeText(this, "No images to display.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (imagePaths.size <= 1) {
            prevButton.isEnabled = false
            nextButton.isEnabled = false
        } else {
            updateButtonVisibility()
        }

        displayImage()

        prevButton.setOnClickListener {
            if (currentImageIndex > 0) {
                currentImageIndex--
                displayImage()
                updateButtonVisibility()
            }
        }

        nextButton.setOnClickListener {
            if (currentImageIndex < imagePaths.size - 1) {
                currentImageIndex++
                displayImage()
                updateButtonVisibility()
            }
        }
    }

    private fun displayImage() {
        val imagePath = imagePaths[currentImageIndex]
        val imageFile = File(imagePath)

        if (imageFile.exists()) {
            Glide.with(this)
                .load(imageFile)
                .placeholder(R.drawable.def_insp_img)
                .error(R.drawable.def_insp_img)
                .into(imageView)
        } else {
            Log.e("ImagePreview", "Image not found: $imagePath")
            imageView.setImageResource(R.drawable.def_insp_img)
            Toast.makeText(this, "Image not found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateButtonVisibility() {
        prevButton.isEnabled = currentImageIndex > 0
        nextButton.isEnabled = currentImageIndex < imagePaths.size - 1
    }
}
