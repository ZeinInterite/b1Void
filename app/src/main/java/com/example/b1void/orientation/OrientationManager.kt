package com.example.b1void.orientation

import android.content.Context
import android.content.res.Configuration
import android.view.Surface
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages orientation-based UI layout transitions for the camera interface.
 * Provides smooth animations between portrait and landscape layouts with
 * optimized component positioning and sizing.
 */
class OrientationManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    
    enum class Orientation {
        PORTRAIT,
        LANDSCAPE
    }
    
    data class ComponentPosition(
        val x: Float,
        val y: Float,
        val width: Int,
        val height: Int,
        val marginStart: Int = 0,
        val marginTop: Int = 0,
        val marginEnd: Int = 0,
        val marginBottom: Int = 0
    )
    
    // Animation settings
    private val transitionDuration = 300L
    private val interpolator = AccelerateDecelerateInterpolator()
    
    // State management
    var currentOrientation = Orientation.PORTRAIT
        private set
    
    var targetOrientation = Orientation.PORTRAIT
        private set
    
    var isTransitioning = false
        private set
    
    // Auto-hide functionality
    private var autoHideJob: Job? = null
    private val autoHideDelay = 3000L // 3 seconds
    
    // Component references
    private var captureButton: ImageButton? = null
    private var thumbnailPreview: ImageView? = null
    private var topControls: ConstraintLayout? = null
    private var bottomControls: ConstraintLayout? = null
    private var rootLayout: ConstraintLayout? = null
    
    // Listeners
    private var orientationChangeListener: ((Orientation) -> Unit)? = null
    private var transitionListener: ((Boolean) -> Unit)? = null
    
    /**
     * Initialize the orientation manager with UI component references
     */
    fun initialize(
        captureButton: ImageButton,
        thumbnailPreview: ImageView,
        topControls: ConstraintLayout,
        bottomControls: ConstraintLayout,
        rootLayout: ConstraintLayout
    ) {
        this.captureButton = captureButton
        this.thumbnailPreview = thumbnailPreview
        this.topControls = topControls
        this.bottomControls = bottomControls
        this.rootLayout = rootLayout
        
        // Set initial orientation based on current configuration
        currentOrientation = getCurrentOrientationFromConfig()
        targetOrientation = currentOrientation
    }
    
    /**
     * Detect orientation change and trigger layout transition
     */
    fun detectOrientationChange(rotation: Int) {
        val newOrientation = when (rotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> Orientation.PORTRAIT
            Surface.ROTATION_90, Surface.ROTATION_270 -> Orientation.LANDSCAPE
            else -> currentOrientation
        }
        
        if (newOrientation != currentOrientation && !isTransitioning) {
            targetOrientation = newOrientation
            triggerLayoutTransition()
        }
    }
    
    /**
     * Trigger smooth layout transition to target orientation
     */
    private fun triggerLayoutTransition() {
        if (isTransitioning) return
        
        isTransitioning = true
        transitionListener?.invoke(true)
        
        lifecycleOwner.lifecycleScope.launch {
            when (targetOrientation) {
                Orientation.LANDSCAPE -> applyLandscapeLayout()
                Orientation.PORTRAIT -> applyPortraitLayout()
            }
            
            delay(transitionDuration)
            
            currentOrientation = targetOrientation
            isTransitioning = false
            transitionListener?.invoke(false)
            
            orientationChangeListener?.invoke(currentOrientation)
            
            // Start auto-hide timer for landscape mode
            if (currentOrientation == Orientation.LANDSCAPE) {
                startAutoHideTimer()
            } else {
                cancelAutoHideTimer()
            }
        }
    }
    
    /**
     * Apply landscape-optimized layout with animations
     */
    private fun applyLandscapeLayout() {
        val rootLayout = this.rootLayout ?: return
        val captureButton = this.captureButton ?: return
        val thumbnailPreview = this.thumbnailPreview ?: return
        
        // Create constraint set for landscape layout
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
        // Animate capture button to bottom-right with larger size
        animateCaptureButtonForLandscape(captureButton)
        
        // Move thumbnail to top panel
        animateThumbnailForLandscape(thumbnailPreview, constraintSet)
        
        // Apply constraint changes with animation
        constraintSet.applyTo(rootLayout)
        
        // Minimize non-essential controls
        minimizeControlsForLandscape()
    }
    
    /**
     * Apply portrait layout with animations
     */
    private fun applyPortraitLayout() {
        val rootLayout = this.rootLayout ?: return
        val captureButton = this.captureButton ?: return
        val thumbnailPreview = this.thumbnailPreview ?: return
        
        // Create constraint set for portrait layout
        val constraintSet = ConstraintSet()
        constraintSet.clone(rootLayout)
        
        // Animate capture button to bottom center with normal size
        animateCaptureButtonForPortrait(captureButton)
        
        // Move thumbnail to bottom-left
        animateThumbnailForPortrait(thumbnailPreview, constraintSet)
        
        // Apply constraint changes with animation
        constraintSet.applyTo(rootLayout)
        
        // Restore full controls for portrait
        restoreControlsForPortrait()
    }
    
    /**
     * Animate capture button for landscape mode
     */
    private fun animateCaptureButtonForLandscape(button: ImageButton) {
        val currentWidth = button.layoutParams.width
        val currentHeight = button.layoutParams.height
        
        val targetWidth = (currentWidth * 1.17f).toInt() // ~17% larger
        val targetHeight = (currentHeight * 1.17f).toInt()
        
        button.animate()
            .scaleX(1.17f)
            .scaleY(1.17f)
            .setDuration(transitionDuration)
            .setInterpolator(interpolator)
            .start()
    }
    
    /**
     * Animate capture button for portrait mode
     */
    private fun animateCaptureButtonForPortrait(button: ImageButton) {
        button.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(transitionDuration)
            .setInterpolator(interpolator)
            .start()
    }
    
    /**
     * Animate thumbnail for landscape mode
     */
    private fun animateThumbnailForLandscape(thumbnail: ImageView, constraintSet: ConstraintSet) {
        // The actual positioning is handled by the landscape layout file
        // This provides smooth transition animation
        thumbnail.animate()
            .alpha(0.7f)
            .setDuration(transitionDuration / 2)
            .setInterpolator(interpolator)
            .withEndAction {
                thumbnail.animate()
                    .alpha(1.0f)
                    .setDuration(transitionDuration / 2)
                    .start()
            }
            .start()
    }
    
    /**
     * Animate thumbnail for portrait mode  
     */
    private fun animateThumbnailForPortrait(thumbnail: ImageView, constraintSet: ConstraintSet) {
        // The actual positioning is handled by the portrait layout file
        // This provides smooth transition animation
        thumbnail.animate()
            .alpha(0.7f)
            .setDuration(transitionDuration / 2)
            .setInterpolator(interpolator)
            .withEndAction {
                thumbnail.animate()
                    .alpha(1.0f)
                    .setDuration(transitionDuration / 2)
                    .start()
            }
            .start()
    }
    
    /**
     * Minimize controls for landscape mode
     */
    private fun minimizeControlsForLandscape() {
        val topControls = this.topControls ?: return
        val bottomControls = this.bottomControls ?: return
        
        // Fade out bottom controls in landscape
        bottomControls.animate()
            .alpha(0f)
            .setDuration(transitionDuration)
            .setInterpolator(interpolator)
            .start()
        
        // Slightly fade top controls
        topControls.animate()
            .alpha(0.8f)
            .setDuration(transitionDuration)
            .setInterpolator(interpolator)
            .start()
    }
    
    /**
     * Restore controls for portrait mode
     */
    private fun restoreControlsForPortrait() {
        val topControls = this.topControls ?: return
        val bottomControls = this.bottomControls ?: return
        
        // Restore bottom controls in portrait
        bottomControls.animate()
            .alpha(1f)
            .setDuration(transitionDuration)
            .setInterpolator(interpolator)
            .start()
        
        // Restore full opacity for top controls
        topControls.animate()
            .alpha(1f)
            .setDuration(transitionDuration)
            .setInterpolator(interpolator)
            .start()
    }
    
    /**
     * Start auto-hide timer for landscape mode
     */
    private fun startAutoHideTimer() {
        cancelAutoHideTimer()
        
        autoHideJob = lifecycleOwner.lifecycleScope.launch {
            delay(autoHideDelay)
            
            if (currentOrientation == Orientation.LANDSCAPE && !isTransitioning) {
                hideNonEssentialControls()
            }
        }
    }
    
    /**
     * Cancel auto-hide timer
     */
    private fun cancelAutoHideTimer() {
        autoHideJob?.cancel()
        autoHideJob = null
    }
    
    /**
     * Hide non-essential controls in landscape mode
     */
    private fun hideNonEssentialControls() {
        val topControls = this.topControls ?: return
        
        topControls.animate()
            .alpha(0.3f)
            .setDuration(500)
            .setInterpolator(interpolator)
            .start()
    }
    
    /**
     * Show controls when user interacts with screen
     */
    fun showControlsOnInteraction() {
        if (currentOrientation == Orientation.LANDSCAPE) {
            val topControls = this.topControls ?: return
            
            topControls.animate()
                .alpha(0.8f)
                .setDuration(200)
                .setInterpolator(interpolator)
                .start()
                
            startAutoHideTimer() // Restart auto-hide timer
        }
    }
    
    /**
     * Get current orientation from system configuration
     */
    private fun getCurrentOrientationFromConfig(): Orientation {
        return when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> Orientation.LANDSCAPE
            else -> Orientation.PORTRAIT
        }
    }
    
    /**
     * Set orientation change listener
     */
    fun setOnOrientationChangeListener(listener: (Orientation) -> Unit) {
        orientationChangeListener = listener
    }
    
    /**
     * Set transition state listener
     */
    fun setOnTransitionListener(listener: (Boolean) -> Unit) {
        transitionListener = listener
    }
    
    /**
     * Force orientation change (for testing or manual override)
     */
    fun forceOrientation(orientation: Orientation) {
        if (orientation != currentOrientation && !isTransitioning) {
            targetOrientation = orientation
            triggerLayoutTransition()
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        cancelAutoHideTimer()
        orientationChangeListener = null
        transitionListener = null
    }
}