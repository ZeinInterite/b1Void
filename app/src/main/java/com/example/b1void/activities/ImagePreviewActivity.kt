// ImagePreviewActivity.kt

package com.example.b1void.activities

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.b1void.R
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        val imagePath = intent.getStringExtra("image_path")
        val imageView: ImageView = findViewById(R.id.image_preview)

        if (imagePath != null) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                imageView.setImageBitmap(bitmap)
            } else {
                imageView.setImageResource(R.drawable.image_ic) // Default icon if image not found
            }
        } else {
            imageView.setImageResource(R.drawable.image_ic) // Default icon if path is null
        }
    }
}
