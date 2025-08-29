package com.example.b1void.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.webkit.MimeTypeMap
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.b1void.R
import com.example.b1void.adapters.ResolutionAdapter
import com.example.b1void.data.CameraSettingsManager
import com.example.b1void.ui.CameraSettingsDialogFragment
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private enum class CaptureMode {
        PHOTO,
        VIDEO
    }

    // View references
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: ImageButton
    private lateinit var modeSwitchButton: ImageButton
    private lateinit var flipCameraButton: ImageButton
    private lateinit var recordingTimer: Chronometer
    private lateinit var thumbnailPreview: ImageView
    private lateinit var settingsButton: ImageButton
    private lateinit var torchButton: ImageButton
    private lateinit var resolutionSelectorButton: ImageButton
    private lateinit var resolutionListContainer: CardView
    private lateinit var resolutionRecyclerView: RecyclerView

    // CameraX components
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null

    // Settings
    private lateinit var settingsManager: CameraSettingsManager
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var timestampEnabled = true
    private var selectedResolution: Size? = null

    // State variables
    private var currentMode = CaptureMode.PHOTO
    private var isRecording = false
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var lastSavedFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        settingsManager = CameraSettingsManager(this)
        initializeViews()
        setupListeners()
        observeSettings()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun initializeViews() {
        previewView = findViewById(R.id.previewView)
        captureButton = findViewById(R.id.shutterButton)
        modeSwitchButton = findViewById(R.id.mode_switch_button)
        flipCameraButton = findViewById(R.id.switchCameraButton)
        recordingTimer = findViewById(R.id.recording_timer)
        thumbnailPreview = findViewById(R.id.thumbnailPreview)
        settingsButton = findViewById(R.id.settingsButton)
        torchButton = findViewById(R.id.torchButton)
        resolutionSelectorButton = findViewById(R.id.resolutionSelectorButton)
        resolutionListContainer = findViewById(R.id.resolutionListContainer)
        resolutionRecyclerView = findViewById(R.id.resolutionRecyclerView)
    }

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsManager.getFlashMode().collect { mode ->
                val newFlashMode = when(mode) {
                    0 -> ImageCapture.FLASH_MODE_OFF
                    1 -> ImageCapture.FLASH_MODE_ON
                    2 -> ImageCapture.FLASH_MODE_AUTO
                    else -> ImageCapture.FLASH_MODE_OFF
                }
                if (newFlashMode != flashMode) {
                    flashMode = newFlashMode
                    startCamera()
                }
            }
        }
        lifecycleScope.launch {
            settingsManager.isTimestampEnabled().collect { isEnabled ->
                timestampEnabled = isEnabled
            }
        }
        lifecycleScope.launch {
            settingsManager.getResolution().collect { resString ->
                val newResolution = resString?.let { parseResolution(it) }
                if (newResolution != selectedResolution) {
                    selectedResolution = newResolution
                    startCamera()
                }
            }
        }
    }

    private fun setupListeners() {
        captureButton.setOnClickListener {
            if (currentMode == CaptureMode.PHOTO) {
                takePhoto()
            } else {
                toggleVideoRecording()
            }
        }

        modeSwitchButton.setOnClickListener {
            currentMode = if (currentMode == CaptureMode.PHOTO) CaptureMode.VIDEO else CaptureMode.PHOTO
            updateCameraUI()
        }

        flipCameraButton.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            startCamera()
        }

        thumbnailPreview.setOnClickListener {
            lastSavedFile?.let {
                if (it.exists()) {
                    onThumbnailClicked(it)
                }
            }
        }

        settingsButton.setOnClickListener {
            CameraSettingsDialogFragment().show(supportFragmentManager, "CameraSettingsDialog")
        }

        resolutionSelectorButton.setOnClickListener {
            resolutionListContainer.visibility = if (resolutionListContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        torchButton.setOnClickListener {
            camera?.let {
                if (it.cameraInfo.hasFlashUnit()) {
                    val isTorchOn = it.cameraInfo.torchState.value == TorchState.ON
                    it.cameraControl.enableTorch(!isTorchOn)
                }
            }
        }
    }

    private fun onThumbnailClicked(file: File) {
        val isImage = file.extension.equals("jpg", ignoreCase = true) || file.extension.equals("jpeg", ignoreCase = true)

        if (isImage) {
            val imagePaths = ArrayList<String>()
            imagePaths.add(file.absolutePath)
            val intent = Intent(this, ImagePreviewActivity::class.java).apply {
                putStringArrayListExtra("image_paths", imagePaths)
                putExtra("current_image_index", 0)
            }
            startActivity(intent)
        } else {
            val authority = "${applicationContext.packageName}.provider"
            val uri = FileProvider.getUriForFile(this, authority, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Не найдено приложение для открытия файла", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCameraUI() {
        runOnUiThread {
            if (currentMode == CaptureMode.VIDEO) {
                modeSwitchButton.setImageResource(R.drawable.ic_switch_to_photo)
                if (!isRecording) {
                    captureButton.setBackgroundResource(R.drawable.bg_capture_button_recording)
                    captureButton.setImageResource(R.drawable.ic_videocam)
                }
            } else {
                modeSwitchButton.setImageResource(R.drawable.ic_switch_to_video)
                captureButton.setBackgroundResource(R.drawable.bg_capture_button_photo)
                captureButton.setImageResource(R.drawable.camera)
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val imageCaptureBuilder = ImageCapture.Builder()
                .setFlashMode(flashMode)
            
            selectedResolution?.let {
                imageCaptureBuilder.setTargetResolution(it)
            }

            imageCapture = imageCaptureBuilder.build()

            try {
                cameraProvider?.unbindAll()
                camera = cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture
                )
                setupTorchObserver()
                setupResolutionList()
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setupResolutionList() {
        camera?.let { cam ->
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager.getCameraCharacteristics(Camera2CameraInfo.from(cam.cameraInfo).cameraId)
            val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val resolutions = streamConfigurationMap?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()

            resolutionRecyclerView.layoutManager = LinearLayoutManager(this)
            resolutionRecyclerView.adapter = ResolutionAdapter(resolutions.reversed(), selectedResolution) { size ->
                lifecycleScope.launch {
                    settingsManager.setResolution("${size.width}x${size.height}")
                }
                resolutionListContainer.visibility = View.GONE
            }
        }
    }

    private fun setupTorchObserver() {
        camera?.cameraInfo?.torchState?.observe(this) { state ->
            if (state == TorchState.ON) {
                torchButton.setColorFilter(ContextCompat.getColor(this, R.color.yellow))
            } else {
                torchButton.clearColorFilter()
            }
        }
    }

    private fun takePhoto() {
        val imageCapture = this.imageCapture ?: return

        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = imageProxyToBitmap(image)
                image.close()

                val finalBitmap = if (timestampEnabled) {
                    addTimestampToBitmap(bitmap)
                } else {
                    bitmap
                }

                val savedFile = saveBitmapToFile(finalBitmap)
                lastSavedFile = savedFile

                runOnUiThread {
                    val msg = "Фото сохранено: ${Uri.fromFile(savedFile)}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    updateThumbnail(Uri.fromFile(savedFile))
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }
        })
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun addTimestampToBitmap(originalBitmap: Bitmap): Bitmap {
        val newBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(newBitmap)
        val paint = Paint().apply {
            color = Color.RED
            textSize = 96f // Large text
            isAntiAlias = true
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())

        val bounds = Rect()
        paint.getTextBounds(timestamp, 0, timestamp.length, bounds)
        val x = newBitmap.width - bounds.width() - 50f // Bottom-right corner with padding
        val y = newBitmap.height - 50f

        canvas.drawText(timestamp, x, y, paint)
        return newBitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath ?: ""
        val file = File(savePath, "IMG_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }
        return file
    }

    private fun toggleVideoRecording() {
        val videoCapture = this.videoCapture ?: return

        if (isRecording) {
            recording?.stop()
            recording = null
            return
        }

        isRecording = true
        startRecordingIndicator()

        val savePath = intent.getStringExtra(EXTRA_SAVE_PATH) ?: externalMediaDirs.firstOrNull()?.absolutePath ?: return
        val videoFile = File(savePath, "VID_${System.currentTimeMillis()}.mp4")
        lastSavedFile = videoFile
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {}
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        stopRecordingIndicator()
                        if (!recordEvent.hasError()) {
                            val msg = "Видео сохранено: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            updateThumbnail(recordEvent.outputResults.outputUri)
                        } else {
                            Log.e(TAG, "Video capture error: ${recordEvent.error}")
                            videoFile.delete()
                        }
                    }
                }
            }
    }

    private fun updateThumbnail(uri: Uri) {
        runOnUiThread {
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(thumbnailPreview)
        }
    }

    private fun startRecordingIndicator() {
        runOnUiThread {
            captureButton.setBackgroundResource(R.drawable.bg_capture_button_recording)
            val anim = AlphaAnimation(0.5f, 1.0f).apply {
                duration = 700
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            captureButton.startAnimation(anim)
            recordingTimer.visibility = View.VISIBLE
            recordingTimer.base = SystemClock.elapsedRealtime()
            recordingTimer.start()
            modeSwitchButton.isEnabled = false
            flipCameraButton.isEnabled = false
        }
    }

    private fun stopRecordingIndicator() {
        runOnUiThread {
            captureButton.clearAnimation()
            captureButton.setBackgroundResource(R.drawable.bg_capture_button_recording)
            recordingTimer.stop()
            recordingTimer.visibility = View.GONE
            modeSwitchButton.isEnabled = true
            flipCameraButton.isEnabled = true
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun parseResolution(resString: String): Size? {
        return try {
            val parts = resString.split("x")
            Size(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Разрешения не предоставлены.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        recording?.stop()
        recording = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        const val EXTRA_SAVE_PATH = "extra_save_path"
    }
}