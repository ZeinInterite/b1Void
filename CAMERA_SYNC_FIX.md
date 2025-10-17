# CameraX Preview/Capture Synchronization Fix

This document describes the fix applied to eliminate preview/capture aspect ratio desynchronization in the Inspector_appVX Android application.

## Problem

When using CameraX, the preview shown on screen and the captured photo can have different aspect ratios and zoom levels, causing:
- Photos to include content not visible in preview (or vice versa)
- Hidden digital zoom being applied
- Inconsistent framing between preview and capture

## Root Causes

1. **No shared ViewPort**: Preview and ImageCapture use cases were using independent crop regions
2. **Different aspect ratios**: Preview and ImageCapture had different target aspect ratios
3. **Missing ScaleType**: PreviewView ScaleType was not explicitly set
4. **Digital zoom not reset**: Camera may have had hidden zoom applied on startup
5. **Surface provider timing**: Preview surface was set before binding, causing potential race conditions

## Solution Applied

### 1. CameraActivity.kt Changes

**Added shared ViewPort with 4:3 aspect ratio:**
```kotlin
val viewPort = ViewPort.Builder(
    android.util.Rational(4, 3),
    currentTargetRotation
).build()

val useCaseGroupBuilder = UseCaseGroup.Builder()
    .setViewPort(viewPort)
```

**Applied AspectRatioStrategy to both Preview and ImageCapture:**
```kotlin
val aspectRatioStrategy = AspectRatioStrategy(
    androidx.camera.core.AspectRatio.RATIO_4_3,
    AspectRatioStrategy.FALLBACK_RULE_AUTO
)

val previewSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(aspectRatioStrategy)
    // ...

val imageCaptureSelector = ResolutionSelector.Builder()
    .setAspectRatioStrategy(aspectRatioStrategy)
    // ...
```

**Set surface provider AFTER binding:**
```kotlin
camera = cameraProvider?.bindToLifecycle(
    this, cameraSelector, useCaseGroupBuilder.build()
)

preview.setSurfaceProvider(previewView.surfaceProvider)
```

**Reset digital zoom to 1.0:**
```kotlin
camera?.cameraControl?.setZoomRatio(1.0f)
```

**Added debug logging:**
```kotlin
Log.d(TAG, "=== CameraX Configuration ===")
Log.d(TAG, "ViewPort: 4:3 aspect ratio, rotation=$currentTargetRotation")
Log.d(TAG, "Preview target resolution: ${previewResolution?.width}x${previewResolution?.height}")
Log.d(TAG, "ImageCapture target resolution: ${captureResolution.width}x${captureResolution.height}")
Log.d(TAG, "PreviewView ScaleType: ${previewView.scaleType}")

// After binding:
Log.d(TAG, "Preview resolved resolution: ${previewRes.width}x${previewRes.height}, aspect ratio: ...")
Log.d(TAG, "ImageCapture resolved resolution: ${captureRes.width}x${captureRes.height}, aspect ratio: ...")
Log.d(TAG, "Initial zoom ratio: ${zoom.zoomRatio}")
```

### 2. activity_camera.xml Changes

**Set explicit ScaleType for PreviewView:**
```xml
<androidx.camera.view.PreviewView
    android:id="@+id/previewView"
    app:scaleType="fitCenter"
    ... />
```

### 3. CameraScreen.kt Changes (Compose UI)

**Fixed surface provider timing:**
```kotlin
camera = provider.bindToLifecycle(
    lifecycleOwner,
    cameraSelector,
    useCaseGroup
)

// Set surface provider AFTER binding
cameraPreview.setSurfaceProvider(previewView.surfaceProvider)
```

**Added debug logging:**
```kotlin
Log.d("CameraScreen", "=== CameraX Configuration ===")
Log.d("CameraScreen", "Preview resolved: ${res.width}x${res.height}, aspect: ...")
Log.d("CameraScreen", "ImageCapture resolved: ${res.width}x${res.height}, aspect: ...")
Log.d("CameraScreen", "Zoom: ${zoom.zoomRatio}")
```

## Quick Checklist to Prevent Desynchronization

Use this checklist when implementing or debugging CameraX camera features:

- [ ] **Create shared ViewPort** with consistent aspect ratio (4:3 recommended)
- [ ] **Use UseCaseGroup** with the ViewPort for all use cases
- [ ] **Set AspectRatioStrategy** to the same value for Preview and ImageCapture
- [ ] **Set PreviewView.ScaleType** to `fitCenter` in XML/code
- [ ] **Reset zoom to 1.0** immediately after camera binding: `camera.cameraControl.setZoomRatio(1.0f)`
- [ ] **Set surface provider AFTER binding**, not before
- [ ] **Add debug logs** to verify resolved resolutions match aspect ratio
- [ ] **Test on multiple devices** with different sensors and screen sizes
- [ ] **Verify aspect ratios** in logs are identical (e.g., both 1.333 for 4:3)

## Verification

To verify the fix is working:

1. Build and run the app
2. Open camera
3. Check logcat for debug messages tagged with `CameraActivity` or `CameraScreen`
4. Verify:
   - Preview and ImageCapture aspect ratios are the same
   - Zoom ratio is 1.0 on startup
   - ScaleType is FIT_CENTER
5. Take a photo and compare with preview - they should match exactly

## Expected Log Output

```
D/CameraActivity: === CameraX Configuration ===
D/CameraActivity: ViewPort: 4:3 aspect ratio, rotation=0
D/CameraActivity: Preview target resolution: 1920x1440
D/CameraActivity: ImageCapture target resolution: 3264x2448
D/CameraActivity: PreviewView ScaleType: FIT_CENTER
D/CameraActivity: AspectRatioStrategy: RATIO_4_3
D/CameraActivity: Preview resolved resolution: 1920x1440, aspect ratio: 1.333
D/CameraActivity: ImageCapture resolved resolution: 3264x2448, aspect ratio: 1.333
D/CameraActivity: Initial zoom ratio: 1.0 (min: 1.0, max: 8.0)
```

## Files Modified

1. `app/src/main/res/layout/activity_camera.xml` - Added `app:scaleType="fitCenter"`
2. `app/src/main/java/com/example/b1void/activities/CameraActivity.kt` - ViewPort, AspectRatioStrategy, zoom reset, debug logs
3. `app/src/main/java/com/example/b1void/ui/camera/CameraScreen.kt` - Fixed surface provider timing, debug logs

## References

- [CameraX ViewPort documentation](https://developer.android.com/training/camerax/preview#viewPort)
- [CameraX AspectRatioStrategy](https://developer.android.com/reference/androidx/camera/core/resolutionselector/AspectRatioStrategy)
- [PreviewView ScaleType](https://developer.android.com/reference/androidx/camera/view/PreviewView.ScaleType)
