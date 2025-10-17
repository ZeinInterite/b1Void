# План отладки проблемы с приближенным превью камеры

## Анализ текущей ситуации

### Проблема
Пользователь сообщает: "Превью камеры очень сильно приближено"

### Проведенные изменения
1. **Первое исправление (неудачное):** Использовали настройку пользователя (960x720) для обоих use cases → превью стало слишком приближенным
2. **Второе исправление (текущее):** Используем максимальное разрешение (1920x1440) для обоих use cases

### Предоставленные логи
Логи показывают только **конфликт с системной службой** `com.samsung.adaptivebrightnessgo`, но **НЕ показывают**:
- Реальную конфигурацию камеры нашего приложения
- Разрешения Preview и ImageCapture
- Значения zoom ratio
- Crop region

## План диагностики

### Шаг 1: Сбор реальных логов нашего приложения

**Команда для сбора логов:**
```bash
adb logcat -c && adb logcat -s CameraActivity:D Camera2CameraImpl:D | findstr "resolution\|zoom\|ScaleType\|SYNC"
```

**Что нужно найти в логах:**
```
Resolution selection: bestPreview=?, selected=?
=== CameraX Configuration ===
Preview target resolution: ?x?
ImageCapture target resolution: ?x?
SYNC CHECK: Preview and Capture using SAME resolution = ?
PreviewView ScaleType: ?
Preview resolved resolution: ?x?
ImageCapture resolved resolution: ?x?
=== RESOLUTION SYNC STATUS: ?
Initial zoom ratio: ? (min: ?, max: ?)
```

### Шаг 2: Проверка основных гипотез

#### Гипотеза 1: Разрешения не совпадают
**Признак:** `SYNC CHECK: Preview and Capture using SAME resolution = false`
**Решение:** Уже реализовано в коде, но нужно проверить логи

#### Гипотеза 2: Zoom ratio установлен неправильно
**Признак:** `Initial zoom ratio: 2.0` или выше
**Решение:** Проверить, что в строке 687 CameraActivity.kt установлено `setZoomRatio(1.0f)`

#### Гипотеза 3: ScaleType неправильный
**Признак:** `PreviewView ScaleType: FIT_END` или другой неожиданный
**Ожидаемое:** `PreviewView ScaleType: FILL_CENTER`

#### Гипотеза 4: Выбрано низкое разрешение
**Признак:** `Resolution selection: bestPreview=null` или `selected=960x720`
**Решение:** Проверить, что `findBestPreviewResolutionFor()` возвращает максимальное разрешение

#### Гипотеза 5: Crop region применен некорректно
**Признак:** ViewPort работает неправильно из-за разных aspect ratio
**Решение:** Убедиться, что оба use cases используют AspectRatioStrategy.RATIO_4_3

### Шаг 3: Визуальная проверка на устройстве

**Установка APK:**
```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Проверка:**
1. Открыть камеру в приложении
2. Сравнить угол обзора с системной камерой Samsung
3. Сделать фото и проверить, совпадает ли с превью
4. Проверить zoom controls - должны показывать 1.0x по умолчанию

### Шаг 4: Дополнительное логирование (если нужно)

Если стандартные логи не помогают, добавить в `CameraActivity.kt` после строки 687:

```kotlin
camera?.cameraInfo?.let { info ->
    // Log camera sensor info
    val sensorSize = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    val availableFocalLengths = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)

    Log.d(TAG, "=== Camera Sensor Info ===")
    Log.d(TAG, "Sensor active array size: $sensorSize")
    Log.d(TAG, "Available focal lengths: ${availableFocalLengths?.joinToString()}")

    // Log crop region
    preview.resolutionInfo?.let { resInfo ->
        Log.d(TAG, "Preview crop rect: ${resInfo.cropRect}")
    }
    newImageCapture.resolutionInfo?.let { resInfo ->
        Log.d(TAG, "ImageCapture crop rect: ${resInfo.cropRect}")
    }
}
```

## Возможные причины проблемы

### 1. ResolutionSelector не работает как ожидалось
**Симптом:** `findBestPreviewResolutionFor(DEFAULT_PHOTO_RESOLUTION)` возвращает `null` или низкое разрешение
**Проверка:** Логи должны показать `Resolution selection: bestPreview=null`
**Решение:**
```kotlin
// Fallback to maximum available resolution
val captureResolution = bestPreviewResolution
    ?: availablePreviewResolutions.maxByOrNull { it.width * it.height }
    ?: DEFAULT_PHOTO_RESOLUTION
```

### 2. CameraX выбирает другое разрешение при bind
**Симптом:** Target resolution 1920x1440, но resolved resolution 960x720
**Решение:** Использовать ResolutionStrategy с FALLBACK_RULE_NONE вместо CLOSEST_HIGHER_THEN_LOWER

### 3. ViewPort применяет неожиданный crop
**Симптом:** Разрешения совпадают, но превью приближено
**Решение:** Проверить aspect ratio ViewPort - должен быть Rational(4, 3)

### 4. PreviewView масштабирует неправильно
**Симптом:** ScaleType: FILL_CENTER, но превью растянуто
**Решение:** Попробовать FIT_CENTER или изменить размер PreviewView

### 5. Zoom сохранен из предыдущей сессии
**Симптом:** Zoom ratio сразу после bind > 1.0
**Решение:** Очистить сохраненное состояние zoom в CameraViewModel

## Ожидаемые результаты после исправления

### В логах должно быть:
```
Resolution selection: bestPreview=1920x1440, selected=1920x1440
Preview target resolution: 1920x1440
ImageCapture target resolution: 1920x1440
SYNC CHECK: Preview and Capture using SAME resolution = true
PreviewView ScaleType: FILL_CENTER
Preview resolved resolution: 1920x1440
ImageCapture resolved resolution: 1920x1440
=== RESOLUTION SYNC STATUS: ✓ MATCHED ===
Initial zoom ratio: 1.0 (min: 0.5, max: 10.0)
```

### Визуально:
- Превью показывает **широкий угол обзора**, сравнимый с системной камерой
- Фотография захватывает **ту же область**, что показывает превью
- Zoom control показывает **1.0x** по умолчанию
- При двойном тапе или pinch zoom работает плавно

## Следующие шаги

1. ✅ **Собрать реальные логи** с тегом `CameraActivity` при запуске камеры
2. ⏳ **Проанализировать логи** - найти несоответствия с ожидаемыми значениями
3. ⏳ **Установить APK на устройство** и визуально проверить превью
4. ⏳ **Если проблема сохраняется** - добавить дополнительное логирование crop rect
5. ⏳ **Применить финальное исправление** на основе реальных данных

## Дата создания
17 октября 2025
