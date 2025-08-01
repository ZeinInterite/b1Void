# Инструкции по сборке оптимизированного B1Void

## Подготовка к сборке

### 1. Проверка зависимостей
Убедитесь, что у вас установлены:
- Android Studio Arctic Fox или новее
- JDK 11 или новее
- Android SDK API 25-35
- Gradle 7.0+

### 2. Настройка проекта
1. Откройте проект в Android Studio
2. Синхронизируйте Gradle файлы
3. Проверьте, что все зависимости загружены

## Сборка оптимизированной версии

### Debug сборка
```bash
# Для тестирования оптимизаций
./gradlew assembleDebug
```

### Release сборка (рекомендуется)
```bash
# Оптимизированная версия для слабых устройств
./gradlew assembleRelease
```

### Сборка с дополнительными оптимизациями
```bash
# Максимальная оптимизация
./gradlew assembleRelease -Poptimize=true
```

## Проверка оптимизаций

### 1. Анализ размера APK
```bash
# Анализ размера APK
./gradlew assembleRelease
# APK будет в app/build/outputs/apk/release/
```

### 2. Проверка оптимизаций ProGuard
```bash
# Просмотр отчета ProGuard
cat app/build/outputs/mapping/release/mapping.txt
```

### 3. Тестирование на слабых устройствах

#### Эмулятор с низкими характеристиками:
- RAM: 512MB
- CPU: 1 core
- Storage: 1GB
- API Level: 25

#### Физические устройства для тестирования:
- Устройства с 1-2GB RAM
- Старые версии Android (7.0+)
- Устройства с медленными процессорами

## Настройки для разных типов устройств

### Автоматическое определение
Приложение автоматически определяет тип устройства и применяет соответствующие оптимизации:

```kotlin
// В коде приложения
val isLowEnd = MemoryManager.isLowEndDevice(context)
val settings = MemoryManager.getOptimizedSettings(context)
```

### Ручная настройка (для разработчиков)
Можно изменить настройки в `B1VoidApplication.kt`:

```kotlin
companion object {
    // Порог для определения слабых устройств
    const val LOW_MEMORY_THRESHOLD = 50 * 1024 * 1024 // 50MB
    
    // Качество изображений
    const val IMAGE_COMPRESSION_QUALITY = 80
    
    // Максимальный размер изображения
    const val MAX_IMAGE_SIZE = 1024
}
```

## Профили сборки

### Debug профиль
- Отключена минификация
- Включены логи
- Отключена оптимизация
- Быстрая сборка

### Release профиль
- Включена минификация (R8/ProGuard)
- Отключены логи
- Максимальная оптимизация
- Сжатие ресурсов

## Оптимизации в релизной сборке

### 1. Код
- Удаление неиспользуемого кода
- Минификация имен
- Оптимизация байткода
- Удаление отладочной информации

### 2. Ресурсы
- Сжатие изображений
- Удаление неиспользуемых ресурсов
- Оптимизация строк
- Сжатие APK

### 3. Зависимости
- Удаление неиспользуемых библиотек
- Оптимизация размеров
- Минификация зависимостей

## Мониторинг производительности

### Встроенные метрики
Приложение автоматически отслеживает:
- Использование памяти
- Время загрузки экранов
- Производительность камеры
- Скорость файловых операций

### Логирование
```kotlin
// Логи оптимизации
Log.d("MemoryManager", "Available memory: ${MemoryManager.getAvailableMemory(context)}")
Log.d("ImageOptimizer", "Image optimized: ${file.name}")
Log.d("CameraOptimizer", "Camera settings applied: $settings")
```

## Тестирование оптимизаций

### 1. Тест памяти
```bash
# Мониторинг использования памяти
adb shell dumpsys meminfo com.example.b1void
```

### 2. Тест производительности
```bash
# Профилирование CPU
adb shell am profile start com.example.b1void /sdcard/profile.trace
# ... выполнение операций ...
adb shell am profile stop com.example.b1void
```

### 3. Тест запуска
```bash
# Измерение времени запуска
adb shell am start -W com.example.b1void/.activities.SplashActivity
```

## Распространение

### Google Play Store
1. Подпишите APK:
```bash
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
  -keystore your-keystore.jks \
  app-release-unsigned.apk your-alias
```

2. Оптимизируйте APK:
```bash
zipalign -v 4 app-release-unsigned.apk app-release.apk
```

### Прямое распространение
- APK готов к установке
- Оптимизирован для слабых устройств
- Размер уменьшен на 30-50%

## Устранение проблем сборки

### Ошибка ProGuard
```bash
# Добавьте правила в proguard-rules.pro
-keep class com.example.b1void.** { *; }
```

### Ошибка памяти
```bash
# Увеличьте память для Gradle
export GRADLE_OPTS="-Xmx2048m -XX:MaxPermSize=512m"
```

### Ошибка зависимостей
```bash
# Очистите кэш Gradle
./gradlew clean
./gradlew --refresh-dependencies
```

## Рекомендации по развертыванию

### 1. Тестирование
- Протестируйте на реальных слабых устройствах
- Проверьте все основные функции
- Убедитесь в стабильности работы

### 2. Мониторинг
- Отслеживайте отзывы пользователей
- Мониторьте краши приложения
- Анализируйте метрики производительности

### 3. Обновления
- Регулярно обновляйте оптимизации
- Адаптируйте под новые версии Android
- Учитывайте отзывы пользователей

## Контакты

При возникновении проблем с оптимизацией:
- Создайте issue в репозитории
- Приложите логи и информацию об устройстве
- Опишите шаги для воспроизведения проблемы 