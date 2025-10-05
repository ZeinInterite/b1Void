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
- `./gradlew test` - Run JVM unit tests
- `./gradlew connectedAndroidTest` - Run instrumentation tests (requires connected device)

**Note:** Do not use `./gradlew.bat compileDebugKotlin` - use the full build commands above.

## Architecture Overview

**Technology Stack:**
- **Language:** Kotlin (primary) with some Java legacy code
- **Camera:** CameraX 1.3.1 for photo/video capture with custom controls
- **UI:** View Binding enabled, Android Material Design
- **Storage:** External storage with FileProvider, Dropbox integration
- **Image Processing:** Glide 4.16.0 with custom optimization, MediaMetadataRetriever
- **Background Work:** WorkManager for file operations
- **Testing:** JUnit + Espresso for instrumentation tests

**Key Application Structure:**
- **Application Class:** `B1VoidApplication.kt` - Initializes Glide optimization, MultiDex, WorkManager configuration
- **Main Activities:**
  - `MainActivity.java` - Entry point with Dropbox authentication setup
  - `CameraActivity.kt` - Camera interface with custom controls, focus, flash, zoom, video recording
  - `FileManagerActivity.kt` - File browser with grid layout, selection, move operations
  - `ImagePreviewActivity.kt` - Full-screen image viewer with editing capabilities
  - `NavigationApp.kt` - Main navigation hub

**Data Layer:**
- `data/CameraSettingsManager.kt` - Camera configuration persistence
- `data/FolderRepository.kt` - File system operations
- `models/` - Data classes for Inspection, Inspector entities
- `utils/` - File operations, image optimization, memory management

**Key Features:**
- Custom camera with manual controls (exposure, ISO, focus)
- Video recording with timestamp overlays
- Batch file operations and cloud sync via Dropbox
- Optimized for low-end devices with memory management
- File sharing with ZIP archive creation

## Development Guidelines

**Code Style:**
- Follow Kotlin style guide: 4-space indentation, camelCase for members, PascalCase for classes
- Android resources use snake_case (`activity_main.xml`)
- Package organization is feature-based under `com.example.b1void`
- View binding is enabled - avoid synthetic imports

**Security Configuration:**
- Release signing expects `app/release.keystore` (keep real credentials outside version control)
- API keys and secrets go in `local.properties` or secure vaults
- Firebase config in `app/google-services.json` - scrub personal data before sharing

**Testing:**
- Mirror source names with `Test` suffix for unit classes
- Instrumentation tests in `androidTest/` directory
- Target >80% coverage on new code
- Add camera/storage smoke tests for permission and happy-path flows

**Memory Optimization:**
- App uses MultiDex and custom Glide configuration for low-end devices
- Image compression quality set to 80%, max size 1024px
- Low memory threshold: 50MB (defined in `B1VoidApplication`)

## Commit and PR Guidelines

- Write commit subjects in Russian imperative or past tense (see git log), capped at 72 characters
- Each commit should be focused and pass `./gradlew lint test`
- PRs require summary, screenshots/recordings for UI updates, and linked issues
- Highlight Firebase, Dropbox, or signing configuration steps in PR descriptions when relevant

## Important Notes

- Target SDK 34, minimum SDK 25, compile SDK 35
- Uses both Kotlin and Java - prefer Kotlin for new features
- Dropbox integration requires proper ACCESS_TOKEN configuration
- Camera permissions and external storage access are critical for core functionality
- App is optimized for warehouse/industrial use with large touch targets and simplified workflows