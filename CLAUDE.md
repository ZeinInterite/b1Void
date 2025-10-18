# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Inspector_appVX is an Android file management application designed for surveyors and warehouse inspectors conducting product expertise. The app focuses on organizing, viewing, and basic editing of photos taken in warehouses, with support for categories, labels, quick search, and user-friendly interface for users wearing gloves with limited time.

## Build and Development Commands

**Build Commands:**
- `./gradlew assembleDebug` (Windows: `./gradlew.bat assembleDebug`) - Build debug APK
- `./gradlew assembleRelease` - Build release APK with signing configuration
- `./gradlew clean` - Clear build outputs when facing Gradle cache issues

**Quality Assurance:**
- `./gradlew lint` - Execute Android lint analysis (resolve warnings before merging)
- `./gradlew test` - Run JVM unit tests for all modules
- `./gradlew :feature:camera:test` - Run tests for camera module specifically
- `./gradlew connectedAndroidTest` - Run instrumentation tests (requires connected device)

**Note:** Do not use `./gradlew.bat compileDebugKotlin` - use the full build commands above.

## Architecture Overview

**Project Structure:**
This is a multi-module Android project:
- `:app` - Main application module with activities, adapters, legacy code
- `:feature:camera` - Modular camera feature using Hilt DI with clean architecture (data/domain/ui layers)

**Technology Stack:**
- **Language:** Kotlin 2.1 (primary) with some Java legacy code
- **UI:** Jetpack Compose + View Binding, Material 3 Design
- **Camera:** CameraX 1.3.1 for photo/video capture with custom controls
- **DI (camera module only):** Hilt 2.52 with KSP compiler
- **Storage:** External storage with FileProvider, Dropbox integration
- **Image Processing:** Glide 4.16.0 with device-adaptive optimization
- **Background Work:** WorkManager for file operations
- **Testing:** JUnit + Espresso for instrumentation tests

**Key Application Structure:**
- **Application Class:** `B1VoidApplication.kt` - Initializes Glide with adaptive memory configuration based on device RAM, MultiDex, WorkManager
- **Main Activities:**
  - `MainActivity.java` - Entry point with Dropbox authentication setup
  - `CameraActivity.kt` / `CameraComposeActivity.kt` - Camera interfaces (legacy View + new Compose)
  - `FileManagerActivity.kt` - File browser with grid layout, selection, move operations
  - `ImagePreviewActivity.kt` - Full-screen image viewer with editing capabilities
  - `NavigationApp.kt` - Main navigation hub

**Camera Module Architecture (`:feature:camera`):**
The camera module uses clean architecture with three layers:
- **Data Layer:** `Camera2FocusController`, `CameraXFocusController`, `FocusRepository` - Hardware abstraction
- **Domain Layer:** `FocusInteractor` - Business logic for focus, exposure, and stabilization
- **UI Layer:** `CameraViewModel`, `FocusOverlayView` - Compose UI and state management
- **DI:** Hilt modules in `di/FocusModule.kt` provide dependency injection

**Main App Data Layer:**
- `data/CameraSettingsManager.kt` - Camera configuration persistence using DataStore
- `data/AppSettingsBootstrap.kt` / `AppSettingsCache.kt` - App-wide settings initialization
- `data/FolderRepository.kt` - File system operations
- `models/` - Data classes for Inspection, Inspector entities (some Java, some Kotlin)
- `utils/` - File operations, image optimization, memory management, device compatibility

**Key Features:**
- Custom camera with manual controls (exposure, ISO, focus) and OIS/EIS stabilization
- Video recording with timestamp overlays and quality settings
- Batch file operations and cloud sync via Dropbox
- Device-adaptive memory management (RAM class detection, low-end device optimization)
- File sharing with ZIP archive creation

## Development Guidelines

**Code Style:**
- Follow Kotlin style guide: 4-space indentation, camelCase for members, PascalCase for classes
- Android resources use snake_case (`activity_main.xml`)
- Package organization is feature-based under `com.example.b1void`
- View binding is enabled - avoid synthetic imports
- Prefer Kotlin for new features; Java code exists for legacy compatibility only

**Dependency Injection:**
- Hilt is only configured in `:feature:camera` module with KSP
- Main `:app` module does NOT use Hilt - uses manual DI and factories (e.g., `DropboxClientFactory`)
- Do not add Hilt to the app module without architectural discussion

**Security Configuration:**
- Release signing expects `app/release.keystore` with hardcoded credentials (development only)
- Dropbox ACCESS_TOKEN configured in `B1VoidApplication.kt` (replace "YOUR_ACCESS_TOKEN")
- API keys and secrets go in `local.properties` or secure vaults
- Firebase config in `app/google-services.json` - scrub personal data before sharing

**Testing:**
- Unit tests in `src/test/` with `Test` suffix (e.g., `FocusCoordinatorTest.kt`)
- Instrumentation tests in `androidTest/` directory
- Camera module has dedicated focus tests - run with `./gradlew :feature:camera:test`
- Target >80% coverage on new code

**Memory Optimization:**
- `B1VoidApplication` detects device RAM class and configures Glide accordingly:
  - Low RAM devices: 0.5x memory multiplier, 25MB disk cache, RGB_565 format
  - Mid-range (128MB heap): 1.0x multiplier, 50MB cache
  - High-end (512MB+ heap): 2.0-2.5x multiplier, 150MB cache
- Image compression quality: 80% (see `IMAGE_COMPRESSION_QUALITY`)
- Use `getOptimalImageSize(context)` instead of deprecated `MAX_IMAGE_SIZE` constant
- Low memory threshold is dynamic via `getLowMemoryThreshold(context)`

## Commit and PR Guidelines

- Write commit messages in Russian using conventional commits format: `feat:`, `fix:`, `docs:`
- Example: `feat: Добавлена интеллектуальная стабилизация изображения (OIS/EIS)`
- Subject line capped at 72 characters
- Each commit should be focused and pass `./gradlew lint test`
- PRs require summary, screenshots/recordings for UI updates, and linked issues
- Highlight Firebase, Dropbox, or signing configuration steps in PR descriptions when relevant

## Important Notes

- **SDK Versions:** compileSdk 35, targetSdk 34, minSdk 27
- Uses both Kotlin 2.1 and Java - prefer Kotlin for new features
- Dropbox integration requires proper ACCESS_TOKEN configuration in `B1VoidApplication.kt`
- Camera permissions and external storage access are critical for core functionality
- App is optimized for warehouse/industrial use with large touch targets and simplified workflows
- The project uses both Compose (new screens) and View Binding (legacy screens) - follow existing patterns in each module