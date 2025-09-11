package com.example.b1void.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.* // Используем Assert из JUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FolderRepositoryTest {

    // Правило для создания временной папки для каждого теста
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: FolderRepository
    private lateinit var rootDir: File

    @Before
    fun setUp() {
        repository = FolderRepository()
        // Создаем структуру папок и файлов для тестов
        rootDir = tempFolder.newFolder("testRoot")
        File(rootDir, "subfolder1").mkdir()
        File(rootDir, "subfolder2").mkdir()
        File(File(rootDir, "subfolder1"), "subfolder1_1").mkdir()
        File(rootDir, "file1.txt").createNewFile() // Файлы должны игнорироваться
    }

    @Test
    fun `getFolderTree should return only directories and build correct structure`() = runBlocking {
        // Act: Выполняем тестируемый метод
        val tree = repository.getFolderTree(rootDir)

        // Assert: Проверяем результаты
        assertEquals(2, tree.size) // Должны быть 2 папки на верхнем уровне
        
        val subfolder1 = tree.find { it.file.name == "subfolder1" }
        assertNotNull(subfolder1)
        assertEquals(1, subfolder1!!.children.size) // В subfolder1 есть одна дочерняя папка
        assertEquals("subfolder1_1", subfolder1.children[0].file.name)

        val subfolder2 = tree.find { it.file.name == "subfolder2" }
        assertNotNull(subfolder2)
        assertTrue(subfolder2!!.children.isEmpty()) // В subfolder2 нет дочерних папок
    }

    @Test
    fun `moveFiles should move file to destination and return true`() = runBlocking {
        // Arrange: Подготовка
        val sourceDir = tempFolder.newFolder("source")
        val destDir = tempFolder.newFolder("destination")
        val fileToMove = File(sourceDir, "movable.txt")
        fileToMove.createNewFile()

        assertTrue(File(sourceDir, "movable.txt").exists()) // Убедимся, что файл на месте
        assertFalse(File(destDir, "movable.txt").exists()) // Убедимся, что в папке назначения его нет

        // Act: Выполняем перемещение
        val result = repository.moveFiles(listOf(fileToMove), destDir)

        // Assert: Проверяем результат
        assertTrue(result)
        assertFalse(File(sourceDir, "movable.txt").exists()) // Файл должен исчезнуть из источника
        assertTrue(File(destDir, "movable.txt").exists()) // Файл должен появиться в назначении
    }

    @Test
    fun `moveFiles should return false if file does not exist`() = runBlocking {
        // Arrange
        val nonExistentFile = File(rootDir, "nonexistent.txt")
        val destDir = tempFolder.newFolder("another_destination")

        // Act
        val result = repository.moveFiles(listOf(nonExistentFile), destDir)

        // Assert
        assertFalse(result) // Ожидаем false, так как перемещение не удалось
    }
}
