# Refactoring & Restoration Plan

## 1. Core & Infrastructure (Priority: High)
- [x] Fix `B1VoidApplication` Hilt Integration (Step 1)
- [x] Verify `settings.gradle.kts` includes all modules (Step 1)
- [x] Clean `:app` dependencies (remove duplicates, ensure core project imports)

## 2. Interface Restoration (UI Layer)
- [x] **Camera Feature:**
    - [x] Restore `CameraActivity` logic using `:feature:camera`
    - [x] Wire up `CameraViewModel` with `:core:camera` (CameraXController)
    - [x] Re-implement `DeviceQuirksManager` usage
    - > **Note from Agent Gemini:** `CameraActivity` now correctly instantiates `CameraXController` from `:core:camera` and passes dependencies (`DeviceQuirksManager`, `Dispatchers.IO`) directly. The misplaced `CameraManager` and `CameraProvider` classes in the `:app` module have been deleted. `CameraViewModel` now receives the controller instance from the activity.
- [x] **Gallery Feature:**
    - [x] Restore `GalleryActivity/Screen` using `:feature:gallery`
    - [x] Connect `GalleryViewModel` to `PhotoRepository` (`:core:data`)
    - > **Note from Agent Gemini:** `GalleryActivity` has been created. `GalleryViewModel` now correctly uses `PhotoRepository` to fetch all images from the device's `MediaStore`. `GalleryScreen` has been updated to display this data. This feature block is complete.
- [x] **Main Navigation:**
    - [x] Update `MainActivity` (or `AdmActivity`) to route to new Feature Screens
    - > **Note from Agent Gemini:** `MainActivity` now uses a `Scaffold` with a `BottomNavigationBar` to switch between `CameraScreen` and `GalleryScreen`. `NavigationGraph.kt` has been updated to support this. The main navigation is now functional.

## 3. Data & Domain Layer Wiring
- [x] Implement missing UseCases in `:core:domain` for Photo/Folder operations
    - > **Note from Agent Gemini:** Added `DeletePhotoUseCase`, `CreateFolderUseCase`, and `DeleteFolderUseCase` to the `:core:domain` module. The corresponding repository methods have been implemented in the `:core:data` module.
- [x] Ensure `WorkManager` (Sync) is properly injected via Hilt
    - > **Note from Agent Gemini:** Re-added Hilt-Work dependencies and configured `B1VoidApplication` to provide the `HiltWorkerFactory`. `SyncWorker` is correctly annotated for Hilt injection.
- [ ] Verify `Room` database migrations or schema validity

## 4. Legacy Cleanup (The "Purge")
- [ ] Delete old `CameraView` library imports
- [ ] Remove unused layouts/XMLs replaced by Compose
- [ ] Delete commented-out "stub" blocks in `:app`