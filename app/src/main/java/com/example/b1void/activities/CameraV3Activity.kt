package com.example.b1void

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Suppress("UNUSED_EXPRESSION")
class CameraV3Activity : AppCompatActivity() {
private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var timeTextView: TextView
    private lateinit var signatureEditText: EditText
    private lateinit var zoomSeekBar: SeekBar
    private lateinit var resolutionSpinner: Spinner

    private lateinit var qualitySeekBar: SeekBar
    private lateinit var qualityTextView: TextView
    private var quality: Int = 85 // Default quality

    private lateinit var blurSeekBar: SeekBar
    private lateinit var blurTextView: TextView
    private var blurRadius: Int = 0 // Default blur radius

    private var camera: Camera? = null

    companion object {
        private const val TAG = "CameraXExample"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_v3)

        timeTextView = findViewById(R.id.timeTextView)
        signatureEditText = findViewById(R.id.signatureEditText)
        zoomSeekBar = findViewById(R.id.zoomSeekBar)
        resolutionSpinner = findViewById(R.id.resolutionSpinner)

        qualitySeekBar = findViewById(R.id.qualitySeekBar)
        qualityTextView = findViewById(R.id.qualityTextView)
        blurSeekBar = findViewById(R.id.blurSeekBar)
        blurTextView = findViewById(R.id.blurTextView)


        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Set up the listeners for take photo button
        findViewById<android.widget.Button>(R.id.camera_capture_button).setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupTimeUpdater()
        setupZoomControl()
        setupResolutionSpinner()
        setupQualityControl()
        setupBlurControl()
    }

    private fun setupResolutionSpinner() {
        val resolutions = listOf("640x480", "1280x720", "1920x1080") // Example resolutions
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, resolutions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        resolutionSpinner.adapter = adapter

        resolutionSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                // Rebind camera use cases when resolution changes
                startCamera()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // Do nothing
            }
        })
    }

    private fun setupZoomControl() {
        zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                camera?.cameraControl?.setZoomRatio(1f + (progress / 100f)) // Example:  from 1x to 2x
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            // Добавлено указание типа Unit для возвращаемого значения функции onStopTrackingTouch
            override fun onStopTrackingTouch(seekBar: SeekBar?): Unit {}
        })
    }

    private fun setupQualityControl() {
        qualitySeekBar.progress = quality // Инициализируем progress ползунка
        qualityTextView.text = "Quality: $quality%" // Инициализируем текст

        qualitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                quality = progress
                qualityTextView.text = "Quality: $quality%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?): Unit {}
        })
    }

    private fun setupBlurControl() {
        blurSeekBar.max = 25 // Максимальный радиус размытия (можно настроить)
        blurSeekBar.progress = blurRadius // Инициализируем progress ползунка
        blurTextView.text = "Blur: $blurRadius" // Инициализируем текст

        blurSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                blurRadius = progress
                blurTextView.text = "Blur: $blurRadius"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?): Unit {}
        })
    }

    private fun setupTimeUpdater() {
        timeHandler.post(timeRunnable)
    }

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val currentTime = sdf.format(Date())
            timeTextView.text = currentTime
            timeHandler.postDelayed(this, 1000) // Обновлять каждую секунду
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "Photo capture succeeded: ${output.savedUri}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)

                    // Add timestamp and signature
                    val savedUri = output.savedUri ?: return
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, savedUri)
                    val signature = signatureEditText.text.toString()
                    val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val bitmapWithText = addTextToBitmap(bitmap, dateTime, signature, 40f)
                    saveImage(bitmapWithText, photoFile.name) // Save the modified bitmap

                    // Delete the original image
                    contentResolver.delete(savedUri, null, null)
                }
            }
        )
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(findViewById<PreviewView>(R.id.viewFinder).surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

                // Touch to focus
                val previewView = findViewById<PreviewView>(R.id.viewFinder)
                previewView.setOnTouchListener { _, event ->
                    return@setOnTouchListener when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            touchFocus(previewView, camera!!, event.x, event.y)
                            true
                        }
                        else -> false
                    }
                }
            } catch(exc: Exception) {
                Log.e(TAG, "Binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun touchFocus(previewView: PreviewView, camera: Camera, x: Float, y: Float) {
        val meteringPointFactory = previewView.meteringPointFactory
        val meteringPoint: MeteringPoint = meteringPointFactory.createPoint(x, y)

        val action = FocusMeteringAction.Builder(meteringPoint).build()
        camera.cameraControl.startFocusAndMetering(action)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        timeHandler.removeCallbacks(timeRunnable)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    private fun addTextToBitmap(bitmap: Bitmap, dateTime: String, signature: String, fontSize: Float): Bitmap {
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true) // Важно: создать копию!
        val canvas = Canvas(outputBitmap)
        val paint = Paint()
        paint.color = Color.WHITE // Цвет текста
        paint.textSize = fontSize
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.LEFT // Выравнивание текста

        // Штамп даты и времени
        canvas.drawText(dateTime, 20f, bitmap.height - 80f, paint)  // Позиция текста

        //Подпись
        canvas.drawText(signature, 20f, bitmap.height - 20f, paint)

        return outputBitmap
    }


    private fun saveImage(bitmap: Bitmap, filename: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg") // Или image/png
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES) //  Папка
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream) // Или PNG
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to save image: ${e.message}", e)
                resolver.delete(it, null, null) // Очистить, если не удалось сохранить
            }
        } ?: run {
            Log.e(TAG, "Failed to create new MediaStore record.")
        }
    }
}