
package com.example.b1void.activities

import android.content.ContentValues
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.RectF
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.b1void.R
import com.example.b1void.cameraFun.appSettingOpen
import com.example.b1void.cameraFun.gone
import com.example.b1void.cameraFun.visible
import com.example.b1void.cameraFun.warningPermissionDialog
import com.example.b1void.databinding.ActivityCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class CameraActivity : AppCompatActivity() {

    private val activityCameraBinding: ActivityCameraBinding by lazy {
        ActivityCameraBinding.inflate(layoutInflater)
    }

    private val multiplePermissionId = 14
    private val multiplePermissionNameList = if (Build.VERSION.SDK_INT >= 33) {
        arrayListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayListOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    private lateinit var videoCapture: VideoCapture<Recorder>
    private var recording: Recording? = null
    private var isPhoto = true
    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var camera: Camera
    private lateinit var cameraSelector: CameraSelector
    private var orientationEventListener: OrientationEventListener? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var aspectRatio = AspectRatio.RATIO_16_9
    private lateinit var appDirectory: File // Removed initialization here
    private var currentDirectoryPath: String? = null // Путь к текущей директории

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(activityCameraBinding.root)

        // Получаем путь из intent
        currentDirectoryPath = intent.getStringExtra("current_directory")

        // Устанавливаем директорию сохранения изображений
        appDirectory = if(currentDirectoryPath != null) {
            File(currentDirectoryPath!!)
        } else {
            getExternalFilesDir(null)?.let { File(it, "InspectorAppFolder") }
                ?: File(filesDir, "InspectorAppFolder") //Fallback to internal storage if external fails
        }
        if (!appDirectory.exists() && !appDirectory.mkdirs()) {
            Toast.makeText(this, "Failed to create app folder", Toast.LENGTH_SHORT).show()
        }



        if (checkMultiplePermission()) {
            startCamera()
        }

        setupListeners()
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun setupListeners() {
        activityCameraBinding.flipCameraIB.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            bindCameraUserCases()
        }

        activityCameraBinding.aspectRatioTxt.setOnClickListener {
            toggleAspectRatio()
        }

        activityCameraBinding.changeCameraToVideoIB.setOnClickListener {
            togglePhotoVideoMode()
        }

        activityCameraBinding.captureIB.setOnClickListener {
            if (isPhoto) {
                takePhoto()
            } else {
                captureVideo()
            }
        }

        activityCameraBinding.flashToggleIB.setOnClickListener {
            setFlashIcon(camera)
        }
    }

    private fun checkMultiplePermission(): Boolean {
        val listPermissionNeeded = arrayListOf<String>()
        for (permission in multiplePermissionNameList) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                listPermissionNeeded.add(permission)
            }
        }
        if (listPermissionNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                listPermissionNeeded.toTypedArray(),
                multiplePermissionId
            )
            return false
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == multiplePermissionId) {
            handlePermissionsResult(grantResults, permissions)
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun handlePermissionsResult(grantResults: IntArray, permissions: Array<out String>) {
        if (grantResults.isNotEmpty()) {
            var isGrant = true
            for (element in grantResults) {
                if (element == PackageManager.PERMISSION_DENIED) {
                    isGrant = false
                }
            }
            if (isGrant) {
                startCamera()
            } else {
                handlePermissionDenied(permissions)
            }
        }
    }

    private fun handlePermissionDenied(permissions: Array<out String>) {
        var someDenied = false
        for (permission in permissions) {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                if (ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                    someDenied = true
                }
            }
        }
        if (someDenied) {
            appSettingOpen(this)
        } else {
            warningPermissionDialog(this) { _: DialogInterface, which: Int ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> checkMultiplePermission()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUserCases()
        }, ContextCompat.getMainExecutor(this))
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun bindCameraUserCases() {
        // Проверка на null перед получением rotation
        val rotation = activityCameraBinding.previewView.display?.rotation ?: Surface.ROTATION_0

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    aspectRatio,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
            )
            .build()

        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build().apply {
                surfaceProvider = activityCameraBinding.previewView.surfaceProvider
            }

        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HIGHEST,
                    FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                )
            )
            .setAspectRatio(aspectRatio)
            .build()

        videoCapture = VideoCapture.withOutput(recorder).apply {
            targetRotation = rotation
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(resolutionSelector)
            .setTargetRotation(rotation)
            .build()

        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val myRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture.targetRotation = myRotation
                videoCapture.targetRotation = myRotation
            }
        }
        orientationEventListener?.enable()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
            setUpZoomTapToFocus()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun toggleAspectRatio() {
        aspectRatio = if (aspectRatio == AspectRatio.RATIO_16_9) {
            AspectRatio.RATIO_4_3
        } else {
            AspectRatio.RATIO_16_9
        }
        setAspectRatio(if (aspectRatio == AspectRatio.RATIO_4_3) "H,4:3" else "H,0:0")
        bindCameraUserCases()
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun togglePhotoVideoMode() {
        isPhoto = !isPhoto
        if (isPhoto) {
            activityCameraBinding.changeCameraToVideoIB.setImageResource(R.drawable.ic_photo)
            activityCameraBinding.captureIB.setImageResource(R.drawable.camera)
        } else {
            activityCameraBinding.changeCameraToVideoIB.setImageResource(R.drawable.ic_videocam)
            activityCameraBinding.captureIB.setImageResource(R.drawable.ic_start)
        }

        bindCameraUserCases() // Перепривязываем пользовательские случаи
    }

    private fun setFlashIcon(camera: Camera) {
        if (camera.cameraInfo.hasFlashUnit()) {
            if (camera.cameraInfo.torchState.value == 0) {
                camera.cameraControl.enableTorch(true)
                activityCameraBinding.flashToggleIB.setImageResource(R.drawable.flash_off)
            } else {
                camera.cameraControl.enableTorch(false)
                activityCameraBinding.flashToggleIB.setImageResource(R.drawable.flash_on)
            }
        } else {
            Toast.makeText(this, "Flash is Not Available", Toast.LENGTH_LONG).show()
            activityCameraBinding.flashToggleIB.isEnabled = false
        }
    }

    private fun captureVideo() {
        activityCameraBinding.captureIB.isEnabled = false

        // Скрытие элементов интерфейса во время захвата видео
        activityCameraBinding.flashToggleIB.gone()
        activityCameraBinding.flipCameraIB.gone()
        activityCameraBinding.aspectRatioTxt.gone()
        activityCameraBinding.changeCameraToVideoIB.gone()

        if (recording != null) {
            recording?.stop()
            stopRecording()
            recording = null
            return
        }
        startRecording()
        val fileName = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis()) + ".mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (ActivityCompat.checkSelfPermission(this@CameraActivity, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        activityCameraBinding.captureIB.setImageResource(R.drawable.ic_stop)
                        activityCameraBinding.captureIB.isEnabled = true
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val message = "Video Capture Succeeded: ${recordEvent.outputResults.outputUri}"
                            Toast.makeText(this@CameraActivity, message, Toast.LENGTH_LONG).show()
                        } else {
                            recording?.close()
                            recording = null
                            Log.d("error", recordEvent.error.toString())
                        }
                        activityCameraBinding.captureIB.setImageResource(R.drawable.ic_start)
                        activityCameraBinding.captureIB.isEnabled = true

                        // Восстановление интерфейса после завершения захвата
                        activityCameraBinding.flashToggleIB.visible()
                        activityCameraBinding.flipCameraIB.visible()
                        activityCameraBinding.aspectRatioTxt.visible()
                        activityCameraBinding.changeCameraToVideoIB.visible()
                    }
                }
            }
    }

    private fun takePhoto() {
        val fileName = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis()) + ".jpg"
        val imageFile = File(appDirectory, fileName) //Save to appDirectory

        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = (lensFacing == CameraSelector.LENS_FACING_FRONT)
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile)
            .setMetadata(metadata)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val message = "Photo Capture Succeeded: ${outputFileResults.savedUri}"
                    Toast.makeText(this@CameraActivity, message, Toast.LENGTH_LONG).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@CameraActivity, exception.message.toString(), Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun setAspectRatio(ratio: String) {
        activityCameraBinding.previewView.layoutParams = activityCameraBinding.previewView.layoutParams.apply {
            if (this is ConstraintLayout.LayoutParams) {
                dimensionRatio = ratio
            }
        }
    }

    override fun onResume() {
        super.onResume()
        orientationEventListener?.enable()
    }

    override fun onPause() {
        orientationEventListener?.disable()
        if (recording != null) {
            recording?.stop()
            captureVideo()
        }
        super.onPause()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val updateTimer = object : Runnable {
        override fun run() {
            val currentTime = SystemClock.elapsedRealtime() - activityCameraBinding.recodingTimerC.base
            val timeString = currentTime.toFormattedTime()
            activityCameraBinding.recodingTimerC.text = timeString
            handler.postDelayed(this, 1000)
        }
    }

    private fun Long.toFormattedTime(): String {
        val seconds = ((this / 1000) % 60).toInt()
        val minutes = ((this / (1000 * 60)) % 60).toInt()
        val hours = ((this / (1000 * 60 * 60)) % 24).toInt()

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun startRecording() {
        activityCameraBinding.recodingTimerC.visible()
        activityCameraBinding.recodingTimerC.base = SystemClock.elapsedRealtime()
        activityCameraBinding.recodingTimerC.start()
        handler.post(updateTimer)
    }

    private fun stopRecording() {
        activityCameraBinding.recodingTimerC.gone()
        activityCameraBinding.recodingTimerC.stop()
        handler.removeCallbacks(updateTimer)
    }

    private fun setUpZoomTapToFocus() {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                camera.cameraControl.setZoomRatio(currentZoomRatio * delta)
                return true
            }
        }

        val scaleGestureDetector = ScaleGestureDetector(this, listener)

        activityCameraBinding.previewView.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = activityCameraBinding.previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(2, TimeUnit.SECONDS)
                    .build()

                val x = event.x
                val y = event.y

                val focusCircle = RectF(x - 50, y - 50, x + 50, y + 50)
                activityCameraBinding.focusCircleView.focusCircle = focusCircle
                activityCameraBinding.focusCircleView.invalidate()

                camera.cameraControl.startFocusAndMetering(action)

                view.performClick()
            }
            true
        }
    }
}
