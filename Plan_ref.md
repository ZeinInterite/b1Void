# Technical Specification & Migration Plan: b1Void 2.0

This document serves as the master task list for the AI Agent responsible for refactoring the `b1Void` project. The goal is to migrate to a strictly typed, modular Clean Architecture using Jetpack Compose, Hilt, and Coroutines.

**Architecture Goals:**
- **Single Activity:** `MainActivity` + `NavHost`.
- **UI:** 100% Jetpack Compose.
- **DI:** Hilt with strict module separation.
- **Threading:** Injected Dispatchers only (No hardcoded `Dispatchers.Main`).
- **Camera:** CameraX abstracted behind a Domain Interface.

---

## Phase 1: Cleanup & Stabilization (High Priority)
*Focus: Removing technical debt and preventing crashes before restructuring.*

- [x] **Dependency Audit**:
    - Remove `com.otaliastudios:cameraview` from `:app` build.gradle.
    - Ensure `androidx.camera` dependencies are up to date in version catalog (`libs.versions.toml`).
- [x] **Dead Code Removal**:
    - Analyze `CameraXFocusController`. Remove all commented-out code blocks.
    - Locate and remove unused resources (XML layouts no longer referenced, unused drawables).
- [x] **Threading Fixes (Critical)**:
    - Scan `FocusInteractor` and `CameraXFocusController`.
    - Replace any `Handler().postDelayed` with `suspend fun` + `delay()`.
    - Identify any I/O operations (file writing, DB access) on Main Thread and wrap them in `withContext(Dispatchers.IO)` temporarily until architecture fixes are applied.

## Phase 2: Core Infrastructure & Modularization
*Focus: Setting up the foundation modules and Dependency Injection.*

- [x] **Module Structure Setup**:
    - Create new Android Library modules: `:core:common`, `:core:model`, `:core:data`, `:core:domain`, `:core:ui`.
    - Configure `build.gradle.kts` for each module to share common configurations.
- [x] **DI Setup (Hilt)**:
    - In `:core:common`, create `DispatchersModule` providing `@IoDispatcher`, `@MainDispatcher`, `@DefaultDispatcher`.
    - Provide `CoroutineScope` wrappers (`ApplicationScope`) for background work.
- [x] **Domain Layer (Pure Kotlin)**:
    - Move core data models to `:core:model` (ensure they are pure Kotlin data classes).
    - Define Repository Interfaces in `:core:domain` (e.g., `PhotoRepository`, `UserRepository`).
    - Define Feature UseCases in `:core:domain` (e.g., `CapturePhotoUseCase`).

## Phase 3: Hardware Abstraction (The Camera Core)
*Focus: Isolating CameraX and device-specific hacks.*

- [x] **Создать модуль ядра камеры**:
    - Initialize `:core:camera`.
- [x] **Define Camera Interface**:
    - Create `CameraController` interface in `:core:domain` containing methods: `takePicture()`, `setZoom()`, `toggleFlash()`.
- [x] **Implement CameraX**:
    - Implement `CameraXController` in `:core:camera`.
    - Move all active CameraX logic from the old `:feature:camera` to this new class.
- [x] **Isolate Quirks**:
    - Create `DeviceQuirksManager` in `:core:camera`.
    - Move `isXiaomiDevice()` checks and specific workaround logic into this manager.
    - Inject `DeviceQuirksManager` into `CameraXController`.

## Phase 4: Data Layer & Persistence
*Focus: Repositories, Database, and Cloud Sync.*

- [x] **Database Migration**:
    - Setup Room Database in `:core:data`.
    - Create Entities mapping to Domain Models.
    - Create DAOs for Photo metadata.
- [x] **Repository Implementation**:
    - Implement `PhotoRepositoryImpl` in `:core:data`.
    - Inject `CameraXController` (from Domain interface) and Room DAO into the repository.
    - Ensure all public methods are `suspend` or return `Flow`.
- [x] **Sync Strategy**:
    - Define `CloudStorage` interface.
    - Implement `FirebaseStorageSource` and `DropboxStorageSource` in `:core:network`.
    - Setup `WorkManager` worker for background syncing.

## Phase 5: UI & Navigation (Jetpack Compose)
*Focus: Replacing XML with Compose and implementing specific features.*

- [x] **Design System**:
    - In `:core:ui`, create `Theme.kt`, `Color.kt`, `Type.kt`.
    - Create reusable components (Buttons, Toolbars, Loaders).
- [x] **Feature: Camera UI**:
    - Create `:feature:camera` (clean slate).
    - Implement `CameraScreen` Composable.
    - Connect `CameraViewModel` to `CapturePhotoUseCase`.
    - Use `AndroidView` wrapper only for the CameraX `PreviewView`, everything else must be Compose.
- [x] **Feature: Gallery UI**:
    - Create `:feature:gallery`.
    - Implement `GalleryScreen` with lazy loading (LazyVerticalGrid).
    - Integrate `Coil` for image loading.
- [ ] **Navigation**:
    - Implement Navigation Graph in `:app`.
    - Define routes in a type-safe manner (or using sealed classes).

## Phase 6: Final Polish & Testing
*Focus: Quality Assurance.*

- [ ] **LeakCanary Check**:
    - Run app with LeakCanary to detect memory leaks, especially in Camera references.
- [x] **Strict Mode**:
    - Enable StrictMode to catch any remaining disk/network I/O on the main thread.
- [x] **Documentation**:
    - Update `README.md` with the new architecture diagram.
    - Document the logic inside `DeviceQuirksManager`.

---

**Agent Instructions:**
1. Read the current status of the project.
2. Pick the highest priority unchecked task from Phase 1.
3. Execute the changes.
4. Mark the task as [x] Completed.
5. Repeat.
