# FIX PLAN: Image Aspect Ratio Distortion in Warehouse Preview

## 1. Summary

**Issue:** Photos in the preview screen are stretched/distorted, compromising the accuracy of warehouse inspections (e.g., distinguishing between round and oval deformation, reading labels at edges).

**Root Cause Analysis:**
Initial analysis suggested that a missing `fitCenter()` call in Glide or an incorrect `scaleType` in the XML was the problem. **This is incorrect.** The layout (`item_image_fullscreen.xml`) and Glide call in `ImagePagerAdapter.kt` already use `fitCenter`.

The true root cause is the `.override(1920, 1440)` call within the Glide chain in `ImagePagerAdapter.kt`. This forces every loaded bitmap into a fixed 4:3 aspect ratio, causing significant distortion for any photo not taken in that exact ratio (e.g., modern widescreen phone photos).

**Goal:** Remove the `.override()` call to allow Glide's `fitCenter()` to work correctly, preserving the original aspect ratio of the photo.

## 2. Affected Files

*   **Kotlin Logic:** `app/src/main/java/com/example/b1void/adapters/ImagePagerAdapter.kt`
*   **UI Layout:** `app/src/main/res/layout/item_image_fullscreen.xml` (No changes needed, but verified as correct).

## 3. Step-by-Step Fix

### Step 1: Verify XML Layout (Already Correct)

The layout file `app/src/main/res/layout/item_image_fullscreen.xml` correctly uses:
```xml
<io.getstream.photoview.PhotoView
    android:id="@+id/image_view"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:scaleType="fitCenter"
    android:adjustViewBounds="true" />
```
The `scaleType="fitCenter"` and `adjustViewBounds="true"` settings are correct for preserving aspect ratio. No changes are needed here.

### Step 2: Correct Glide Loading Logic

The critical fix is to remove the distorting resize operation.

*   **File:** `app/src/main/java/com/example/b1void/adapters/ImagePagerAdapter.kt`
*   **Location:** `ImageViewHolder.bind()` function.

**ACTION:** Modify the Glide chain to remove the `.override()` call.

**REPLACE THIS:**
```kotlin
Glide.with(context)
    .load(imageFile)
    .override(1920, 1440) // CRITICAL BUG: This distorts the aspect ratio
    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
    .fitCenter()
    .placeholder(R.drawable.image_ic)
    .error(R.drawable.def_insp_img)
    .into(imageView)
```

**WITH THIS:**
```kotlin
Glide.with(context)
    .load(imageFile)
    // .override(1920, 1440) // REMOVED to prevent aspect ratio distortion
    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
    .fitCenter() // This can now work correctly
    .placeholder(R.drawable.image_ic)
    .error(R.drawable.def_insp_img)
    .into(imageView)
```
*Note: The `.diskCacheStrategy(DiskCacheStrategy.RESOURCE)` is also a good candidate for review. Caching the original, full-resolution file might be better (`DiskCacheStrategy.DATA`) and then letting Glide handle downsampling dynamically. However, for this specific bug fix, only removing `.override()` is required.*

## 4. Verification (Warehouse Context)

Rebuild and test with a photo that is **NOT** 4:3 (e.g., a standard 16:9 phone picture).

1.  **Geometric Test:** Photograph a circular object (e.g., a roll of tape). In the preview, it **must** remain a perfect circle, not an oval.
2.  **Edge Test:** Photograph a document filling the frame. Ensure no edges are cropped.
3.  **Black Bars:** Confirm that "letterboxing" (black/empty bars) appears either on the sides or top/bottom. This is the **expected and correct** behavior, proving the aspect ratio is preserved.

## 5. Prevention

*   **Rule for Image Loading:** Never use `Glide.override()` with fixed dimensions unless you are explicitly cropping to a specific, required aspect ratio (e.g., for a circular profile picture thumbnail). For displaying evidential photos, always omit it.
*   **Code Review Standard:** Any PR modifying an image loading call should be scrutinized for hardcoded `override()` dimensions, as it is a common source of distortion bugs.
---

# FIX PLAN 2: Camera EV Initialization Robustness

## 1. Summary

**Issue:** The `CameraViewModel` has a fragile initialization process for the Exposure Value (EV) range, which can lead to a silent failure mode and a confusing user experience.

**Root Cause Analysis:**
The `initializeEvRange()` function in `CameraViewModel.kt` is responsible for fetching the camera's supported EV range. It has several flaws:
1.  **Silent Failure:** If fetching the range fails (e.g., camera not ready, unsupported operation), it defaults to a hardcoded range (`-2.0f..2.0f`) without ever notifying the user or the UI layer. The user might interact with a slider that doesn't work correctly.
2.  **Race Condition Prone:** The function relies on being called at the "right time" from the UI layer, which is unreliable and can lead to it being called before the camera is available.
3.  **No Loading State:** The UI has no way of knowing that the EV range is being fetched asynchronously. It shows a default range, which might suddenly change after a delay, creating a UI "jump".

**Goal:** Make the EV range initialization process robust, state-driven, and transparent to the user by eliminating silent failures and race conditions.

## 2. Affected Files

*   **ViewModel Logic:** `feature/camera/src/main/java/com/example/b1void/camera/ui/camera/CameraViewModel.kt`

## 3. Step-by-Step Fix

### Step 1: Introduce a UI State Data Class

Create a dedicated `data class` to represent all possible states of the camera UI, including loading and error states for the EV range.

**ACTION:** Add a new data class, for example `CameraUiState`.

```kotlin
data class CameraUiState(
    val evRange: ClosedFloatingPointRange<Float> = -2.0f..2.0f,
    val isInitializing: Boolean = true,
    val initializationError: Throwable? = null
)
```

### Step 2: Refactor CameraViewModel to Use the UI State

Modify `CameraViewModel.kt` to use this new state holder.

**REPLACE THIS:**
```kotlin
// EV Range state - получаем из camera после инициализации
private val _evRange = MutableStateFlow(-2.0f..2.0f)
val evRange: StateFlow<ClosedFloatingPointRange<Float>> = _evRange.asStateFlow()

// ...

fun initializeEvRange() {
    viewModelScope.launch {
        try {
            val range = focusInteractor.getEvRange()
            _evRange.value = range
        } catch (e: Exception) {
            // В случае ошибки используем значения по умолчанию
            _evRange.value = -2.0f..2.0f
        }
    }
}
```

**WITH THIS (Conceptual):**

```kotlin
private val _uiState = MutableStateFlow(CameraUiState())
val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

// This should be triggered by a signal that the camera is ready,
// not called imperatively from the UI.
// For example, collecting a state from a lower layer.
// A simple alternative is an init block if the interactor is ready on creation.
init {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isInitializing = true)
        try {
            val range = focusInteractor.getEvRange()
            _uiState.value = _uiState.value.copy(evRange = range, isInitializing = false)
        } catch (e: Exception) {
            // Now the failure is explicitly part of the state
            _uiState.value = _uiState.value.copy(initializationError = e, isInitializing = false)
        }
    }
}

// The old public method should be removed to prevent race conditions.
// fun initializeEvRange() { ... } // REMOVE
```

*Note: The `init` block is a simple way to trigger this, but a more advanced solution would involve the `focusInteractor` exposing a `cameraReady` StateFlow that the ViewModel collects.*

## 4. Verification

1.  **Success Path:** Rebuild and run. The EV slider should correctly reflect the device's capabilities.
2.  **Loading State:** (May require adding a `delay()` in the `try` block for testing). Verify the UI can listen to `isInitializing` and show a loading indicator.
3.  **Failure Path:** To simulate an error, modify `focusInteractor.getEvRange()` to throw an exception.
    *   **Expected:** The UI should react to the `initializationError` state. It could show a toast message like "Could not get camera settings", and the EV slider should be disabled or hidden.
    *   **Verify:** The app no longer silently fails with a non-functional slider.

## 5. Prevention

*   **ViewModel Best Practice:** Avoid "fire-and-forget" functions that silently handle errors. Expose errors as part of the view state so the UI can react appropriately.
*   **State-Driven Initialization:** Don't rely on imperative calls like `initialize()` from the UI. Initialization should be a deterministic, observable reaction to dependencies (like the ViewModel's `init` block) or state changes in a lower layer.
---

# FIX PLAN 3: FocusInteractor Architecture and Threading

## 1. Summary

**Issue:** The `FocusInteractor` in the camera feature has critical architectural flaws that can lead to UI freezes (ANRs), are hard to maintain, and are not type-safe.

**Root Cause Analysis:**
1.  **UI Thread Blocking:** The interactor's default `CoroutineScope` is hardcoded to `Dispatchers.Main`. Several functions launch coroutines on this scope to perform potentially long-running repository operations (interacting with camera hardware), which will block the main thread and cause the application to freeze.
2.  **Hardcoded Device-Specific Hacks:** The logic is littered with `isXiaomiDevice()` checks and other "hotfixes" for specific hardware. This makes the code brittle, difficult to maintain, and non-extensible for other devices with similar quirks. This logic does not belong in a domain-layer interactor.
3.  **String-Based Events:** The interactor emits events as raw strings (e.g., `"tap_focus"`), which is not type-safe and makes consuming these events error-prone.

**Goal:** Refactor `FocusInteractor` to be a robust, maintainable, and thread-safe domain component by correcting the threading model, abstracting device-specific logic, and using type-safe events.

## 2. Affected Files

*   **Domain Logic:** `feature/camera/src/main/java/com/example/b1void/camera/domain/camera/FocusInteractor.kt`
*   **Dependency Injection / Initialization code:** The code responsible for creating `FocusInteractor` will need to be updated to inject a proper coroutine scope.

## 3. Step-by-Step Fix

### Step 1: Fix Threading Model

Inject a dispatcher and use a background-threaded scope.

**REPLACE THIS:**
```kotlin
class FocusInteractor(
    private val repo: FocusRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main) // BUG: Hardcoded to Main thread
)
```

**WITH THIS:**
```kotlin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class FocusInteractor(
    private val repo: FocusRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default // Inject dispatcher
) {
    // Create scope with the injected dispatcher
    private val scope = CoroutineScope(defaultDispatcher)

    // ... rest of the class
```
This ensures that work is launched on a background thread by default, preventing UI freezes. The dispatcher can be replaced with `Dispatchers.Unconfined` or a `TestDispatcher` in unit tests.

### Step 2: Abstract Device-Specific Hacks

Move the Xiaomi-specific logic out of the interactor and into a lower-level component.

**ACTION (Conceptual):**
1.  Create a `DeviceQuirksProvider` interface and its implementation. This class will be responsible for providing device-specific configurations.
    ```kotlin
    interface DeviceQuirksProvider {
        fun getClampedEv(evValue: Float): Float
        fun requiresNearZeroEvWorkaround(): Boolean
    }
    ```
2.  Inject this provider into the `FocusRepository` (not the `FocusInteractor`).
3.  The `FocusRepository` will then use this provider to apply workarounds before sending values to the camera HAL.
4.  Remove all `isXiaomiDevice()` and related hotfix logic from `FocusInteractor`. The `setAbsoluteEv` function should be simplified to only do basic validation.

**REFACTORED `setAbsoluteEv` in `FocusInteractor`:**
```kotlin
fun setAbsoluteEv(evValue: Float) {
    scope.launch {
        if (!evValue.isFinite()) {
            // Log error or emit an event
            return@launch
        }
        repo.setAbsoluteEv(evValue) // Now clean of device-specific logic
    }
}
```

### Step 3: Implement Type-Safe Events

Replace the string-based `SharedFlow` with a `sealed class` for events.

**REPLACE THIS:**
```kotlin
private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)
```

**WITH THIS:**
```kotlin
sealed class CameraInteractionEvent {
    object TapFocus : CameraInteractionEvent()
    object AeLockToggle : CameraInteractionEvent()
    object AeUnlock : CameraInteractionEvent()
    data class XiaomiEvClamped(val original: Float, val clamped: Float) : CameraInteractionEvent()
}

// ... in the class ...
private val _events = MutableSharedFlow<CameraInteractionEvent>(extraBufferCapacity = 32)
val events: SharedFlow<CameraInteractionEvent> = _events
```
Update all `_events.tryEmit("...")` calls to use the new type-safe events (e.g., `_events.tryEmit(CameraInteractionEvent.TapFocus)`).

## 4. Verification

1.  **ANR Test:** Verify the app remains responsive while performing camera operations like adjusting EV. (This can be hard to test without a slow device, but the code change prevents the possibility).
2.  **Xiaomi Logic:** Test on a Xiaomi device to confirm the EV clamping behavior is still working correctly after being moved to the repository layer.
3.  **Event Consumption:** Verify that the UI or other components consuming the `events` flow are updated to use the new `sealed class` and can still react to events correctly.

## 5. Prevention

*   **Domain Layer Purity:** Domain-layer components like interactors should not be aware of Android-specific implementation details like `Build.MANUFACTURER` or `Dispatchers.Main`. Their only job is to orchestrate business logic.
*   **Dependency Injection:** Always inject dependencies, including `CoroutineScope` or `CoroutineDispatcher`, to improve testability and prevent hardcoded behavior.
*   **Type-Safe Communication:** Use `sealed class` or `enum` for events passing between layers to prevent runtime errors from typos in strings.
---

# FIX PLAN 4: CameraX Controller Cleanup and Refactoring

## 1. Summary

**Issue:** The `CameraXFocusController` is a critical component that contains a mixture of dead code, threading issues, race conditions, and overly complex logic, making it a significant source of bugs and technical debt.

**Root Cause Analysis:**
1.  **Abandoned/Dead Code:** Large, complex methods for applying manufacturer-specific optimizations (`applyManufacturerOptimizations`, `applyExposureOptimizations`, `logCameraCapabilities`) are completely disabled with `return` statements. This indicates an incomplete or broken feature that was left in the codebase, creating confusion and noise.
2.  **Incorrect Threading and Race Conditions:** The class defaults to a `Dispatchers.Main` coroutine scope, risking UI freezes. Furthermore, the `performFocus` method uses an error-prone `postDelayed` call to implement a timeout, creating a race condition with the `Future`'s listener.
3.  **Overly Complex and Brittle Logic:** The `performFocus` method is monolithic. It manually handles retries by launching new coroutines and manages torch state with repetitive `try/catch` blocks in every exit path, making it hard to read and prone to errors.
4.  **Incomplete Features:** The `enableMacro` function contains a `TODO` comment, indicating an unimplemented feature.

**Goal:** Aggressively refactor `CameraXFocusController` to remove dead code, fix threading and race conditions, simplify complex logic, and clearly delineate incomplete features.

## 2. Affected Files

*   **CameraX Implementation:** `feature/camera/src/main/java/com/example/b1void/camera/data/camera/camerax/CameraXFocusController.kt`

## 3. Step-by-Step Fix

### Step 1: Remove Dead and Disabled Code

The disabled manufacturer-specific code is the biggest problem. It should be removed entirely. If this feature is ever revived, it should be done in a separate, maintainable module, not via reflection hacks.

**ACTION:**
1.  Delete the methods: `applyManufacturerOptimizations`, `applyExposureOptimizations`, and `logCameraCapabilities`.
2.  Remove the `manufacturerOptimizationsApplied` property.
3.  Remove the commented-out call in the `init` block.

This will immediately reduce the file's complexity by over 100 lines and remove the misleading, non-functional logic.

### Step 2: Fix Threading and Timeouts

Replace the manual timeout and main-thread scope with modern, idiomatic coroutine patterns.

**ACTION:**
1.  **Inject Dispatcher**: Change the constructor to accept a `CoroutineDispatcher` (defaulting to `Dispatchers.IO`) instead of a `CoroutineScope`, and create the internal scope with it.
    ```kotlin
    class CameraXFocusController(
        // ...
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) {
        private val scope = CoroutineScope(ioDispatcher)
        // ...
    }
    ```
2.  **Refactor `performFocus` Timeout**: Replace the `postDelayed`/`Future.addListener` race condition with `withTimeoutOrNull` and Kotlin coroutine-guava interop (`await()`).

**REPLACE THIS (Conceptual):**
```kotlin
// ... in performFocus
val future = camera.cameraControl.startFocusAndMetering(action)
val resolved = AtomicBoolean(false)
previewView.postDelayed({
    if (resolved.compareAndSet(false, true)) {
        // timeout logic
    }
}, 3000)
future.addListener({
    try {
        val r = future.get()
        if (resolved.compareAndSet(false, true)) {
            // success/fail logic
        }
    } catch (t: Throwable) {
        // error logic
    }
}, mainExecutor)
```

**WITH THIS:**
```kotlin
import androidx.concurrent.futures.await
import kotlinx.coroutines.withTimeoutOrNull

// ... in performFocus, launched within the class's background scope
scope.launch {
    try {
        val result = withTimeoutOrNull(3000) { // 3-second timeout
            camera.cameraControl.startFocusAndMetering(action).await()
        }

        if (result == null) {
            // Timeout occurred
            _state.update { FocusState.Failed("timeout") }
            // ... retry logic if needed
        } else if (result.isFocusSuccessful) {
            // Success
            _state.update { if (lock) FocusState.Locked(x, y) else FocusState.Idle() }
        } else {
            // Failure
            _state.update { FocusState.Failed("af_fail") }
            // ... retry logic if needed
        }
    } catch (e: Exception) {
        // Exception from .await()
        _state.update { FocusState.Failed(e.message) }
    } finally {
        // Clean up torch
    }
}
```

### Step 3: Simplify `performFocus` Logic

Refactor the torch control and retry logic to be cleaner and more robust.

**ACTION:**
1.  **Use `try-finally` for Torch**: Wrap the focus logic in a `try-finally` block to ensure the torch is always turned off.
2.  **Simplify Retry Logic**: Instead of launching a new coroutine for a retry, use a simple loop or a dedicated retry function.

**REFACTORED `performFocus` (Conceptual):**
```kotlin
private fun performFocus(x: Float, y: Float, lock: Boolean, retries: Int) {
    scope.launch {
        var attempt = 0
        var success = false
        while (attempt <= retries && !success) {
            try {
                if (torchAssist && lock) camera.cameraControl.enableTorch(true)

                val result = withTimeoutOrNull(3000) { /* ... await focus future ... */ }
                
                if (result?.isFocusSuccessful == true) {
                    success = true
                    // update state to success
                } else {
                    attempt++
                    // update state to failed, log attempt
                }
            } catch (e: Exception) {
                attempt++
                // update state to failed, log exception
            } finally {
                if (torchAssist && lock) camera.cameraControl.enableTorch(false)
            }
        }
    }
}
```

## 4. Verification

1.  **Functionality:** Confirm that tap-to-focus, long-press-to-lock, and EV adjustment still work as expected after the refactoring.
2.  **Timeout Test:** Manually block the future's result (if possible in a test environment) or add a long delay to confirm the `withTimeoutOrNull` path is correctly taken and the UI updates to a "Failed (timeout)" state.
3.  **Torch Test:** Confirm the torch correctly turns on for focus assist during a long-press lock and, critically, always turns off afterward, even if focusing fails or times out.

## 5. Prevention

*   **No Dead Code:** Don't leave commented-out or disabled code blocks in the main source. Use version control (e.g., `git`) to manage old code. If code is experimental, put it on a feature branch, not disabled on the main branch.
*   **Embrace Coroutines:** When working in a coroutine-based architecture, avoid mixing in legacy asynchronous patterns like raw `Future` listeners and `postDelayed`. Use coroutine wrappers (`.await()`) and structured concurrency features (`withTimeoutOrNull`).
*   **Single Responsibility:** A method should do one thing. `performFocus` was trying to do everything: metering, timeout, retry, and torch management. Breaking these concerns into smaller, testable functions makes the code more robust.
---

# FIX PLAN 5: Application Initialization and Security

## 1. Summary

**Issue:** The main `B1VoidApplication.kt` class contains several critical initialization flaws, including a severe security risk, and anti-patterns that contradict the project's established Hilt architecture.

**Root Cause Analysis:**
1.  **Hardcoded Secrets (CRITICAL SECURITY RISK):** The line `DropboxClientFactory.init("YOUR_ACCESS_TOKEN")` presents a severe security vulnerability. Although it's a placeholder now, a developer could easily commit a live token, exposing it in the git history. Secrets must never be stored in source code.
2.  **Service Locator Anti-Pattern:** The app uses static `init()` methods (`DropboxClientFactory.init(...)`, `AppSettingsBootstrap.init(...)`) to initialize and provide global dependencies. This is a Service Locator pattern, which is widely considered an anti-pattern because it creates implicit, hidden dependencies and makes testing extremely difficult. It directly conflicts with the project's use of Hilt for proper dependency injection.
3.  **Improper Glide Initialization:** Glide is configured via a manual `Glide.init()` call inside the `Application` class. The idiomatic and more robust method is to create an `AppGlideModule` implementation. This ensures Glide is configured correctly and consistently everywhere in the app, including in library modules, and it avoids potential issues with initialization order.

**Goal:** Refactor the application's initialization sequence to eliminate the security vulnerability, align with modern dependency injection principles using Hilt, and follow best practices for library configuration.

## 2. Affected Files

*   **Application Class:** `app/src/main/java/com/example/b1void/B1VoidApplication.kt`
*   **Utility/Factory classes:** `app/src/main/java/com/example/b1void/utils/DropboxClientFactory.kt`, `app/src/main/java/com/example/b1void/data/AppSettingsBootstrap.kt` (and any other classes using this pattern).
*   **DI Modules:** A new Hilt module will need to be created.

## 3. Step-by-Step Fix

### Step 1: Eliminate Hardcoded Secrets

The Dropbox token must be removed from the source code immediately.

**ACTION:**
1.  Add `secrets.properties` to the project's root and add `secrets.properties` to the `.gitignore` file.
2.  Inside `secrets.properties`, add the line: `DROPBOX_ACCESS_TOKEN="YOUR_REAL_TOKEN_HERE"`
3.  In the `app/build.gradle.kts` file, read this property and expose it as a `BuildConfig` field.
    ```kotlin
    // In app/build.gradle.kts
    val secretsFile = rootProject.file("secrets.properties")
    val properties = java.util.Properties()
    if (secretsFile.exists()) {
        properties.load(java.io.FileInputStream(secretsFile))
    }

    android {
        //... 
        defaultConfig {
            //... 
            buildConfigField("String", "DROPBOX_ACCESS_TOKEN", "\"${properties.getProperty("DROPBOX_ACCESS_TOKEN", "")}\"")
        }
    }
    ```
4.  In the code, access the token via `BuildConfig.DROPBOX_ACCESS_TOKEN`.

### Step 2: Refactor Static Initializers to Hilt Modules

Convert the Service Locators into proper, injectable dependencies managed by Hilt.

**ACTION:**
1.  **Refactor `DropboxClientFactory`**:
    -   Remove the static `init` method and the static instance.
    -   Create a Hilt module (e.g., `CloudModule.kt`) to provide the Dropbox client instance.
    ```kotlin
    // In app/src/main/java/com/example/b1void/di/CloudModule.kt
    @Module
    @InstallIn(SingletonComponent::class)
    object CloudModule {
        @Provides
        @Singleton
        fun provideDropboxClient(): DbxClientV2 {
            // Access the token securely from BuildConfig
            val accessToken = com.example.b1void.BuildConfig.DROPBOX_ACCESS_TOKEN
            val requestConfig = com.dropbox.core.v2.DbxRequestConfig("b1void/1.0")
            return com.dropbox.core.v2.DbxClientV2(requestConfig, accessToken)
        }
    }
    ```
    -   Remove the `DropboxClientFactory.init()` call from `B1VoidApplication`.
    -   Inject the `DbxClientV2` instance wherever it's needed using `@Inject`.

2.  **Refactor `AppSettingsBootstrap`**:
    -   Convert it into a class that can be injected by Hilt.
    -   In `B1VoidApplication`, inject and call it.
    ```kotlin
    @HiltAndroidApp
    class B1VoidApplication : Application() {
        @Inject lateinit var appSettingsBootstrap: AppSettingsBootstrap

        override fun onCreate() {
            super.onCreate()
            appSettingsBootstrap.init(this)
            // ...
        }
    }
    ```
    This makes the dependency explicit and testable.

### Step 3: Convert Manual Glide Setup to `AppGlideModule`

Follow Glide's official best practice for configuration.

**ACTION:**
1.  Create a new class `AppGlideModule.kt` in the `app` module.
2.  Annotate it with `@GlideModule`.
3.  Move the logic from `setupOptimizedGlideConfiguration` into the `applyOptions` method of this new module.
    ```kotlin
    @com.bumptech.glide.annotation.GlideModule
    class AppGlideModule : com.bumptech.glide.module.AppGlideModule() {
        override fun applyOptions(context: Context, builder: GlideBuilder) {
            // All the logic from setupOptimizedGlideConfiguration goes here
            // e.g., val calculator = MemorySizeCalculator.Builder(context)...
            // builder.setMemoryCache(...)
            // builder.setDiskCache(...)
        }
        // You can also override isManifestParsingEnabled to false
        override fun isManifestParsingEnabled() = false
    }
    ```
4.  Delete the `setupOptimizedGlideConfiguration()` method and its call from `B1VoidApplication`. Glide's annotation processor will find and use this module automatically.

## 4. Verification

1.  **Dropbox:** Confirm that Dropbox features still work after refactoring. Verify that the app crashes or fails gracefully if the `DROPBOX_ACCESS_TOKEN` is missing from `BuildConfig`.
2.  **App Startup:** Ensure the app still starts correctly and that settings are bootstrapped as expected.
3.  **Image Loading:** Verify that images throughout the app continue to load correctly, confirming that the new `AppGlideModule` is being used. Check logs to see the "Glide configured" message (if you move it to the module).

## 5. Prevention

*   **Secrets Management:** Enforce a strict "no secrets in code" policy. Use a combination of `.gitignore` and `BuildConfig` fields or a more robust secrets management tool.
*   **Favor DI over Service Locators:** For any new dependency, use Hilt to `@Inject` it. Avoid static singletons and `init()` methods.
*   **Follow Library Best Practices:** When integrating a major library like Glide, consult its documentation for the recommended setup (e.g., using `AppGlideModule`) rather than implementing a custom solution.
