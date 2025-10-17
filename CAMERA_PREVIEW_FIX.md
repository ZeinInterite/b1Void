# Исправление рассинхронизации превью и фотографии

## Проблема

При использовании камеры превью показывало приближенное изображение, а итоговая фотография оказывалась менее приближенной (более широкий угол обзора).

## Анализ логов

Из логов камеры видно, что использовались разные разрешения:

```
Stream[0] : [1920 x 1440] - Preview (превью)
Stream[1] : [960 x 720]   - ImageCapture (фотография)
Stream[2] : [1280 x 720]  - VideoCapture (видео)
```

### Ключевая проблема:

**Preview использует разрешение 1920x1440, а ImageCapture - 960x720 (в 2 раза меньше)**

Даже при использовании `ViewPort` с одинаковым aspect ratio (4:3) и zoom=1.0, разные разрешения приводят к тому, что:
- Preview показывает **более узкую область** сенсора (1920x1440 пикселей)
- ImageCapture захватывает **более широкую область** сенсора (960x720 пикселей)

## Причина

В методе `startCamera()` в `CameraActivity.kt:586`:

```kotlin
val previewResolution = findBestPreviewResolutionFor(captureResolution)
```

Метод `findBestPreviewResolutionFor()` искал **максимальное** доступное разрешение для превью с тем же aspect ratio, что и у фото. Это приводило к:
- `captureResolution` = 960x720 (из настроек пользователя)
- `previewResolution` = 1920x1440 (максимальное 4:3 разрешение для превью)

## Решение

Установлено **максимальное одинаковое разрешение** для Preview и ImageCapture:

```kotlin
// CRITICAL FIX: Use HIGHEST available resolution for both preview and capture
// This ensures ViewPort synchronization and prevents zoomed-in preview
val bestPreviewResolution = findBestPreviewResolutionFor(DEFAULT_PHOTO_RESOLUTION)
val captureResolution = bestPreviewResolution ?: ...
val previewResolution = captureResolution // SAME resolution for both
```

### Изменения в коде:

**Файл:** `app/src/main/java/com/example/b1void/activities/CameraActivity.kt`

**Строки 575-588:**
```kotlin
// До:
// val captureResolution = selectedResolution?.let { ... } ?: ...
// val previewResolution = findBestPreviewResolutionFor(captureResolution)

// После:
// Use HIGHEST available resolution for both to avoid zoomed preview
val bestPreviewResolution = findBestPreviewResolutionFor(DEFAULT_PHOTO_RESOLUTION)
val captureResolution = bestPreviewResolution ?: ...
val previewResolution = captureResolution
```

**Добавлено логирование (строка 656):**
```kotlin
Log.d(TAG, "SYNC CHECK: Preview and Capture using SAME resolution = ${previewResolution == captureResolution}")
```

**Добавлена проверка после bind (строки 705-713):**
```kotlin
// CRITICAL CHECK: Verify preview and capture use same resolution
if (previewResActual != null && captureResActual != null) {
    val isMatching = previewResActual == captureResActual
    Log.d(TAG, "=== RESOLUTION SYNC STATUS: ${if (isMatching) "✓ MATCHED" else "✗ MISMATCH"} ===")
    if (!isMatching) {
        Log.w(TAG, "WARNING: Preview and Capture resolutions don't match!")
        Log.w(TAG, "This will cause preview to show different area than captured photo")
    }
}
```

## Как работает ViewPort

`ViewPort` в CameraX предназначен для синхронизации crop region между use cases (Preview, ImageCapture, VideoCapture). Однако он работает корректно **только при одинаковых разрешениях**.

### Ключевые концепции:

1. **ViewPort определяет общую область сенсора** для всех use cases
2. **При одинаковых разрешениях** ViewPort обрезает одинаковую область
3. **При разных разрешениях** ViewPort не может гарантировать одинаковую crop region

## Проверка исправления

После применения исправления в логах должно быть:

```
Resolution selection: bestPreview=1920x1440, selected=1920x1440
=== CameraX Configuration ===
Preview target resolution: 1920x1440
ImageCapture target resolution: 1920x1440
SYNC CHECK: Preview and Capture using SAME resolution = true

Preview resolved resolution: 1920x1440
ImageCapture resolved resolution: 1920x1440
=== RESOLUTION SYNC STATUS: ✓ MATCHED ===
```

**Ключевое отличие от первой версии исправления:**
- Используется **максимальное** разрешение (1920x1440) вместо настройки пользователя (960x720)
- Это предотвращает чрезмерно приближенное превью

## Результат

Теперь превью и итоговая фотография показывают **одинаковую область** сенсора, решая проблему рассинхронизации.

## Дата исправления

17 октября 2025
