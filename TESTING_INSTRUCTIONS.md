# Инструкция по проверке исправления синхронизации превью и фотографии

## Что было исправлено

Устранена проблема, при которой превью камеры показывало приближенное изображение (более узкий угол обзора), а итоговая фотография получалась менее приближенной (более широкий угол).

## Как проверить исправление

### 1. Установить новую версию приложения

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 2. Проверить логи при запуске камеры

Откройте логи и найдите строки с тегом `CameraActivity`:

```bash
adb logcat | findstr CameraActivity
```

**Ожидаемые логи (правильная конфигурация):**

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

**Если видите это - значит исправление работает правильно!**

### 3. Визуальная проверка

1. Откройте камеру в приложении
2. Наведите камеру на объект с четкими границами (например, дверь, окно, рамка картины)
3. Обратите внимание на то, что видно в превью
4. Сделайте фотографию
5. Откройте сделанную фотографию

**Результат:** Фотография должна содержать **ту же область**, что и превью. Никакого "сюрприза" с неожиданно расширенной областью.

### 4. Проверка приближения превью

**До исправления:** Превью было слишком приближено (использовало разрешение 960x720)
**После исправления:** Превью использует максимальное разрешение (1920x1440) и показывает нормальный угол обзора

Если превью все еще слишком приближено:
1. Проверьте логи - должно быть `Resolution selection: bestPreview=1920x1440`
2. Убедитесь, что установлена правильная версия APK
3. Очистите данные приложения и переустановите

## Что проверять в логах

### ✓ Правильная конфигурация:
```
Resolution selection: bestPreview=1920x1440, selected=1920x1440
Preview target resolution: 1920x1440
ImageCapture target resolution: 1920x1440
SYNC CHECK: Preview and Capture using SAME resolution = true
=== RESOLUTION SYNC STATUS: ✓ MATCHED ===
```

### ✗ Неправильная конфигурация (до исправления):
```
Preview target resolution: 1920x1440
ImageCapture target resolution: 960x720
SYNC CHECK: Preview and Capture using SAME resolution = false
=== RESOLUTION SYNC STATUS: ✗ MISMATCH ===
WARNING: Preview and Capture resolutions don't match!
```

## Технические детали

- **Файл с исправлением:** `app/src/main/java/com/example/b1void/activities/CameraActivity.kt:588`
- **Суть исправления:** Принудительное использование одинакового разрешения для Preview и ImageCapture
- **Механизм:** `ViewPort` теперь корректно синхронизирует crop region между use cases

## Что делать, если проблема не исправлена

1. Проверьте, что используется правильная версия APK (пересоберите и переустановите)
2. Проверьте логи на наличие `WARNING` или `MISMATCH`
3. Сообщите о проблеме с полными логами

## Дата тестирования

17 октября 2025
