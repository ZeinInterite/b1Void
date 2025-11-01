# Camera Exposure Rework (CameraX + Compose)

This document describes the new exposure compensation implementation aligned with OpenCamera UX.

Key components
- `feature/camera/.../ExposureInteractor.kt` – single source of truth for CameraX EV operations.
- `app/.../ui/camera/CameraViewModel.kt` – exposes `evCompensation`, `evRange`, and actions; persists EV via `CameraSettingsManager`.
- `app/.../ui/camera/EvControlPanel.kt` – Compose UI with EV button + vertical slider and 1.5s auto‑hide.

Architecture
- Clean Architecture: interactor in feature module; ViewModel in app; UI in Compose.
- Hilt DI: `@HiltAndroidApp` on `B1VoidApplication`, `@AndroidEntryPoint` for `CameraActivity`, `@HiltViewModel` for `CameraViewModel`, provider in `AppModule` for `CameraSettingsManager`.

Behavior
- EV range is read from `CameraInfo.exposureState`, visually clamped to [-2..+2].
- EV changes applied live to preview via `CameraControl.setExposureCompensationIndex(...)`.
- EV value is saved to DataStore and restored on app start/bind.
- Slider hides automatically after ~1.5s without interaction.

Removed legacy
- Old `EvController` and overlay views are removed/replaced by Compose.

Developer notes
- Lint: run `./gradlew lint`. Address non‑EV related issues separately (missing permissions, etc.).
- Unit tests: `./gradlew test`. Added basic unit tests for interactor and VM clamping/persist.

File map
- feature/camera/src/main/java/com/example/b1void/camera/ev/ExposureInteractor.kt
- app/src/main/java/com/example/b1void/ui/camera/CameraViewModel.kt
- app/src/main/java/com/example/b1void/ui/camera/EvControlPanel.kt
- app/src/main/java/com/example/b1void/activities/CameraActivity.kt (wiring Compose panel)
- app/src/main/res/layout*/activity_camera.xml (Compose host view)

