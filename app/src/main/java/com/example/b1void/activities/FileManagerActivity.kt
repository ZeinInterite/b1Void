
package com.example.b1void.activities

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.b1void.R
import com.example.b1void.adapters.FileAdapter
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import kotlin.concurrent.thread

class FileManagerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var createFolderButton: Button
    private lateinit var fileAdapter: FileAdapter
    private val directoryStack: LinkedList<File> = LinkedList()
    private lateinit var appDirectory: File
    private lateinit var zipDirectory: File
    private lateinit var captureButton: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var shareButton: Button
    private lateinit var deleteButton: Button
    private lateinit var moveButton: Button
    private lateinit var titleTextView: TextView
    private lateinit var progressBar: SeekBar
    private lateinit var buttonContainer : LinearLayout
    private lateinit var sharedPreferences: SharedPreferences
    private var currentProgress = 0

    private val OPEN_FILE = 1

    private var imageUri: Uri? = null

    private var imgGalUriString: String? = null
    private var imgGalUri: Uri? = null

    private var isSelectionMode = false
    private val selectedFiles = mutableSetOf<File>()

    private var currentFileForMenu: File? = null

    private var sortAscending = false

    companion object {
        private const val PREF_SEEK_BAR_PROGRESS = "seek_bar_progress"
    }

    // Самый первый метод, который вызывается при создании экрана. Тут инициализируется вся хуйня.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        // Цепляем все элементы UI из макета (activity_file_manager.xml) к переменным в коде.
        // Чтобы потом с ними взаимодействовать: менять текст, обрабатывать нажатия и т.д.
        recyclerView = findViewById(R.id.recycler_view)
        createFolderButton = findViewById(R.id.create_folder_button)
        captureButton = findViewById(R.id.capture_button)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        shareButton = findViewById(R.id.share_button)
        deleteButton = findViewById(R.id.delete_button)
        moveButton = findViewById(R.id.move_button)
        titleTextView = findViewById(R.id.titleTextView)
        val uploadButton = findViewById<View>(R.id.upload_button)
        progressBar = findViewById(R.id.progressBar)
        val sortButton: ImageButton = findViewById(R.id.sort_button)
        buttonContainer = findViewById(R.id.button_container)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Восстановление прогресса SeekBar из SharedPreferences
        currentProgress = sharedPreferences.getInt(PREF_SEEK_BAR_PROGRESS, 0)
        progressBar.progress = currentProgress


        sortButton.setOnClickListener {
            toggleSortOrder()
        }

        // Ловим URI изображения, если его передали из другого активити.
        // Например, из галереи, когда дрочила выбрал картинку и нажал "Поделиться" -> "InspectorApp".
        if (intent.getStringExtra("imageUri") != null) {
            imgGalUriString = intent.getStringExtra("imageUri")
            imgGalUri = Uri.parse(imgGalUriString)

            // Если URI есть, то сохраняем в папку, в которой находимся
            showCreateFolderDialog { newDir ->
                saveImageToDirectory(newDir)
            }
        }

        // Вешаем слушателя на кнопку загрузки. При нажатии открываем стандартный диалог выбора файла (изображения) из галереи.
        // OPEN_FILE - это код запроса, чтобы потом понять, откуда пришел результат.
        uploadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, OPEN_FILE)
        }

        // Вешаем слушателя на остальные кнопки
        captureButton.setOnClickListener {
            val intent = Intent(this, CameraV2Activity::class.java)
            // передаем абсолютный путь до директории куда сохранять фотки
            intent.putExtra("save_path", getCurrentDirectory().absolutePath)
            startActivity(intent)
        }

        shareButton.setOnClickListener {
            shareSelectedFiles()
        }

        deleteButton.setOnClickListener {
            deleteSelectedFiles()
        }

        moveButton.setOnClickListener {
            showMoveDialog()
        }

        progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentProgress = progress
                    updateProgress(progress)
                    // Сохранение прогресса SeekBar в SharedPreferences
                    sharedPreferences.edit().putInt(PREF_SEEK_BAR_PROGRESS, progress).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        // Настраиваем RecyclerView: используем GridLayoutManager для отображения файлов в виде сетки.
        val gridLayoutManager = GridLayoutManager(this, 4)
        recyclerView.layoutManager = gridLayoutManager

        // Определяем папку приложения, где будут храниться все файлы.
        // filesDir - это стандартная папка приложения, доступная только ему.
        appDirectory = File(filesDir, "InspectorAppFolder")

        // Создаем папку приложения, если ее еще нет. Обрабатываем возможные ошибки при создании.
        if (!appDirectory.exists()) {
            try {
                if (appDirectory.mkdirs()) {
                    Toast.makeText(this, "Папка приложения создана", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Не удалось создать папку приложения",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating directory: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка: Недостаточно прав для создания папки",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating directory: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка ввода/вывода при создании папки",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        // Определяем папку для временных zip файлов.
        zipDirectory = File(filesDir, "zipFolder")

        // Создаем папку для zip файлов, если ее еще нет.
        if (!zipDirectory.exists()) {
            try {
                if (zipDirectory.mkdirs()) {
                    Toast.makeText(this, "Папка zip-файлов создана", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        this,
                        "Не удалось создать папку zip-файлов",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating directory: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка: Недостаточно прав для создания папки zip-файлов ",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating directory: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка ввода/вывода при создании папки zip-файлов",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Загружаем содержимое папки приложения в RecyclerView.
        loadDirectoryContent(appDirectory)

        // Слушатели
        createFolderButton.setOnClickListener {
            showCreateFolderDialog { newDir ->
                loadDirectoryContent(getCurrentDirectory())
            }
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadDirectoryContent(getCurrentDirectory())
        }
    }

    private fun toggleSortOrder() {
        sortAscending = !sortAscending
        val sortButton: ImageView = findViewById(R.id.sort_button)

        if (sortAscending) {
            sortButton.scaleY = -1f  // Отражение по вертикали
        } else {
            sortButton.scaleY = 1f // Вернуть в исходное состояние
            sortButton.translationY = 0f
        }

        loadDirectoryContent(getCurrentDirectory())
    }

    // Функция, которая вызывается, когда возвращаемся из другого активити (например, из галереи).
    // requestCode - это код запроса, который мы передавали при запуске другого активити (OPEN_FILE).
    // resultCode - это результат работы другого активити (RESULT_OK - успешно, RESULT_CANCELED - отменено).
    // data - это данные, которые вернуло другое активити (например, URI выбранного файла).
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Проверяем, что вернулись из активити выбора файла (requestCode == OPEN_FILE),
        // что все прошло успешно (resultCode == RESULT_OK) и что нам вообще вернули какие-то данные (data != null).
        if (requestCode == OPEN_FILE && resultCode == Activity.RESULT_OK && data != null) {
            // Если выбрали несколько файлов, то получаем информацию о них из data.clipData.
            if (data.clipData != null) {
                val clipData = data.clipData
                val count = clipData!!.itemCount

                // Создаем список URI для всех выбранных файлов.
                val uris = mutableListOf<Uri>()
                for (i in 0 until count) {
                    val imageUri = clipData.getItemAt(i).uri
                    uris.add(imageUri)
                }

                // сохраняем в папку, в которой находимся (исправил создание новой папки)
                saveImagesToDirectory(getCurrentDirectory(), uris)


            } else if (data.data != null) {
                // Если выбрали только один файл, то получаем его URI из data.data.
                imageUri = data.data
                val uris = mutableListOf<Uri>()
                uris.add(imageUri!!)
                saveImagesToDirectory(getCurrentDirectory(), uris)
            }
        }
    }

    // Функция для сохранения списка изображений в указанную папку.
    private fun saveImagesToDirectory(directory: File, uris: List<Uri>) {
        // Создаем имя файла: "Image-" + текущее время + ".jpg".
        // Чтобы файлы не перезаписывались, используем уникальное имя.
        // file - это объект File, представляющий файл, который мы будем создавать. (Умный, ага)
        for (uri in uris) {
            val fname = "Image-" + System.currentTimeMillis() + ".jpg"
            val file = File(directory, fname)

            // contentResolver - это объект, который позволяет получать доступ к данным приложения, в том числе и к файлам.
            // openInputStream(uri) - открывает поток для чтения данных из файла, на который указывает URI.
            // inputStream - это поток, из которого мы будем читать данные изображения.
            try {
                // Получаем InputStream из URI изображения
                val inputStream = contentResolver.openInputStream(uri)

                // Создаем OutputStream для записи данных в файл
                val outputStream = FileOutputStream(file)
                // FileOutputStream(file) - создает поток для записи данных в файл.
                // outputStream - это поток, в который мы будем записывать данные изображения.

                // Буфер для чтения и записи данных (рекомендуемый размер)
                val buffer = ByteArray(4096) // 4KB
                // Создаем буфер для чтения и записи данных. Буфер - это кусок памяти, куда мы временно сохраняем данные.
                // Размер буфера - 4096 байт (4 КБ).


                // Читаем данные из inputStream в буфер, пока не дойдем до конца файла
                // read(buffer) - читает данные из inputStream в буфер
                // bytesRead - количество прочитанных байт
                // write(buffer, 0, bytesRead) - записывает данные из буфера в outputStream
                var bytesRead: Int
                while (inputStream!!.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                // Закрываем потоки, иначе все пойдет по пизде
                outputStream.close()
                inputStream.close()

                // runOnUiThread - запускает код в основном потоке. Это необходимо, потому что всплывашку можно показывать только из основного потока
                // loadDirectoryContent(directory) - обновляет список файлов в RecyclerView
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Изображение сохранено в: ${file.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                    loadDirectoryContent(directory)
                }

                // Обрабатываем проеьы при работе с файлами
                // e.printStackTrace() - выводит информацию об проебе в консоль (logcat)
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    // Показываем сообщение об проебе
                    Toast.makeText(this, "Ошибка при сохранении изображения", Toast.LENGTH_SHORT)
                        .show()
                }
                return
            }
        }

        runOnUiThread {
            loadDirectoryContent(directory) // Обновляем контент
        }
    }

    // Функция для сохранения одного изображения в указанную папку (когда шарят из галереи).
    // Все то же самое, что и в saveImagesToDirectory, но только для одного файла.
    // directory: File - папка, куда сохраняем.
    private fun saveImageToDirectory(directory: File) {
        val fname = "Image-" + System.currentTimeMillis() + ".jpg"
        val file = File(directory, fname)

        try {
            val inputStream = contentResolver.openInputStream(imgGalUri!!)

            val outputStream = FileOutputStream(file)

            val buffer = ByteArray(4096) // 4KB

            var bytesRead: Int
            while (inputStream!!.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.close()
            inputStream.close()

            runOnUiThread {
                Toast.makeText(
                    this,
                    "Изображение сохранено в: ${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
                loadDirectoryContent(directory)
            }

        } catch (e: IOException) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Ошибка при сохранении изображения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Создаем контекстное меню, которое появляется при долгом тапании (хомяка) на файл.
    // menu: ContextMenu? - меню, которое нужно создать.
    // v: View? - View, на котором произошло долгое тапание.
    // menuInfo: ContextMenu.ContextMenuInfo? - дополнительная информация о меню.
    override fun onCreateContextMenu(
        menu: ContextMenu?,
        v: View?,
        menuInfo: ContextMenu.ContextMenuInfo?
    ) {
        super.onCreateContextMenu(menu, v, menuInfo)
        menuInflater.inflate(R.menu.file_context_menu, menu)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_rename -> {
                showRenameDialog(currentFileForMenu!!)
                true
            }

            R.id.action_delete -> {
                deleteFile(currentFileForMenu!!)
                true
            }

            R.id.action_share_single -> {
                shareFile(currentFileForMenu!!)
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    // Получаем текущую папку, которую сейчас отображаем.
    // Если directoryStack пуст (то есть мы находимся в корневой папке), то возвращаем appDirectory.
    // directoryStack - это список папок, в которых мы побывали.
    // lastOrNull() - возвращает последний элемент списка или null, если список пуст.
    // А если там null, то возвращаем папку нашего приложения. Вроде логично.
    private fun getCurrentDirectory(): File {
        return directoryStack.lastOrNull() ?: appDirectory
    }

    // Включаем анимацию обновления списка. Чтобы дрочила видел, что что-то происходит.
    private fun loadDirectoryContent(directory: File) {
        swipeRefreshLayout.isRefreshing = true
        thread { // Run the loading in a background thread
            if (directory.exists() && directory.isDirectory) {
                val filesAndDirs = directory.listFiles()?.toList() ?: emptyList()

                // Сортировка файлов по дате изменения
                val sortedFilesAndDirs = if (sortAscending) {
                    filesAndDirs.sortedBy { it.lastModified() } // От старых к новым
                } else {
                    filesAndDirs.sortedByDescending { it.lastModified() } // От новых к старым
                }

                runOnUiThread { // Post the UI update back to the main thread
                    if (!this::fileAdapter.isInitialized) {
                        fileAdapter = FileAdapter(
                            sortedFilesAndDirs,  // Use the sorted list here
                            this,
                            { file -> // single click
                                onItemClick(file)
                            },
                            { file ->  //long click
                                onItemLongClick(file)
                            },
                            isSelectionMode = isSelectionMode,
                            selectedFiles = selectedFiles // Pass selectedFiles to the adapter
                        )
                        recyclerView.adapter = fileAdapter
                        fileAdapter.setProgress(currentProgress)
                    } else {
                        fileAdapter.isSelectionMode = isSelectionMode
                        fileAdapter.selectedFiles = selectedFiles
                        fileAdapter.updateFiles(sortedFilesAndDirs)  // Update with the sorted list
                    }
                    title = directory.name
                    if(title != appDirectory.name) {
                        titleTextView.text = title
                    } else {
                        titleTextView.text = "DOCUMENT   LLC"
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@FileManagerActivity, "Папка не найдена", Toast.LENGTH_SHORT).show()
                }
            }
            runOnUiThread {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
    private fun onItemClick(file: File) {
        if (isSelectionMode) {
            toggleFileSelection(file)
        } else if (file.isDirectory) {
            openDirectory(file)
        } else {
            if (fileAdapter.isImage(file)) {
                openImagePreview(file)
            } else {
                Toast.makeText(
                    this,
                    "Выбран файл: ${file.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun onItemLongClick(file: File) {
        if (!isSelectionMode) {
            startSelectionMode()
            toggleFileSelection(file)
        }
    }

    // Открываем превью изображения.
    // imageFile: File - файл изображения, которое нужно открыть.
    // Создаем Intent для открытия ImagePreviewActivity.
    // Передаем путь к изображению в Intent.
    // Запускаем ImagePreviewActivity.
    // просто, как три рубля.
    private fun openImagePreview(imageFile: File) {
        val intent = Intent(this, ImagePreviewActivity::class.java)
        val imagePaths = arrayListOf(imageFile.absolutePath)
        intent.putStringArrayListExtra("image_paths", imagePaths)
        intent.putExtra("current_image_index", 0) // Начинаем с нулевой фотки
        startActivity(intent)
    }

    // Открываем папку.
    // file: File - папка, которую нужно открыть.
    // Добавляем папку в список открытых папок (directoryStack).
    // Загружаем содержимое папки в RecyclerView.
    private fun openDirectory(file: File) {
        if (directoryStack.isEmpty() || directoryStack.lastOrNull() != file) {
            directoryStack.add(file)
        }
        loadDirectoryContent(file)
    }

    // Обрабатываем нажатие кнопки "Назад"
    // Если включен режим выделения, то отключаем его
    // Если есть открытые папки в списке (directoryStack), то открываем предыдущую папку
    // Если нет открытых папок, то вызываем метод суперкласса (закрываем активити)
    // Чтобы пользователь мог вернуться назад по истории открытых папок
    override fun onBackPressed() {
        if (isSelectionMode) {
            clearSelection()
        } else if (directoryStack.isNotEmpty()) {
            directoryStack.removeLast()
            val previousDirectory = directoryStack.lastOrNull() ?: appDirectory
            loadDirectoryContent(previousDirectory)
        } else {
            super.onBackPressed()
        }
    }

    // Показываем диалог создания новой папки
    // onFolderCreated: (File) -> Unit - функция, которая будет вызвана после создания папки
    // Это колбэк, который позволяет нам выполнить какие-то действия после создания папки
    // Например, обновить список файлов
    private fun showCreateFolderDialog(onFolderCreated: (File) -> Unit) {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        builder.setTitle("Создать новую папку")
        builder.setView(input)

        builder.setPositiveButton("Создать") { dialog, _ ->
            val folderName = input.text.toString()
            val newDir = File(getCurrentDirectory(), folderName)

            // Пытаемся создать папку
            // mkdir() - создает новую папку. (Линукс ядро - привет!)
            // Если папка создана успешно, то вызываем колбэк onFolderCreated
            // Если папка не создана, то показываем сообщение об ошибке
            try {
                if (newDir.mkdir()) {
                    onFolderCreated(newDir)
                    loadDirectoryContent(getCurrentDirectory()) // Обновляем контент текущей директории
                } else {
                    Toast.makeText(this, "Ошибка при создании папки", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException creating folder: ${e.message}")
                Toast.makeText(this, "Ошибка безопасности при создании папки", Toast.LENGTH_SHORT)
                    .show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException creating folder: ${e.message}")
                Toast.makeText(this, "Ошибка ввода/вывода при создании папки", Toast.LENGTH_SHORT)
                    .show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    // понятно
    private fun showRenameDialog(file: File) {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)
        input.setText(file.name)
        builder.setTitle("Переименовать")
        builder.setView(input)

        builder.setPositiveButton("Переименовать") { dialog, _ ->
            val newName = input.text.toString()


            val newFile = File(file.parentFile, newName)

            try {
                if (file.renameTo(newFile)) {
                    loadDirectoryContent(getCurrentDirectory())
                } else {
                    Toast.makeText(this, "Ошибка при переименовании", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                Log.e("FileManager", "SecurityException renaming file: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка безопасности при переименовании файла",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: IOException) {
                Log.e("FileManager", "IOException renaming file: ${e.message}")
                Toast.makeText(
                    this,
                    "Ошибка ввода/вывода при переименовании файла",
                    Toast.LENGTH_SHORT
                ).show()
            }

            dialog.dismiss()
        }
        builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    // это тоже.
    fun deleteFile(file: File) {
        try {
            if (file.isDirectory) {
                deleteDirectory(file)
            } else {
                if (file.delete()) {
                    Log.d("File Manager", "File ${file.name} deleted successfully")
                } else {
                    Log.e("File Manager", "Error deleting file ${file.name}")
                }
            }
//            deleteFromDropbox(file)
            loadDirectoryContent(getCurrentDirectory())
        } catch (e: SecurityException) {
            Log.e("FileManager", "SecurityException deleting file: ${e.message}")
        }
    }

    // Сносим директорию рекурсивно
    private fun deleteDirectory(directory: File): Boolean {
        val files = directory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    if (!file.delete()) {
                        Log.e("FileManager", "Failed to delete file: " + file.absolutePath)
                        return false
                    }
                }
            }
        }
        return directory.delete()
    }


    // Функция для обмена файлом с другими приложениями
    // Используем ShareCompat.IntentBuilder, чтобы упростить создание Intent для обмена файлом
    // Сначала проверяем, является ли файл папкой
    // Если файл - папка, то архивируем ее в zip файл и создаем URI для zip файла
    // Если файл - не папка, то просто создаем URI для файла
    // Потом создаем Intent для обмена файлом и показываем диалоговое окно выбора приложения для обмена файлом
    // Если юзер захочет поделиться файлом, то все произойдет как по маслу
    private fun shareFile(file: File) {

        val mimeType: String

        val uri: Uri

        try {
            if (file.isDirectory) {

                val zipFileName = "${file.name}.zip"
                val zipFile = File(zipDirectory, zipFileName)

                ZipFile(zipFile).addFolder(file)


                uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    zipFile
                )
                mimeType = "application/zip"

                zipFile.deleteOnExit()

            } else {
                uri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                mimeType = "image/*"
            }

            ShareCompat.IntentBuilder(this)
                .setStream(uri)
                .setType(mimeType)
                .setChooserTitle("Поделиться файлом")
                .startChooser()
        } catch (e: IllegalArgumentException) {
            Log.e("FileManager", "FileProvider error: ${e.message}")
            Toast.makeText(this, "Ошибка при обмене файлом", Toast.LENGTH_SHORT).show()
        }
    }

    // Multiple Selection magic (its really magic? bro)

    private fun startSelectionMode() {
        // isSelectionMode - это переменная, которая указывает, включен ли режим выделения.
        isSelectionMode = true

        buttonContainer.visibility = View.VISIBLE

        shareButton.visibility = View.VISIBLE
        deleteButton.visibility = View.VISIBLE // Показываем кнопку удаления
        moveButton.visibility = View.VISIBLE   // Показываем кнопку перемещения
        loadDirectoryContent(getCurrentDirectory())
    }

    private fun clearSelection() {
        isSelectionMode = false

        buttonContainer.visibility = View.GONE
        shareButton.visibility = View.GONE
        deleteButton.visibility = View.GONE  // Скрываем кнопку удаления
        moveButton.visibility = View.GONE    // Скрываем кнопку перемещения
        selectedFiles.clear()
        loadDirectoryContent(getCurrentDirectory())
    }

    private fun toggleFileSelection(file: File) {
        if (selectedFiles.contains(file)) {
            selectedFiles.remove(file)
        } else {
            selectedFiles.add(file)
        }
        fileAdapter.notifyItemChanged(fileAdapter.files.indexOf(file))
        if (selectedFiles.isEmpty()) {
            clearSelection()
        }
    }

    private fun deleteSelectedFiles() {
        if (selectedFiles.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Удалить выбранные элементы?")
                .setMessage("Вы уверены, что хотите удалить выбранные элементы?")
                .setPositiveButton(android.R.string.yes) { dialog, which ->
                    Thread {
                        selectedFiles.forEach { file ->
                            try {
                                if (file.isDirectory) {
                                    deleteDirectory(file)
                                } else {
                                    if (file.delete()) {
                                        Log.d(
                                            "File Manager",
                                            "File ${file.name} deleted successfully"
                                        )
                                    } else {
                                        Log.e("File Manager", "Error deleting file ${file.name}")
                                        runOnUiThread {
                                            Toast.makeText(
                                                this@FileManagerActivity,
                                                "Не удалось удалить файл ${file.name}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                runOnUiThread { // Обновляем UI в основном потоке после каждого удаления
                                    loadDirectoryContent(getCurrentDirectory()) // Обновляем RecyclerView
                                }

                            } catch (e: SecurityException) {
                                Log.e(
                                    "FileManager",
                                    "SecurityException deleting file: ${e.message}"
                                )
                                runOnUiThread {
                                    Toast.makeText(
                                        this@FileManagerActivity,
                                        "Ошибка безопасности при удалении файла ${file.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        runOnUiThread { // Обновление UI в основном потоке
                            Toast.makeText(
                                this@FileManagerActivity,
                                "Выбранные файлы удалены",
                                Toast.LENGTH_SHORT
                            ).show()
                            clearSelection()
                            loadDirectoryContent(getCurrentDirectory())
                        }
                    }.start()
                }
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
        } else {
            Toast.makeText(this, "Не выбраны файлы для удаления", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showMoveDialog() {
        if (selectedFiles.isNotEmpty()) {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Выберите папку для перемещения")

            // Создаем список папок для отображения в диалоговом окне
            val directories = getCurrentDirectory().listFiles { file -> file.isDirectory }
            val directoryNames = directories?.map { it.name }?.toTypedArray() ?: emptyArray()

            if (directoryNames.isNotEmpty()) {
                builder.setItems(directoryNames) { dialog, which ->
                    val destinationDirectory = directories!![which]
                    moveSelectedFiles(destinationDirectory)
                    dialog.dismiss()
                }

                builder.setNegativeButton("Отмена") { dialog, _ -> dialog.cancel() }
                builder.show()
            } else {
                Toast.makeText(this, "Нет доступных папок для перемещения", Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            Toast.makeText(this, "Не выбраны файлы для перемещения", Toast.LENGTH_SHORT).show()
        }
    }

    private fun moveSelectedFiles(destinationDirectory: File) {
        Thread {
            selectedFiles.forEach { file ->
                val newFile = File(destinationDirectory, file.name)
                try {
                    if (file.renameTo(newFile)) {
                        Log.d(
                            "FileManager",
                            "File ${file.name} moved successfully to ${destinationDirectory.absolutePath}"
                        )
                    } else {
                        Log.e(
                            "FileManager",
                            "Error moving file ${file.name} to ${destinationDirectory.absolutePath}"
                        )
                        runOnUiThread { // Обновление UI в основном потоке
                            Toast.makeText(
                                this@FileManagerActivity,
                                "Ошибка при перемещении файла ${file.name}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e("FileManager", "SecurityException moving file: ${e.message}")
                    runOnUiThread { // Обновление UI в основном потоке
                        Toast.makeText(
                            this,
                            "Ошибка безопасности при перемещении файла ${file.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: IOException) {
                    Log.e("FileManager", "IOException moving file: ${e.message}")
                    runOnUiThread { // Обновление UI в основном потоке
                        Toast.makeText(
                            this,
                            "Ошибка ввода/вывода при перемещении файла ${file.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            runOnUiThread { // Обновление UI в основном потоке
                Toast.makeText(
                    this@FileManagerActivity,
                    "Выбранные файлы перемещены",
                    Toast.LENGTH_SHORT
                ).show()
                clearSelection()
                loadDirectoryContent(getCurrentDirectory())
            }
        }.start()
    }


    private fun onFileSelectionChanged(file: File, isSelected: Boolean) {
        if (isSelected) {
            selectedFiles.add(file)
        } else {
            selectedFiles.remove(file)
        }

        if (isSelectionMode && selectedFiles.isEmpty()) {
            clearSelection()
        }

        shareButton.visibility = if (selectedFiles.isNotEmpty()) View.VISIBLE else View.GONE
    }


    private fun shareSelectedFiles() {
        if (selectedFiles.isNotEmpty()) {
            Thread {
                val filesUris = ArrayList<Uri>()
                val filesToDelete = mutableListOf<File>() // Список файлов для удаления
                val tempDir = File(cacheDir, "temp_zip")

                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }

                var errorOccurred = false

                try {
                    for (file in selectedFiles) {
                        try {
                            if (file.isDirectory) {
                                val zipFile = File(tempDir, "${file.name}.zip")
                                zipFolder(file, zipFile)

                                if (zipFile.exists()) {
                                    val uri = FileProvider.getUriForFile(
                                        this,
                                        "${packageName}.fileprovider",
                                        zipFile
                                    )
                                    filesUris.add(uri)
                                    filesToDelete.add(zipFile) // Добавляем zip-файл в список для удаления
                                } else {
                                    Log.e(
                                        "FileManager",
                                        "Failed to create ZIP archive for ${file.name}"
                                    )
                                    errorOccurred = true
                                    runOnUiThread {
                                        Toast.makeText(
                                            this,
                                            "Ошибка при создании архива для ${file.name}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                val uri = FileProvider.getUriForFile(
                                    this,
                                    "${packageName}.fileprovider",
                                    file
                                )
                                filesUris.add(uri)
                                filesToDelete.add(file) // Добавляем файл в список для удаления
                            }
                        } catch (e: Exception) {
                            Log.e("FileManager", "Error processing file ${file.name}: ${e.message}")
                            errorOccurred = true
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    "Ошибка при обработке файла ${file.name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    if (filesUris.isNotEmpty()) {
                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE)
                        shareIntent.type = "application/zip" // Или "image/*" для отдельных файлов
                        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesUris)
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                        runOnUiThread {
                            val chooserIntent =
                                Intent.createChooser(shareIntent, "Share selected files")
                            startActivity(chooserIntent)
                            clearSelection()

                            // Удаляем файлы с задержкой
                            Handler(Looper.getMainLooper()).postDelayed({
                                deleteTempFiles(filesToDelete)
                            }, 100000) // Задержка в 5 секунд (можно настроить)

                        }
                    } else {
                        runOnUiThread {
                            if (errorOccurred) {
                                Toast.makeText(
                                    this,
                                    "Не удалось подготовить ни один файл для отправки.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(this, "Нечего отправлять.", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e("FileManager", "FileProvider error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this, "Ошибка при обмене файлами", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        } else {
            Toast.makeText(this, "No files selected", Toast.LENGTH_SHORT).show()
        }
    }


    private fun deleteTempFiles(filesToDelete: List<File>) {
        for (file in filesToDelete) {
            try {
                if (file.delete()) {
                    Log.d("FileManager", "Удален временный файл: ${file.absolutePath}")
                } else {
                    Log.e("FileManager", "Не удалось удалить временный файл: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(
                    "FileManager",
                    "Ошибка при удалении временного файла: ${file.absolutePath}",
                    e
                )
            }
        }
    }

    fun zipFolder(folderToZip: File, zipFile: File) {
        try {
            val zipParameters = ZipParameters()
            zipParameters.compressionMethod = CompressionMethod.DEFLATE
            zipParameters.compressionLevel = CompressionLevel.NORMAL

            ZipFile(zipFile.absolutePath).addFolder(folderToZip, zipParameters)

        } catch (e: Exception) {
            Log.e(
                "FileManager",
                "Error zipping folder ${folderToZip.absolutePath} to ${zipFile.absolutePath}: ${e.message}",
                e
            )
            runOnUiThread {
                Toast.makeText(this, "Ошибка при архивации папки: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    fun updateProgress(progress: Int) {
        progressBar.progress = progress
        fileAdapter.setProgress(progress)

        // 1. Рассчитываем новое количество столбцов
        val noOfColumns = calculateNoOfColumns(progress)

        // 2. Обновляем GridLayoutManager
        (recyclerView.layoutManager as GridLayoutManager).spanCount = noOfColumns
    }

    private fun calculateNoOfColumns(progress: Int): Int {
        // 1. Вычисляем scaleFactor на основе прогресса
        val scaleFactor = 0.5f + (progress / 100f) * 0.5f // От 0.5 до 1.0

        // 2. Определяем логику расчета количества столбцов
        return when {
            scaleFactor <= 0.65 -> 5
            scaleFactor >= 0.85 -> 3
            else -> 4
        }
    }

    override fun onResume() {
        super.onResume()
        loadDirectoryContent(getCurrentDirectory())
    }
}

