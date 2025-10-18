package com.example.b1void.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.fileManagerDataStore: DataStore<Preferences> by preferencesDataStore(name = "file_manager_settings")

/**
 * FileManagerSettingsManager управляет настройками файлового менеджера через DataStore.
 * Использует manual DI (без Hilt) согласно архитектуре проекта.
 */
class FileManagerSettingsManager(private val context: Context) {

    companion object {
        // Sort settings keys
        val SORT_MODE_KEY = stringPreferencesKey("sort_mode")
        val SORT_ASCENDING_KEY = booleanPreferencesKey("sort_ascending")

        // Display settings keys
        val DISPLAY_MODE_KEY = stringPreferencesKey("display_mode")
        val SPAN_COUNT_KEY = intPreferencesKey("span_count")

        // Navigation keys
        val LAST_DIRECTORY_KEY = stringPreferencesKey("last_directory")

        // Default values
        const val DEFAULT_SORT_MODE = "DATE_ASC"
        const val DEFAULT_DISPLAY_MODE = "grid"
        const val DEFAULT_SPAN_COUNT = 4
    }

    /**
     * Enum для режимов сортировки
     */
    enum class SortMode {
        DATE_ASC,
        DATE_DESC,
        NAME_ASC,
        NAME_DESC,
        SIZE_ASC,
        SIZE_DESC;

        companion object {
            fun fromString(value: String): SortMode {
                return values().find { it.name == value } ?: DATE_ASC
            }
        }
    }

    /**
     * Enum для режима отображения
     */
    enum class DisplayMode {
        GRID,
        LIST;

        companion object {
            fun fromString(value: String): DisplayMode {
                return values().find { it.name.lowercase() == value.lowercase() } ?: GRID
            }
        }
    }

    // ==================== Sort Settings ====================

    /**
     * Получить режим сортировки
     * @return SortMode (default: DATE_ASC)
     */
    fun getSortMode(): Flow<SortMode> {
        return context.fileManagerDataStore.data.map {
            val modeString = it[SORT_MODE_KEY] ?: DEFAULT_SORT_MODE
            SortMode.fromString(modeString)
        }
    }

    /**
     * Сохранить режим сортировки
     * @param mode Режим сортировки
     */
    suspend fun setSortMode(mode: SortMode) {
        try {
            context.fileManagerDataStore.edit {
                it[SORT_MODE_KEY] = mode.name
            }
            android.util.Log.d("FileManagerSettings", "Sort mode saved: ${mode.name}")
        } catch (e: Exception) {
            android.util.Log.e("FileManagerSettings", "Failed to save sort mode", e)
        }
    }

    /**
     * Получить направление сортировки (возрастание/убывание)
     * @return true для возрастания (default: true)
     */
    fun getSortAscending(): Flow<Boolean> {
        return context.fileManagerDataStore.data.map {
            it[SORT_ASCENDING_KEY] ?: true
        }
    }

    /**
     * Сохранить направление сортировки
     * @param ascending true для возрастания, false для убывания
     */
    suspend fun setSortAscending(ascending: Boolean) {
        try {
            context.fileManagerDataStore.edit {
                it[SORT_ASCENDING_KEY] = ascending
            }
            android.util.Log.d("FileManagerSettings", "Sort ascending saved: $ascending")
        } catch (e: Exception) {
            android.util.Log.e("FileManagerSettings", "Failed to save sort ascending", e)
        }
    }

    // ==================== Display Settings ====================

    /**
     * Получить режим отображения (сетка/список)
     * @return DisplayMode (default: GRID)
     */
    fun getDisplayMode(): Flow<DisplayMode> {
        return context.fileManagerDataStore.data.map {
            val modeString = it[DISPLAY_MODE_KEY] ?: DEFAULT_DISPLAY_MODE
            DisplayMode.fromString(modeString)
        }
    }

    /**
     * Сохранить режим отображения
     * @param mode Режим отображения (GRID или LIST)
     */
    suspend fun setDisplayMode(mode: DisplayMode) {
        try {
            context.fileManagerDataStore.edit {
                it[DISPLAY_MODE_KEY] = mode.name.lowercase()
            }
            android.util.Log.d("FileManagerSettings", "Display mode saved: ${mode.name}")
        } catch (e: Exception) {
            android.util.Log.e("FileManagerSettings", "Failed to save display mode", e)
        }
    }

    /**
     * Получить количество колонок в сетке
     * @return Количество колонок (default: 4)
     */
    fun getSpanCount(): Flow<Int> {
        return context.fileManagerDataStore.data.map {
            it[SPAN_COUNT_KEY] ?: DEFAULT_SPAN_COUNT
        }
    }

    /**
     * Сохранить количество колонок в сетке
     * @param count Количество колонок (2-6)
     */
    suspend fun setSpanCount(count: Int) {
        try {
            val validCount = count.coerceIn(2, 6)
            context.fileManagerDataStore.edit {
                it[SPAN_COUNT_KEY] = validCount
            }
            android.util.Log.d("FileManagerSettings", "Span count saved: $validCount")
        } catch (e: Exception) {
            android.util.Log.e("FileManagerSettings", "Failed to save span count", e)
        }
    }

    // ==================== Navigation Settings ====================

    /**
     * Получить последнюю открытую директорию
     * @return Путь к директории или null
     */
    fun getLastDirectory(): Flow<String?> {
        return context.fileManagerDataStore.data.map {
            it[LAST_DIRECTORY_KEY]
        }
    }

    /**
     * Сохранить последнюю открытую директорию
     * @param path Путь к директории
     */
    suspend fun setLastDirectory(path: String) {
        try {
            context.fileManagerDataStore.edit {
                it[LAST_DIRECTORY_KEY] = path
            }
            android.util.Log.d("FileManagerSettings", "Last directory saved: $path")
        } catch (e: Exception) {
            android.util.Log.e("FileManagerSettings", "Failed to save last directory", e)
        }
    }
}
