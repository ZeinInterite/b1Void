# Быстрая инструкция по тестированию

## 1. Установить APK

```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## 2. Собрать логи

```bash
# Открыть новое окно командной строки и запустить:
adb logcat -c
adb logcat -s CameraActivity:D | findstr "resolution\|Resolution\|zoom\|SYNC\|MATCHED"
```

## 3. Запустить камеру

- Открыть приложение
- Перейти в камеру
- Посмотреть, что выводится в логах

## 4. Что ищем в логах

### ✅ ХОРОШО (проблема решена):
```
Maximum available preview resolution: 1920x1440
Selected resolution: 1920x1440
Preview target resolution: 1920x1440
ImageCapture target resolution: 1920x1440
SYNC CHECK: Preview and Capture using SAME resolution = true
Preview resolved resolution: 1920x1440
ImageCapture resolved resolution: 1920x1440
=== RESOLUTION SYNC STATUS: ✓ MATCHED ===
Initial zoom ratio: 1.0
```

### ❌ ПЛОХО (проблема остается):
```
Maximum available preview resolution: 960x720  <-- низкое!
Selected resolution: 960x720                    <-- низкое!
```

или

```
Preview resolved resolution: 1920x1440
ImageCapture resolved resolution: 960x720      <-- не совпадают!
=== RESOLUTION SYNC STATUS: ✗ MISMATCH ===
```

или

```
Initial zoom ratio: 2.0                         <-- zoom > 1.0!
```

## 5. Визуальная проверка

- Превью должно показывать **широкий угол обзора**
- Сравнить с системной камерой - должно быть похоже
- Сделать фото - должно совпадать с превью

## 6. Отправить результат

Скопировать логи и отправить вместе с описанием:
- Стало лучше / хуже / без изменений
- Скриншот превью
