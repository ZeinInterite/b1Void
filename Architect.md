B1Void 2.0: Target Architecture Design
1. Глобальная стратегия и принципы
   Single Activity & 100% Compose: Полный отказ от XML и Fragments. Одна MainActivity служит контейнером для NavHost.

Feature-Based Modularization: Модули делятся не по слоям (data/domain/ui), а по фичам. Это ускоряет сборку и изолирует код.

Strict Layer Separation: Domain-слой — это чистый Kotlin. Никакого android.* контекста.

Hardware Abstraction: Камера и файловая система — это такие же "источники данных", как и REST API. Они должны быть скрыты за интерфейсами.

2. Структура модулей (Gradle Modules)
   Мы переходим от монолита к четкой иерархии:

text
:app (DI Root, Navigation Graph)
├── :feature:auth      (Login, Firebase Auth)
├── :feature:camera    (CameraX impl, Image Capture UI)
├── :feature:gallery   (Image viewing, zooming)
├── :feature:sync      (Dropbox/Firebase upload logic)
├── :core:domain       (Global UseCases, Models - if needed shared)
├── :core:data         (Repositories impl, Database, Network)
├── :core:network      (Retrofit, Firebase wrappers)
├── :core:camera       (CameraX wrappers - HARDWARE ABSTRACTION)
├── :core:ui           (Design System, Theme, Common Composables)
└── :core:common       (Dispatchers, Extensions, Result wrappers)
Ключевое изменение: Выделение :core:camera. Логика работы с "железом" выносится из фичи в ядро. Фича :feature:camera занимается только UI, а :core:camera предоставляет чистый API для управления устройством.

3. Детальная проработка слоев (Clean Architecture)
   3.1. UI Layer (Presentation)
   Паттерн: MVI (Model-View-Intent) или MVVM с Unidirectional Data Flow.

Technology: Jetpack Compose.

State: Каждый экран имеет один data class UiState.

Events: UI отправляет UiAction во ViewModel.

ViewModel: Не знает о существовании CameraX или Firebase. Она общается только с UseCase.

3.2. Domain Layer (Business Logic)
Правило: Чистый Kotlin модуль. Никаких Android зависимостей.

Use Cases: Атомарные операции.

CapturePhotoUseCase

SyncPendingPhotosUseCase

GetGalleryPhotosUseCase

Models: Чистые data classes (без аннотаций JSON или Room).

3.3. Data Layer (Implementation)
Здесь мы решаем главные проблемы вашего текущего проекта.

A. Решение проблемы с Камерой (Hardware Abstraction)
Вместо того чтобы писать код CameraX внутри Activity или ViewModel, мы создаем абстракцию.

Interface (в Domain):

kotlin
interface CameraController {
suspend fun takePicture(): Result<PhotoModel>
fun setZoom(level: Float)
fun toggleFlash(isEnabled: Boolean)
}
Implementation (в Data/Core): CameraXControllerImpl.

Здесь живет CameraX.

Здесь живут "хаки" для Xiaomi.

Устранение дублирования: Библиотека CameraView (Otalia) полностью удаляется. Используется только CameraX.

B. Решение проблемы "Device Specific Hacks"
Создаем отдельный компонент DeviceCompatibilityManager.

kotlin
class DeviceQuirksManager @Inject constructor() {
fun shouldApplyXiaomiFix(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    // Этот метод вызывается внутри CameraXControllerImpl, 
    // не загрязняя бизнес-логику.
    fun applyFocusWorkaround(cameraControl: CameraControl) { ... }
}
C. Решение проблемы с Cloud Sync
Используем Repository Pattern для объединения локального кеша и облака.

PhotoRepository:

Сохраняет фото локально (Room/DataStore).

Помечает флаг isSynced = false.

Запускает WorkManager для фоновой синхронизации.

SyncStrategy: Интерфейс для поддержки разных облаков.

interface CloudStorage { suspend fun upload(file: File) }

Реализации: FirebaseStorageImpl, DropboxStorageImpl.

4. Решение проблем многопоточности (Threading Strategy)
   Чтобы избежать ANR и блокировок UI, внедряем строгую инъекцию диспетчеров.

Создаем аннотации для Hilt:

kotlin
@Qualifier annotation class IoDispatcher
@Qualifier annotation class MainDispatcher
@Qualifier annotation class DefaultDispatcher
Использование в Repository/DataSource:

kotlin
class CameraDataSource @Inject constructor(
@IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
suspend fun saveImage(bytes: ByteArray) = withContext(ioDispatcher) {
// Тяжелая операция ввода-вывода
// Безопасно, даже если вызвано с Main потока
}
}
Запрет: Прямое использование Dispatchers.Main или Dispatchers.IO в коде. Только через инъекцию (это упрощает тесты).

5. Стек технологий (Tech Stack 2026)
   Компонент	Выбор архитектора	Обоснование
   DI	Hilt	Стандарт индустрии, отличная интеграция с Compose и WorkManager.
   Async	Coroutines + Flow	Полный отказ от RxJava и Callbacks.
   Network	Retrofit + OkHttp	Для REST API.
   Sync	WorkManager	Для надежной загрузки фото в Dropbox/Firebase в фоне (гарантированное выполнение).
   Image Loading	Coil	Легче и "роднее" для Compose, чем Glide.
   Database	Room	Для хранения метаданных фото и статуса синхронизации.
   Build	Kotlin DSL + Version Catalogs	Централизованное управление зависимостями (libs.versions.toml).
