# Полная документация проекта Inspector_appVX

## Оглавление

1. [Обзор проекта](#обзор-проекта)
2. [Архитектура приложения](#архитектура-приложения)
3. [Модули и структура](#модули-и-структура)
4. [Основные компоненты](#основные-компоненты)
5. [Система управления данными](#система-управления-данными)
6. [Утилиты и вспомогательные классы](#утилиты-и-вспомогательные-классы)
7. [UI/UX компоненты](#uiux-компоненты)
8. [Сборка и развертывание](#сборка-и-развертывание)
9. [Тестирование](#тестирование)
10. [Безопасность и конфигурация](#безопасность-и-конфигурация)

---

## Обзор проекта

**Inspector_appVX** — это Android-приложение для управления файлами, предназначенное для сюрвейеров и инспекторов складов, проводящих экспертизу продукции. Приложение фокусируется на организации, просмотре и базовом редактировании фотографий, сделанных на складах.

### Целевая аудитория
- Инспекторы складов
- Сюрвейеры
- Специалисты по экспертизе продукции

### Ключевые особенности
- Работа в перчатках (крупные кнопки и элементы управления)
- Оптимизация для работы в условиях ограниченного времени
- Поддержка слабых устройств с адаптивным управлением памятью
- Категоризация и быстрый поиск фотографий
- Интеграция с Dropbox для синхронизации

### Технические характеристики
- **Язык программирования**: Kotlin 2.1 (основной), Java (legacy код)
- **Минимальная версия Android**: API 27 (Android 8.1)
- **Целевая версия Android**: API 34 (Android 14)
- **Компиляция SDK**: API 35

---

## Архитектура приложения

### Общая архитектура

Приложение построено на **мультимодульной архитектуре**:

```
b1Void/
├── app/                      # Основной модуль приложения
│   ├── activities/           # Activity компоненты
│   ├── adapters/             # RecyclerView адаптеры
│   ├── data/                 # Управление данными и настройками
│   ├── models/               # Модели данных
│   ├── ui/                   # UI компоненты и фрагменты
│   └── utils/                # Утилиты
└── feature/
    └── camera/               # Модуль камеры (clean architecture + Hilt)
        ├── data/             # Data layer
        ├── domain/           # Domain layer (бизнес-логика)
        ├── ui/               # UI layer (Compose)
        ├── core/             # Общие компоненты
        └── di/               # Dependency Injection (Hilt)
```

### Архитектурные паттерны

#### Основной модуль `:app`
- **Паттерн**: MVC/MVVM гибрид
- **DI**: Ручная инъекция зависимостей через фабрики (например, `DropboxClientFactory`)
- **UI**: Комбинация View Binding и Jetpack Compose

#### Модуль камеры `:feature:camera`
- **Паттерн**: Clean Architecture (Data → Domain → UI)
- **DI**: Hilt с KSP компилятором
- **UI**: Jetpack Compose
- **Слои**:
  - **Data Layer**: `Camera2FocusController`, `CameraXFocusController`, `FocusRepository`
  - **Domain Layer**: `FocusInteractor` — бизнес-логика фокусировки, экспозиции, стабилизации
  - **UI Layer**: `CameraViewModel`, `FocusOverlayView` — Compose UI и управление состоянием

---

## Модули и структура

### Модуль `:app`

Основной модуль приложения, содержащий все активити, адаптеры и основную бизнес-логику.

#### Ключевые пакеты

**activities/**
- `MainActivity.java` — точка входа, настройка Dropbox
- `NavigationApp.kt` — главная навигация приложения
- `CameraActivity.kt` / `CameraComposeActivity.kt` — интерфейсы камеры (legacy и Compose)
- `FileManagerActivity.kt` — файловый менеджер с grid layout, выделение, перемещение
- `ImagePreviewActivity.kt` — полноэкранный просмотр изображений с редактированием
- `InspectionAddActivity.java` — создание инспекций
- `ShowInspectionActivity.kt` — отображение инспекций
- `AdmActivity.kt` / `AdmLogActivity.java` — администрирование
- `WorkerActivity.java` — работа с инспекторами
- `EditImageActivity.java` — редактирование изображений
- `ShareImportActivity.kt` — импорт изображений через Share
- `SplashActivity.java` — экран загрузки
- `CameraSettingsActivity.java` — настройки камеры
- `SignatureView.java` — компонент подписи

**adapters/**
- `FileAdapter.kt` — адаптер для файлового менеджера
- `ImagePagerAdapter.kt` — адаптер для просмотра изображений
- `InspectionAdapter.java` — адаптер списка инспекций
- `InspectorAdapter.kt` — адаптер списка инспекторов
- `FolderTreeAdapter.kt` — адаптер дерева папок
- `ResolutionAdapter.kt` — адаптер выбора разрешения

**data/**
- `CameraSettingsManager.kt` — управление настройками камеры через DataStore
- `FileManagerSettingsManager.kt` — настройки файлового менеджера
- `AppSettingsBootstrap.kt` — инициализация настроек при запуске
- `AppSettingsCache.kt` — кэш настроек приложения
- `FolderRepository.kt` — операции с файловой системой

**models/**
- `Inspection.java` — модель инспекции
- `Inspector.kt` — модель инспектора
- `MoveState.kt` — состояние операции перемещения файлов

**utils/**
- `ImageOptimizer.kt` — оптимизация и сжатие изображений
- `MemoryManager.kt` — управление памятью устройства
- `FileManagerUtils.kt` — утилиты работы с файлами
- `DropboxClientFactory.kt` — фабрика клиента Dropbox
- `CameraOptimizer.kt` — оптимизация камеры
- `VideoStampProcessor.kt` — добавление временных меток на видео
- `SelectionManager.kt` — управление выделением файлов
- `GestureHandler.kt` — обработка жестов
- `FullscreenImageManager.kt` — управление полноэкранным просмотром
- `ViewExtensions.kt` — расширения для View
- `DeviceInfo.kt` — информация об устройстве
- `ManufacturerCompatibility.kt` — совместимость с производителями

### Модуль `:feature:camera`

Модульный компонент для работы с камерой, построенный по Clean Architecture.

#### Структура слоев

**data/** — Data Layer
- `camera/`
  - `FocusRepository.kt` — репозиторий для управления фокусировкой
  - `camera2/Camera2FocusController.kt` — контроллер для Camera2 API
  - `camerax/CameraXFocusController.kt` — контроллер для CameraX API

**domain/** — Domain Layer
- `camera/FocusInteractor.kt` — интерактор фокусировки с методами:
  - `tapToFocus()` — фокус по касанию
  - `longPressLock()` — блокировка AE/AF
  - `unlock()` — разблокировка
  - `startTracking()` / `stopTracking()` — отслеживание объектов
  - `adjustEv()` — коррекция экспозиции
  - `enableMacro()` / `enableTorchAssist()` — макро режим и подсветка

**ui/** — UI Layer
- `camera/CameraViewModel.kt` — ViewModel для управления камерой
- `camera/FocusOverlayView.kt` — UI overlay для визуализации фокусировки
- `camera/ZoomControl.kt` — элемент управления зумом

**core/** — Общие компоненты
- `camera/FocusState.kt` — состояния фокусировки
- `telemetry/TelemetryLogger.kt` — логирование телеметрии

**di/** — Dependency Injection
- `FocusModule.kt` — Hilt модуль для предоставления зависимостей

**focus/** — Дополнительные компоненты фокусировки
- `FocusProvider.kt` — провайдер фокусировки
- `FocusCoordinator.kt` — координация операций фокусировки
- `CameraXFocusEngine.kt` — движок фокусировки для CameraX

**ev/** — Управление экспозицией
- `EvController.kt` — контроллер экспозиции

---

## Основные компоненты

### 1. Application Class: `B1VoidApplication.kt`

Центральный класс приложения, инициализирующий критически важные компоненты.

**Функциональность:**
- **Glide Configuration**: Адаптивная конфигурация кэширования изображений на основе класса памяти устройства
- **MultiDex Support**: Поддержка старых устройств
- **WorkManager Configuration**: Настройка фоновых задач с учетом ресурсов
- **Dropbox Initialization**: Инициализация клиента Dropbox

**Адаптивная конфигурация памяти:**

```kotlin
// Memory multipliers based on device class
Low RAM devices (Android Go):       0.5x, 25MB disk cache, RGB_565
Mid-range (128MB heap):             1.0x, 50MB disk cache
Mid-high (256MB heap):              1.5x, 100MB disk cache
High-end (512MB heap):              2.0x, 150MB disk cache
Premium (>512MB heap):              2.5x, 150MB disk cache
```

**Константы:**
- `IMAGE_COMPRESSION_QUALITY = 80` — качество сжатия JPEG
- `MAX_IMAGE_SIZE = 1024` (deprecated) — использовать `getOptimalImageSize(context)`
- `getLowMemoryThreshold(context)` — динамический порог низкой памяти (20-100MB)

### 2. MainActivity.java

**Назначение**: Точка входа приложения с настройкой Dropbox

**Основные функции:**
- Инициализация Dropbox клиента
- Переход к NavigationApp

### 3. NavigationApp.kt

**Назначение**: Главная навигационная активность

**Основные функции:**
- Кнопка "Сделать фото" → `CameraActivity`
- Кнопка "Загрузить" → выбор изображений из галереи
- Кнопка "Создать папку" → `FileManagerActivity`

### 4. CameraActivity.kt

**Назначение**: Основная активность камеры (legacy View-based)

**Функциональность:**
- **CameraX Integration**: Использование CameraX 1.3.1 для захвата фото/видео
- **Manual Controls**:
  - Ручная фокусировка (tap-to-focus, long-press lock)
  - Контроль экспозиции (EV compensation)
  - Контроль ISO
  - OIS/EIS стабилизация
- **Video Recording**: Запись видео с наложением временных меток
- **Flash Control**: Управление вспышкой
- **Zoom**: Pinch-to-zoom и программное управление
- **Resolution Settings**: Выбор разрешения фото/видео
- **Preview**: Предпросмотр последней фотографии

**Интеграция с модулем camera:**
```kotlin
implementation(project(":feature:camera"))
```

### 5. CameraComposeActivity.kt

**Назначение**: Новая версия камеры на Jetpack Compose

**Отличия от CameraActivity:**
- Полностью на Compose UI
- Использует `CameraViewModel` из модуля `:feature:camera`
- Современный декларативный подход к UI

### 6. FileManagerActivity.kt

**Назначение**: Файловый менеджер с расширенными возможностями

**Основные функции:**

**Отображение файлов:**
- Grid layout с адаптивным количеством столбцов (2-6)
- Pinch-to-zoom для изменения размера сетки
- Вертикальный SeekBar для динамического изменения размера
- Поддержка landscape/portrait ориентации

**Управление файлами:**
- Создание папок
- Выделение файлов (single/multi selection)
- Swipe selection режим
- Перемещение файлов через `MoveFilesBottomSheet`
- Удаление в корзину
- Восстановление из корзины
- Permanent delete (очистка корзины)
- Переименование
- Sharing (одиночный файл, множественные файлы, ZIP архив)

**Сортировка:**
- По дате (возрастание/убывание)
- По имени (A-Z/Z-A)
- По размеру (малые/большие)

**Настройки:**
- Сохранение размера grid через `FileManagerSettingsManager`
- SwipeRefreshLayout для обновления списка

**UI Components:**
- `RecyclerView` с `GridLayoutManager`
- Selection toolbar (количество выбранных, выделить все, подтвердить)
- Bottom navigation buttons (создать папку, камера, корзина, очистить корзину)
- Context menu для длинного нажатия

### 7. ImagePreviewActivity.kt

**Назначение**: Полноэкранный просмотр изображений с галереей

**Основные функции:**

**Просмотр:**
- `ViewPager2` для swipe между изображениями
- Полноэкранный режим
- Auto-hide UI controls

**Операции:**
- Удаление текущего изображения (с подтверждением)
- Перемещение через `MoveFilesBottomSheet`
- Sharing (через FileProvider)
- Редактирование (переход к `EditImageActivity`)

**Интеграция:**
- Получает массив путей к изображениям
- Синхронизация с `FileManagerActivity` через result listeners

### 8. InspectionAddActivity.java / ShowInspectionActivity.kt

**Назначение**: Работа с инспекциями

**InspectionAddActivity:**
- Создание новых инспекций
- Ввод данных инспекции
- Связывание с фотографиями

**ShowInspectionActivity:**
- Отображение списка инспекций
- Просмотр деталей инспекции
- Редактирование существующих инспекций

### 9. AdmActivity.kt / AdmLogActivity.java

**Назначение**: Административные функции

**AdmActivity:**
- Управление инспекторами
- Настройки приложения
- Администрирование данных

**AdmLogActivity:**
- Просмотр логов
- История операций

### 10. ShareImportActivity.kt

**Назначение**: Обработка внешних Share запросов

**Функциональность:**
- Прием изображений через Android Share
- Поддержка `ACTION_SEND` и `ACTION_SEND_MULTIPLE`
- Импорт в файловую систему приложения

**Manifest интеграция:**
```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <data android:mimeType="image/*" />
</intent-filter>
```

---

## Система управления данными

### DataStore (Jetpack Preferences DataStore)

Приложение использует **DataStore** для персистентного хранения настроек.

#### CameraSettingsManager.kt

**Хранимые настройки:**
- Разрешение фото/видео
- Качество изображения
- Настройки вспышки
- EV compensation
- ISO settings
- Формат даты/времени для watermark
- Stabilization preferences (OIS/EIS)
- Macro mode
- Torch assist

**Использование:**
```kotlin
lifecycleScope.launch {
    val resolution = settingsManager.photoResolution.first()
    // Apply resolution
}
```

#### FileManagerSettingsManager.kt

**Хранимые настройки:**
- Количество столбцов grid (spanCount)
- Режим сортировки
- Последняя открытая папка

#### AppSettingsBootstrap.kt

**Назначение**: Инициализация настроек при первом запуске

**Функции:**
- Восстановление сохраненных настроек
- Установка дефолтных значений
- Миграция старых настроек (SharedPreferences → DataStore)

#### AppSettingsCache.kt

**Назначение**: In-memory кэш для быстрого доступа к настройкам

### Firebase Integration

**Используемые сервисы:**
- **Firebase Database**: Хранение данных инспекций
- **Firebase Auth**: Аутентификация пользователей
- **Firebase Storage**: Хранение изображений (опционально)

**Конфигурация:**
- `app/google-services.json` — файл конфигурации Firebase

### Dropbox Integration

**Библиотеки:**
```gradle
implementation("com.dropbox.core:dropbox-core-sdk:7.0.0")
implementation("com.dropbox.core:dropbox-android-sdk:7.0.0")
```

**DropboxClientFactory.kt:**
- Singleton фабрика для создания Dropbox клиента
- Инициализация с ACCESS_TOKEN
- Используется для синхронизации файлов

**Конфигурация в B1VoidApplication:**
```kotlin
DropboxClientFactory.init("YOUR_ACCESS_TOKEN")
```

**Manifest схема:**
```xml
<data android:scheme="db-elw6ey40dkbo1i0" />
```

### FileProvider

**Назначение**: Безопасный доступ к файлам для других приложений

**Конфигурация в `file_paths.xml`:**
- External storage paths
- Cache directories
- App-specific directories

**Использование:**
```kotlin
val uri = FileProvider.getUriForFile(
    context,
    "${context.packageId}.provider",
    file
)
```

---

## Утилиты и вспомогательные классы

### ImageOptimizer.kt

**Назначение**: Оптимизация изображений для слабых устройств

**Основные методы:**

**`optimizeImage()`**
- Адаптивное масштабирование на основе доступной памяти
- Сжатие с заданным качеством (по умолчанию 80%)
- Использование RGB_565 для низкоресурсных устройств
- Автоматическая очистка памяти

**`compressBitmap()`**
- JPEG compression с настраиваемым quality
- Поддержка различных форматов

**`calculateInSampleSize()`**
- Вычисление оптимального коэффициента масштабирования
- Минимизация использования памяти

**`hasEnoughMemory()`**
- Проверка доступной памяти перед операциями
- Предотвращение OutOfMemoryError

**`clearImageCache()`**
- Очистка кэша изображений при низкой памяти

**Использование:**
```kotlin
suspend fun processImage(inputFile: File) {
    val maxSize = B1VoidApplication.getOptimalImageSize(context)
    val success = ImageOptimizer.optimizeImage(
        context, inputFile, outputFile, maxSize, 80
    )
}
```

### MemoryManager.kt

**Назначение**: Централизованное управление памятью

**Основные методы:**

**`getAvailableMemory()`**
- Получение доступной оперативной памяти

**`isLowEndDevice()`**
- Определение слабого устройства (<100MB доступно)

**`hasEnoughMemory()`**
- Проверка достаточности памяти для операции

**`clearMemoryIfNeeded()`**
- Автоматическая очистка при достижении порога
- Триггер: availMem < `getLowMemoryThreshold(context)`

**`onTrimMemory(level)`**
- Обработка системных событий нехватки памяти
- Уровни: `TRIM_MEMORY_UI_HIDDEN`, `TRIM_MEMORY_RUNNING_LOW`, etc.

**Интеграция в Activities:**
```kotlin
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    MemoryManager.onTrimMemory(level)
}
```

### FileManagerUtils.kt

**Назначение**: Утилиты для работы с файловой системой

**Основные методы:**

**`createAppDirectories()`**
- Создание структуры папок приложения
- Возвращает: (appDir, picturesDir, trashDir)

**`moveFileToTrash()`**
- Перемещение файла в корзину

**`restoreFromTrash()`**
- Восстановление из корзины

**`permanentDelete()`**
- Окончательное удаление файла

**`createZipArchive()`**
- Создание ZIP архива из списка файлов

**`getFileSize()`**
- Получение размера файла в человекочитаемом формате

**`getMimeType()`**
- Определение MIME типа файла

### VideoStampProcessor.kt

**Назначение**: Добавление временных меток на видео

**Технологии:**
- **Media3 Transformer**: Обработка видео
- **Media3 Effect**: Наложение эффектов

**Функциональность:**
- Overlay с датой/временем съемки
- Настраиваемый формат timestamp
- Позиционирование текста (углы экрана)

### CameraOptimizer.kt

**Назначение**: Оптимизация настроек камеры

**Функциональность:**
- Выбор оптимального разрешения на основе устройства
- Настройка битрейта для видео
- Адаптация FPS
- Оптимизация для слабых устройств

### SelectionManager.kt

**Назначение**: Управление выделением файлов

**Функциональность:**
- Multi-selection режим
- Swipe-selection (выделение свайпом)
- Select all / Deselect all
- Синхронизация с адаптерами

### GestureHandler.kt

**Назначение**: Обработка жестов пользователя

**Поддерживаемые жесты:**
- Single tap
- Double tap
- Long press
- Pinch-to-zoom
- Swipe

### DeviceInfo.kt

**Назначение**: Получение информации об устройстве

**Данные:**
- Модель устройства
- Производитель
- Версия Android
- Класс памяти
- Поддержка аппаратного ускорения

### ManufacturerCompatibility.kt

**Назначение**: Обработка особенностей производителей

**Поддерживаемые производители:**
- Samsung (специфичные настройки Camera2)
- Xiaomi (обход ограничений MIUI)
- Huawei (HMS vs GMS)
- OnePlus (настройки OIS)

---

## UI/UX компоненты

### Adapters

#### FileAdapter.kt

**Назначение**: RecyclerView адаптер для файлового менеджера

**Особенности:**
- Поддержка ViewBinding
- Grid layout с адаптивным количеством столбцов
- Selection mode (single/multi)
- Thumbnail loading через Glide
- Context menu на long press
- Swipe-to-select режим

**ViewHolder:**
- ImageView для превью
- TextView для имени файла
- TextView для размера/даты
- CheckBox для selection mode
- Selection overlay

#### ImagePagerAdapter.kt

**Назначение**: ViewPager2 адаптер для просмотра изображений

**Особенности:**
- Full-screen image viewing
- PhotoView library для zoom/pan
- Lazy loading изображений
- Memory-efficient caching

#### InspectionAdapter.java

**Назначение**: Адаптер списка инспекций

**Особенности:**
- Отображение деталей инспекции
- Click listeners для редактирования
- Swipe-to-delete

### Bottom Sheets

#### MoveFilesBottomSheet.kt

**Назначение**: Bottom sheet для перемещения файлов

**Компоненты:**
- RecyclerView с деревом папок через `FolderTreeAdapter`
- Кнопка создания новой папки
- Кнопка подтверждения перемещения
- Breadcrumb навигация

#### CameraSettingsDialogFragment

**Назначение**: Настройки камеры

**Секции:**
- Разрешение фото/видео
- Качество изображения
- Вспышка
- Формат timestamp
- Stabilization

### Compose UI Components

#### ZoomControl.kt

**Назначение**: Compose компонент для управления зумом

**Особенности:**
- Slider для zoom ratio
- Visual feedback
- Material 3 Design

#### FocusOverlayView.kt

**Назначение**: Overlay для визуализации фокусировки

**Состояния:**
- Idle (CAF)
- Focusing (анимированное кольцо)
- Focused (зеленое кольцо)
- Failed (красное кольцо)
- Locked (желтое кольцо с иконкой замка)

**Интеграция:**
```kotlin
val focusState by viewModel.state.collectAsState()
FocusOverlayView(state = focusState)
```

### Material Design Components

**Используемые компоненты:**
- `MaterialButton`
- `TextInputLayout` / `TextInputEditText`
- `FloatingActionButton`
- `BottomSheet`
- `Slider`
- `Switch`
- `Chip`
- `ProgressIndicator`

**Theme:**
- Material 3 (Material You)
- Dynamic colors support (Android 12+)
- Dark mode support

---

## Сборка и развертывание

### Gradle Configuration

#### Root `build.gradle.kts`

**Plugins:**
- Android Application Plugin: 8.9.2
- Android Library Plugin: 8.9.2
- Kotlin Android: 2.1.0
- Kotlin Compose Compiler: 2.1.0
- Hilt: 2.52
- KSP: 2.1.0-1.0.28
- Google Services: 4.4.2

#### App Module `build.gradle.kts`

**SDK Versions:**
```kotlin
compileSdk = 35
targetSdk = 34
minSdk = 27
```

**Build Types:**

**Debug:**
```kotlin
isMinifyEnabled = false
isDebuggable = true
```

**Release:**
```kotlin
isMinifyEnabled = true
isShrinkResources = true
signingConfig = signingConfigs.getByName("release")
proguardFiles(
    getDefaultProguardFile("proguard-android-optimize.txt"),
    "proguard-rules.pro"
)
```

**Signing Configuration:**
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("release.keystore")
        storePassword = "b1void123"
        keyAlias = "b1void"
        keyPassword = "b1void123"
    }
}
```

**Важно**: Эта конфигурация только для разработки. В продакшене использовать безопасное хранение ключей!

**Build Features:**
```kotlin
viewBinding = true
compose = true
```

**Java Compatibility:**
```kotlin
sourceCompatibility = JavaVersion.VERSION_11
targetCompatibility = JavaVersion.VERSION_11
jvmTarget = "11"
```

**MultiDex:**
```kotlin
multiDexEnabled = true
```

**NDK ABI Filters:**
```kotlin
ndk {
    abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
}
```

### Dependencies

**Core Android:**
```gradle
androidx.core:core-ktx:1.15.0
androidx.appcompat:appcompat:1.7.0
com.google.android.material:material:1.12.0
androidx.constraintlayout:constraintlayout:2.2.0
```

**Compose:**
```gradle
androidx.compose:compose-bom:2024.10.01
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.activity:activity-compose:1.9.3
androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7
```

**CameraX:**
```gradle
androidx.camera:camera-core:1.3.1
androidx.camera:camera-camera2:1.3.1
androidx.camera:camera-lifecycle:1.3.1
androidx.camera:camera-view:1.3.1
androidx.camera:camera-video:1.3.1
```

**Image Loading:**
```gradle
com.github.bumptech.glide:glide:4.16.0
io.getstream:photoview:1.0.3
```

**Data & Storage:**
```gradle
androidx.datastore:datastore-preferences:1.1.1
```

**Cloud & Network:**
```gradle
com.dropbox.core:dropbox-core-sdk:7.0.0
com.dropbox.core:dropbox-android-sdk:7.0.0
```

**Firebase:**
```gradle
com.google.firebase:firebase-database:21.0.0
com.google.firebase:firebase-auth:23.2.0
com.google.firebase:firebase-storage-ktx:21.0.1
```

**Background Work:**
```gradle
androidx.work:work-runtime-ktx:2.9.0
```

**Utilities:**
```gradle
com.google.code.gson:gson:2.10.1
com.h6ah4i.android.widget.verticalseekbar:verticalseekbar:1.0.0
com.github.yukuku:ambilwarna:2.0.1 (Color picker)
```

**Camera View (legacy):**
```gradle
com.otaliastudios:cameraview:2.7.2
```

**Testing:**
```gradle
junit:junit:4.13.2
androidx.test.ext:junit:1.2.1
androidx.test.espresso:espresso-core:3.6.1
```

### Команды сборки

**Debug Build:**
```bash
./gradlew assembleDebug
# Windows:
./gradlew.bat assembleDebug
```

**Release Build:**
```bash
./gradlew assembleRelease
```

**Clean Build:**
```bash
./gradlew clean
```

**Build с подробным выводом:**
```bash
./gradlew assembleDebug --warning-mode all
```

**Установка на устройство:**
```bash
./gradlew installDebug
```

### Lint и Code Quality

**Запуск Lint:**
```bash
./gradlew lint
```

**Lint для конкретного модуля:**
```bash
./gradlew :app:lintDebug
```

**Тихий режим:**
```bash
./gradlew :app:lintDebug --quiet
```

**Важно**: Необходимо устранить все warnings перед merge в main/master!

---

## Тестирование

### Unit Tests

**Расположение**: `src/test/`

**Фреймворки:**
- JUnit 4.13.2
- Kotlin Coroutines Test

**Запуск всех тестов:**
```bash
./gradlew test
```

**Запуск тестов модуля camera:**
```bash
./gradlew :feature:camera:test
```

**Примеры тестов:**
- `FocusCoordinatorTest.kt` — тесты логики фокусировки
- `FocusInteractorTest.kt` — тесты бизнес-логики

### Instrumentation Tests

**Расположение**: `src/androidTest/`

**Фреймворки:**
- Espresso 3.6.1
- AndroidX Test 1.2.1

**Запуск на устройстве:**
```bash
./gradlew connectedAndroidTest
```

**Требования:**
- Подключенное Android устройство или эмулятор
- USB debugging включен
- Достаточно памяти на устройстве

### Coverage Target

**Целевое покрытие**: >80% для нового кода

**Измерение coverage:**
```bash
./gradlew jacocoTestReport
```

---

## Безопасность и конфигурация

### Permissions

**Критически важные разрешения:**

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
                 android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
```

**Дополнительные:**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
```

**Runtime Permissions:**
- Camera и Audio запрашиваются при первом запуске CameraActivity
- Storage permissions обрабатываются в FileManagerActivity

### Sensitive Data

**⚠️ Важно: Следующие данные не должны коммититься в репозиторий!**

**1. Dropbox Access Token**

В `B1VoidApplication.kt`:
```kotlin
DropboxClientFactory.init("YOUR_ACCESS_TOKEN")
```

Заменить на реальный токен или хранить в:
- `local.properties`
- Secure vault (Azure Key Vault, AWS Secrets Manager)
- Environment variables для CI/CD

**2. Firebase Configuration**

Файл `app/google-services.json` содержит:
- API ключи
- Project ID
- OAuth client IDs

**Для продакшена:**
- Использовать разные конфигурации для debug/release
- Скрывать personalized данные перед sharing

**3. Signing Keystore**

Файл `app/release.keystore`:
- Никогда не коммитить в Git!
- Хранить в secure location
- Использовать CI/CD secrets для релизной сборки

**Best practices:**
```gitignore
*.keystore
*.jks
google-services.json
local.properties
```

### ProGuard Rules

**Файл**: `app/proguard-rules.pro`

**Основные правила:**
- Keep модели данных (Firebase, Gson)
- Keep библиотеки с reflection (Glide, Dropbox)
- Obfuscate бизнес-логику
- Keep native методы

### Network Security

**Конфигурация**: `res/xml/network_security_config.xml`

**Рекомендации:**
- Использовать HTTPS для всех запросов
- Pin certificates для критичных API
- Запретить cleartext traffic (кроме debug)

### File Provider Security

**Конфигурация**: `res/xml/file_paths.xml`

**Принципы:**
- Минимальный доступ к файлам
- Temporary grant URI permissions
- Использовать `grantUriPermissions="true"`

---

## Git и Version Control

### Commit Guidelines

**Формат коммитов**: Conventional Commits на русском языке

**Типы:**
- `feat:` — новая функциональность
- `fix:` — исправление бага
- `docs:` — обновление документации
- `refactor:` — рефакторинг без изменения функциональности
- `perf:` — улучшение производительности
- `test:` — добавление или обновление тестов
- `chore:` — обновление зависимостей, конфигурации

**Примеры:**
```
feat: Добавлена интеллектуальная стабилизация изображения (OIS/EIS)
fix: Исправлен баг с неправильным разрешением камеры
refactor: Оптимизирован алгоритм сжатия изображений
```

**Правила:**
- Заголовок не более 72 символов
- Описательное сообщение (focus на "why", а не "what")
- Один коммит = одна логическая единица изменений

### Pull Request Guidelines

**Требования к PR:**

1. **Название**: Четкое описание изменений на русском
2. **Описание**: Секции:
   - Summary (краткое описание)
   - Changes (список изменений)
   - Screenshots/Videos (для UI изменений)
   - Testing (как тестировали)
3. **Линки**: Связь с issues/tasks
4. **Checklist**:
   - [ ] Lint passed (`./gradlew lint`)
   - [ ] Tests passed (`./gradlew test`)
   - [ ] Manual testing completed
   - [ ] No sensitive data in commit

**Special Notes:**
- Если затрагивается Firebase/Dropbox/Signing — явно указать в описании
- Для изменений в памяти/производительности — прикрепить metrics

### Branching Strategy

**Main Branches:**
- `master` — production-ready код
- `develop` — integration branch для features

**Feature Branches:**
```
feature/camera-stabilization
fix/memory-leak-filemanager
refactor/compose-migration
```

**Hotfix Branches:**
```
hotfix/crash-on-low-memory
```

---

## Дополнительные ресурсы

### Структура файлов ресурсов

**Layouts** (`res/layout/`):
- 42+ XML layout файлов
- Naming convention: `activity_*.xml`, `dialog_*.xml`, `item_*.xml`, `bottom_sheet_*.xml`

**Drawable** (`res/drawable/`):
- Icons для кнопок
- Backgrounds
- Selectors для states

**Values** (`res/values/`):
- `strings.xml` — текстовые ресурсы
- `colors.xml` — цветовая палитра
- `themes.xml` — темы приложения (Material 3)
- `dimens.xml` — размеры

**XML** (`res/xml/`):
- `file_paths.xml` — FileProvider пути
- `network_security_config.xml` — сетевая безопасность
- `backup_rules.xml` / `data_extraction_rules.xml` — правила бэкапа

### Assets

**Папка** `assets/`:
- Вспомогательные файлы
- Шрифты (если есть)
- Configuration JSONs

### WorkManager Tasks

**Используемые Worker'ы:**
- File synchronization с Dropbox
- Batch image optimization
- Cleanup старых файлов из корзины

**Конфигурация в B1VoidApplication:**
```kotlin
override val workManagerConfiguration: Configuration
    get() = Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.INFO)
        .setMaxSchedulerLimit(if (isLowRam) 2 else 3)
        .build()
```

---

## Известные особенности и ограничения

### Ограничения производительности

1. **Low-end Devices:**
   - Ограниченное разрешение камеры (до 512px на слабых устройствах)
   - RGB_565 формат вместо ARGB_8888
   - Уменьшенный размер Glide cache

2. **Memory Management:**
   - Агрессивная очистка кэша при low memory
   - Possible GC pauses на старых устройствах
   - Thumbnails генерируются асинхронно

### Совместимость

**Поддерживаемые версии Android:**
- Минимум: API 27 (Android 8.1 Oreo)
- Целевая: API 34 (Android 14)
- Компиляция: SDK 35

**Известные проблемы:**
- Camera2 API работает по-разному на Samsung/Xiaomi/Huawei
- OIS/EIS может не работать на бюджетных устройствах
- MIUI требует дополнительных разрешений для background work

### Deprecated API

**В коде присутствуют:**
```kotlin
@Deprecated("Use getOptimalImageSize() instead")
const val MAX_IMAGE_SIZE = 1024

@Deprecated("Use settingsManager instead")
private lateinit var sharedPreferences: SharedPreferences
```

**Рекомендация**: Мигрировать на новые API при рефакторинге.

---

## Roadmap и будущие улучшения

### Планируемые функции

1. **Full Compose Migration:**
   - Миграция всех Activities на Compose
   - Единая UI система

2. **Hilt в основном модуле:**
   - Расширение DI на весь проект
   - Упрощение тестирования

3. **Cloud Sync Improvements:**
   - Поддержка Google Drive
   - Incremental sync
   - Conflict resolution

4. **Advanced Camera Features:**
   - RAW capture
   - HDR mode
   - Night mode

5. **Offline-first Architecture:**
   - Room Database для metadata
   - Sync queue для Dropbox
   - Conflict resolution UI

### Технический долг

- Миграция Java кода на Kotlin
- Рефакторинг legacy Activities
- Улучшение test coverage
- Code documentation (KDoc)
- Migration to Kotlin Flow (вместо LiveData в некоторых местах)

---

## Контакты и поддержка

**Документация обновлена**: 2025-10-26

**Для вопросов и предложений:**
- Создайте Issue в репозитории
- Используйте conventional commit format
- Прикрепляйте logs и screenshots

**При багах указывайте:**
- Модель устройства
- Версия Android
- Steps to reproduce
- Logcat output

---

## Лицензия

_[Указать тип лицензии проекта]_

---

**Конец документации**
