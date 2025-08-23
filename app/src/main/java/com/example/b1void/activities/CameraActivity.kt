package com.example.b1void.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.b1void.R
import com.example.b1void.viewmodels.CameraEvent
import com.example.b1void.viewmodels.CameraViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CameraActivity : AppCompatActivity() {

    private lateinit var viewModel: CameraViewModel
    private lateinit var resolutionAdapter: ResolutionAdapter
    
    // Views
    private lateinit var resolutionSelectorButton: ImageButton
    private lateinit var watermarkButton: ImageButton
    private lateinit var shutterButton: ImageButton
    private lateinit var flashButton: ImageButton
    private lateinit var qualityToggleButton: ImageButton
    private lateinit var thumbnailPreview: ImageView
    private lateinit var resolutionListContainer: View
    private lateinit var progressBar: ProgressBar
    private lateinit var switchCameraButton: ImageButton

    // CameraX components
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        viewModel = ViewModelProvider(this).get(CameraViewModel::class.java)
        
        // Find views
        resolutionSelectorButton = findViewById(R.id.resolutionSelectorButton)
        resolutionListContainer = findViewById(R.id.resolutionListContainer)
        val resolutionRecyclerView = findViewById<RecyclerView>(R.id.resolutionRecyclerView)
        progressBar = findViewById(R.id.resolutionChangeProgressBar)
        shutterButton = findViewById(R.id.shutterButton)
        thumbnailPreview = findViewById(R.id.thumbnailPreview)
        flashButton = findViewById(R.id.flashButton)
        switchCameraButton = findViewById(R.id.switchCameraButton)


        // Setup RecyclerView
        resolutionAdapter = ResolutionAdapter { size ->
            viewModel.onResolutionSelected(size)
            rebindCameraUseCases()
            resolutionListContainer.visibility = View.GONE
        }
        resolutionRecyclerView.layoutManager = LinearLayoutManager(this)
        resolutionRecyclerView.adapter = resolutionAdapter

        // Setup Listeners
        resolutionSelectorButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            resolutionListContainer.visibility = if (resolutionListContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        shutterButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            val savePath = intent.getStringExtra(EXTRA_SAVE_PATH)
            viewModel.onTakePicture(imageCapture, savePath)
        }
        flashButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            viewModel.cycleFlashMode()
        }
        switchCameraButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            rebindCameraUseCases()
        }

        if (isCameraPermissionGranted()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
        
        observeUiState()
        observeCameraEvents()
    }
    
    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Update resolution list
                    resolutionAdapter.submitList(state.availableResolutions)
                    
                    // Show/hide progress bar
                    progressBar.visibility = if (state.isBinding) View.VISIBLE else View.GONE

                    // Update thumbnail
                    state.lastThumbnail?.let { thumbnail ->
                        thumbnailPreview.setImageBitmap(thumbnail)
                    }

                    // Update flash button icon
                    val flashIconRes = when (state.flashMode) {
                        ImageCapture.FLASH_MODE_ON -> android.R.drawable.ic_menu_camera
                        ImageCapture.FLASH_MODE_OFF -> android.R.drawable.ic_lock_power_off
                        else -> android.R.drawable.ic_menu_rotate // Represents Auto
                    }
                    flashButton.setImageResource(flashIconRes)
                    
                    // Apply torch state to the camera
                    camera?.cameraControl?.enableTorch(state.isTorchOn)
                }
            }
        }
    }

    private fun observeCameraEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.event.collect { event ->
                    when (event) {
                        is CameraEvent.Error -> {
                            Toast.makeText(this@CameraActivity, "Error: ${event.message}", Toast.LENGTH_LONG).show()
                        }
                        is CameraEvent.PictureSaved -> {
                            Toast.makeText(this@CameraActivity, "Photo saved!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            this.cameraProvider = cameraProviderFuture.get()
            rebindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun rebindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: return
        val uiState = viewModel.uiState.value

        val preview = Preview.Builder()
            .build()
            .also {
                val previewView = findViewById<PreviewView>(R.id.previewView)
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val captureMode = if (uiState.isQualityPriority) {
            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        } else {
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }

        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setFlashMode(uiState.flashMode)
        
        uiState.currentResolution?.let {
            imageCaptureBuilder.setTargetResolution(it)
        }
        this.imageCapture = imageCaptureBuilder.build()

        try {
            cameraProvider.unbindAll()
            this.camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, this.imageCapture)
            
            this.camera?.cameraInfo?.let { viewModel.onCameraBound(it) }
            
            lifecycleScope.launch {
                delay(300)
                viewModel.onRebindComplete()
            }

        } catch(exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Toast.makeText(this, "Failed to rebind camera.", Toast.LENGTH_SHORT).show()
            viewModel.onRebindComplete()
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (isCameraPermissionGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // --- Inner Adapter Class ---
    class ResolutionAdapter(private val onSizeSelected: (Size) -> Unit) : RecyclerView.Adapter<ResolutionAdapter.ResolutionViewHolder>() {

        private var resolutions: List<Size> = emptyList()

        fun submitList(newList: List<Size>) {
            resolutions = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResolutionViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ResolutionViewHolder(view)
        }

        override fun onBindViewHolder(holder: ResolutionViewHolder, position: Int) {
            val size = resolutions[position]
            holder.bind(size, onSizeSelected)
        }

        override fun getItemCount(): Int = resolutions.size

        class ResolutionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView: TextView = itemView.findViewById(android.R.id.text1)

            fun bind(size: Size, onSizeSelected: (Size) -> Unit) {
                textView.text = "${size.width} x ${size.height}"
                itemView.setOnClickListener { onSizeSelected(size) }
            }
        }
    }
    
    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        const val EXTRA_SAVE_PATH = "extra_save_path"
    }
}