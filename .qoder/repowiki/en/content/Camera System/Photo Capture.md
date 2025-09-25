# Photo Capture

<cite>
**Referenced Files in This Document **   
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt)
- [CameraSettingsManager.kt](file://app/src/main/java/com/example/b1void/data/CameraSettingsManager.kt)
- [ResolutionAdapter.kt](file://app/src/main/java/com/example/b1void/adapters/ResolutionAdapter.kt)
</cite>

## Table of Contents
1. [Introduction](#introduction)
2. [ImageCapture Configuration](#imagecapture-configuration)
3. [Photo Capture Pipeline](#photo-capture-pipeline)
4. [Bitmap Processing and Timestamp Overlay](#bitmap-processing-and-timestamp-overlay)
5. [Thumbnail Update Mechanism](#thumbnail-update-mechanism)
6. [Visual Feedback Animation](#visual-feedback-animation)
7. [Error Handling and Permissions](#error-handling-and-permissions)
8. [Performance Considerations](#performance-considerations)

## Introduction
The photo capture feature in CameraActivity implements a comprehensive image acquisition system using Android's CameraX framework. This document details the implementation of the ImageCapture use case, covering configuration parameters, the complete capture pipeline from button press to file storage, bitmap processing with timestamp overlay, thumbnail management, visual feedback animations, error handling, and performance optimization strategies.

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L76-L880)

## ImageCapture Configuration

### Flash Mode Configuration
The ImageCapture use case is configured with dynamic flash mode support through CameraX's flash mode settings. The application supports three flash modes: OFF, ON, and AUTO, which are mapped from user preferences stored in DataStore. The flash mode is set during ImageCapture builder initialization and can be updated dynamically when user settings change.

```mermaid
flowchart TD
A[User Settings] --> B[DataStore]
B --> C[CameraSettingsManager]
C --> D[observeSettings]
D --> E[setFlashMode]
E --> F[ImageCapture.Builder]
F --> G[setFlashMode]
```

**Diagram sources **
- [CameraSettingsManager.kt](file://app/src/main/java/com/example/b1void/data/CameraSettingsManager.kt#L23-L27)
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L274-L306)

### Target Resolution Configuration
The ImageCapture use case is configured with a specific target resolution based on user selection. The application queries available camera resolutions through Camera2 API characteristics and presents them in a resolution selector UI. When a resolution is selected, it is persisted in DataStore and applied to the ImageCapture builder.

```mermaid
flowchart TD
A[Camera Characteristics] --> B[SCALER_STREAM_CONFIGURATION_MAP]
B --> C[getOutputSizes JPEG]
C --> D[Resolution List]
D --> E[ResolutionAdapter]
E --> F[User Selection]
F --> G[setResolution]
G --> H[ImageCapture Builder]
H --> I[setTargetResolution]
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L410-L426)
- [ResolutionAdapter.kt](file://app/src/main/java/com/example/b1void/adapters/ResolutionAdapter.kt#L28-L47)

### Rotation Handling
The ImageCapture use case properly handles device rotation through the OrientationEventListener and Surface rotation constants. The system detects orientation changes and updates the target rotation for all camera use cases (preview, image capture, and video capture) to ensure proper image orientation regardless of device orientation.

```mermaid
flowchart TD
A[Orientation Change] --> B[OrientationEventListener]
B --> C{Degrees}
C --> |45-134| D[ROTATION_270]
C --> |135-224| E[ROTATION_180]
C --> |225-314| F[ROTATION_90]
C --> |Else| G[ROTATION_0]
D --> H[applyTargetRotations]
E --> H
F --> H
G --> H
H --> I[Update Use Cases]
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L602-L627)
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L629-L633)

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L351-L399)

## Photo Capture Pipeline

### Button Press to Capture Initiation
The photo capture process begins when the user presses the capture button in PHOTO mode. The takePhoto() method is triggered, which accesses the ImageCapture instance and initiates the capture process by calling takePicture() with a camera executor and an OnImageCapturedCallback implementation.

```mermaid
sequenceDiagram
participant User
participant CaptureButton
participant CameraActivity
participant ImageCapture
participant Callback
User->>CaptureButton : Tap
CaptureButton->>CameraActivity : onClick()
CameraActivity->>ImageCapture : takePicture()
ImageCapture-->>Callback : onCaptureSuccess or onError
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L672-L707)

### Image Capture Callback Flow
The OnImageCapturedCallback handles both successful captures and errors. On success, the captured ImageProxy is converted to a Bitmap, rotated according to the device orientation, processed with timestamp overlay if enabled, saved to storage, and used to update the thumbnail preview with visual feedback.

```mermaid
flowchart TD
A[onCaptureSuccess] --> B[imageProxyToBitmap]
B --> C{rotationDegrees != 0?}
C --> |Yes| D[Rotate Bitmap]
C --> |No| E[Use Original]
D --> F
E --> F[Apply Timestamp]
F --> G[saveBitmapToFile]
G --> H[updateThumbnail]
H --> I[playCaptureAnimation]
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L672-L707)

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L672-L707)

## Bitmap Processing and Timestamp Overlay

### Bitmap Conversion Process
The captured ImageProxy is converted to a Bitmap by extracting the pixel data from the image planes. The first plane contains the JPEG data, which is copied to a byte array and decoded using BitmapFactory.

```mermaid
flowchart TD
A[ImageProxy] --> B[Get Plane 0]
B --> C[Get Buffer]
C --> D[Create ByteArray]
D --> E[Copy Buffer Data]
E --> F[decodeByteArray]
F --> G[Bitmap]
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L709-L715)

### Timestamp Overlay Implementation
The timestamp overlay is implemented using Android's Canvas and Paint classes. The system creates a mutable copy of the original bitmap, sets up a red-colored Paint object with anti-aliasing, formats the current date and time, and draws the text in the bottom-right corner with appropriate padding.

```mermaid
flowchart TD
A[Original Bitmap] --> B[copy ARGB_8888]
B --> C[Create Canvas]
C --> D[Configure Paint]
D --> E[Set Color RED]
E --> F[Set Text Size]
F --> G[Enable AntiAlias]
G --> H[Format Timestamp]
H --> I[Calculate Position]
I --> J[drawText]
J --> K[Return Modified Bitmap]
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L717-L737)

### JPEG Compression and File Saving
The processed bitmap is saved as a JPEG file with 95% quality compression. The file is named with a timestamp prefix and saved to the designated storage directory, which is either provided via intent extra or defaults to external media directories.

```mermaid
flowchart TD
A[Processed Bitmap] --> B[FileOutputStream]
B --> C[compress JPEG 95%]
C --> D[Write to File]
D --> E[IMG_timestamp.jpg]
E --> F[Return File Object]
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L739-L746)

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L717-L746)

## Thumbnail Update Mechanism

### Glide Integration for Thumbnail Loading
The application uses Glide library to efficiently load and display thumbnails. After a photo is captured and saved, the thumbnail preview is updated by loading the newly created image file URI into the ImageView with circle cropping transformation.

```mermaid
flowchart TD
A[New Image Saved] --> B[Get File URI]
B --> C[Glide.with Context]
C --> D[load URI]
D --> E[circleCrop Transform]
E --> F[into thumbnailPreview]
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L786-L793)

### Thumbnail Click Handling
The thumbnail preview is clickable, allowing users to view their recently captured images. When clicked, the application checks if the last saved file exists and launches the ImagePreviewActivity with the appropriate image path and index.

```mermaid
flowchart TD
A[Thumbnail Click] --> B[Check lastSavedFile]
B --> C{File Exists?}
C --> |Yes| D[isImageFile Check]
D --> E[Get All Images]
E --> F[Find Current Index]
F --> G[Launch ImagePreviewActivity]
C --> |No| H[Do Nothing]
```

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L240-L268)

## Visual Feedback Animation

### Capture Animation Sequence
The visual feedback animation provides immediate confirmation of photo capture by animating the captured image from the center of the screen to the thumbnail preview location. The animation consists of two phases: an initial scale-up and fade-in, followed by a translation to the thumbnail position with scaling down and fade-out.

```mermaid
flowchart TD
A[Start Animation] --> B[Load Image to captureAnimationView]
B --> C[Set Initial State]
C --> D[Scale 0.6, Alpha 0]
D --> E[Animate Scale 1.0, Alpha 1.0]
E --> F[Wait 140ms]
F --> G[Animate Translation]
G --> H[Move to Thumbnail Position]
H --> I[Scale 0.3, Alpha 0]
I --> J[Wait 280ms]
J --> K[Reset View]
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L547-L600)

### Animation Interpolation
The animation uses different interpolators for each phase to create a natural feel. The initial appearance uses DecelerateInterpolator for a smooth start, while the movement phase uses AccelerateInterpolator for a quick finish, mimicking real-world physics.

```mermaid
classDiagram
class playCaptureAnimation {
+captureAnimationView ImageView
+DecelerateInterpolator
+AccelerateInterpolator
+startLocation int[2]
+endLocation int[2]
+deltaX float
+deltaY float
+playCaptureAnimation(uri Uri) void
}
class AnimationInterpolators {
+DecelerateInterpolator
+AccelerateInterpolator
}
playCaptureAnimation --> AnimationInterpolators : "uses"
```

**Diagram sources **
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L547-L600)

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L547-L600)

## Error Handling and Permissions

### Capture Failure Handling
The application implements robust error handling for photo capture failures through the OnImageCapturedCallback's onError method. Any capture exceptions are logged with error level, providing diagnostic information while maintaining application stability.

```mermaid
flowchart TD
A[onError] --> B[Log Exception]
B --> C[Log.e TAG]
C --> D[Include Message]
D --> E[Include Stack Trace]
E --> F[Continue Operation]
```

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L672-L707)

### Permission Requirements
The photo capture feature requires CAMERA and RECORD_AUDIO permissions, with the latter needed due to shared components with video recording functionality. The application checks for these permissions on startup and requests them if not granted, terminating gracefully if permissions are denied.

```mermaid
flowchart TD
A[onCreate] --> B[allPermissionsGranted]
B --> C{Permissions Granted?}
C --> |Yes| D[Start Camera]
C --> |No| E[Request Permissions]
E --> F{onRequestPermissionsResult}
F --> G{All Granted?}
G --> |Yes| D
G --> |No| H[Show Toast]
H --> I[Finish Activity]
```

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L150-L158)

## Performance Considerations

### Memory Management During Bitmap Processing
The application manages memory carefully during bitmap processing by using efficient conversion methods and ensuring proper resource cleanup. The ImageProxy is closed after conversion to free native resources, and bitmaps are processed in-place when possible to minimize memory allocation.

```mermaid
flowchart TD
A[ImageProxy] --> B[Convert to Bitmap]
B --> C[Process Bitmap]
C --> D[Close ImageProxy]
D --> E[Save File]
E --> F[Release Bitmap References]
```

### Storage Optimization
The application optimizes storage usage through JPEG compression at 95% quality, which provides excellent visual quality while significantly reducing file size compared to lossless formats. Files are named with timestamps to ensure uniqueness without requiring additional metadata storage.

```mermaid
flowchart TD
A[Bitmap] --> B[compress JPEG]
B --> C[Quality 95%]
C --> D[Optimal Size/Quality]
D --> E[File Output]
E --> F[IMG_timestamp.jpg]
```

**Section sources**
- [CameraActivity.kt](file://app/src/main/java/com/example/b1void/activities/CameraActivity.kt#L739-L746)