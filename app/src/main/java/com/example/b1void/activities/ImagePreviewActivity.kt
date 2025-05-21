
package com.example.b1void.activities

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.b1void.R
import java.io.File

class ImagePreviewActivity : AppCompatActivity() {

    private lateinit var imagePaths: ArrayList<String>
    private var currentImageIndex: Int = 0
    private lateinit var imageView: ImageView
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var translateX: Float = 0f
    private var translateY: Float = 0f
    private var focusX: Float = 0f // Focus X coordinate during scaling
    private var focusY: Float = 0f // Focus Y coordinate during scaling


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

        if (imagePaths.size == 1) {
            prevButton.isEnabled = false
            nextButton.isEnabled = false
        }

        displayImage()

        prevButton.setOnClickListener {
            if (currentImageIndex > 0) {
                currentImageIndex--
                displayImage()
            } else {
                Toast.makeText(this, "No previous image.", Toast.LENGTH_SHORT).show()
            }
        }

        nextButton.setOnClickListener {
            if (currentImageIndex < imagePaths.size - 1) {
                currentImageIndex++
                displayImage()
            } else {
                Toast.makeText(this, "No next image.", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize ScaleGestureDetector
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
    }

    private fun displayImage() {
        val imagePath = imagePaths[currentImageIndex]
        val imageFile = File(imagePath)

        if (imageFile.exists()) {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            imageView.setImageBitmap(bitmap)
            // Reset scale and translation when loading a new image
            scaleFactor = 1.0f
            translateX = 0f
            translateY = 0f
            imageView.scaleX = scaleFactor
            imageView.scaleY = scaleFactor
            imageView.translationX = translateX
            imageView.translationY = translateY
        } else {
            Log.e("ImagePreview", "Image not found: $imagePath")
            imageView.setImageResource(R.drawable.def_insp_img) // Placeholder
            Toast.makeText(this, "Image not found.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(motionEvent)

        when (motionEvent.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = motionEvent.x
                lastTouchY = motionEvent.y
            }
            MotionEvent.ACTION_MOVE -> {
                val currentX = motionEvent.x
                val currentY = motionEvent.y
                val deltaX = (currentX - lastTouchX)
                val deltaY = (currentY - lastTouchY)

                translateX += deltaX
                translateY += deltaY

                // Apply limits to translation to prevent going too far
                val maxX = (imageView.width * (scaleFactor - 1)) / 2
                val maxY = (imageView.height * (scaleFactor - 1)) / 2

                translateX = translateX.coerceIn(-maxX, maxX)
                translateY = translateY.coerceIn(-maxY, maxY)

                imageView.translationX = translateX
                imageView.translationY = translateY

                lastTouchX = currentX
                lastTouchY = currentY
            }
        }

        return true
    }


    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            focusX = detector.focusX
            focusY = detector.focusY
            return true
        }


        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactorPrev = scaleFactor
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 5.0f))


            val focusX = detector.focusX
            val focusY = detector.focusY

            // Adjust translation to keep the focus point under the pinch point
            translateX += (focusX - this@ImagePreviewActivity.focusX) * (scaleFactor - scaleFactorPrev)
            translateY += (focusY - this@ImagePreviewActivity.focusY) * (scaleFactor - scaleFactorPrev)

            // Apply limits to translation to prevent going too far
            val maxX = (imageView.width * (scaleFactor - 1)) / 2
            val maxY = (imageView.height * (scaleFactor - 1)) / 2

            translateX = translateX.coerceIn(-maxX, maxX)
            translateY = translateY.coerceIn(-maxY, maxY)

            imageView.scaleX = scaleFactor
            imageView.scaleY = scaleFactor
            imageView.translationX = translateX
            imageView.translationY = translateY

            this@ImagePreviewActivity.focusX = focusX
            this@ImagePreviewActivity.focusY = focusY


            return true
        }

    }
}
