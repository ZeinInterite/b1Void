package com.example.b1void.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit тесты для FileManagerSettingsManager
 * Проверяет корректность enum конвертации и значений по умолчанию
 */
class FileManagerSettingsManagerTest {

    @Test
    fun `test SortMode enum has all expected values`() {
        val modes = FileManagerSettingsManager.SortMode.values()
        assertEquals("SortMode should have 6 values", 6, modes.size)

        val expectedModes = setOf(
            FileManagerSettingsManager.SortMode.DATE_ASC,
            FileManagerSettingsManager.SortMode.DATE_DESC,
            FileManagerSettingsManager.SortMode.NAME_ASC,
            FileManagerSettingsManager.SortMode.NAME_DESC,
            FileManagerSettingsManager.SortMode.SIZE_ASC,
            FileManagerSettingsManager.SortMode.SIZE_DESC
        )
        assertEquals("SortMode should contain all expected values", expectedModes, modes.toSet())
    }

    @Test
    fun `test SortMode fromString with valid values`() {
        assertEquals(
            FileManagerSettingsManager.SortMode.DATE_ASC,
            FileManagerSettingsManager.SortMode.fromString("DATE_ASC")
        )
        assertEquals(
            FileManagerSettingsManager.SortMode.NAME_DESC,
            FileManagerSettingsManager.SortMode.fromString("NAME_DESC")
        )
        assertEquals(
            FileManagerSettingsManager.SortMode.SIZE_ASC,
            FileManagerSettingsManager.SortMode.fromString("SIZE_ASC")
        )
    }

    @Test
    fun `test SortMode fromString with invalid value returns default`() {
        val result = FileManagerSettingsManager.SortMode.fromString("INVALID_MODE")
        assertEquals("Invalid sort mode should return DATE_ASC as default",
            FileManagerSettingsManager.SortMode.DATE_ASC,
            result)
    }

    @Test
    fun `test DisplayMode enum has expected values`() {
        val modes = FileManagerSettingsManager.DisplayMode.values()
        assertEquals("DisplayMode should have 2 values", 2, modes.size)

        val expectedModes = setOf(
            FileManagerSettingsManager.DisplayMode.GRID,
            FileManagerSettingsManager.DisplayMode.LIST
        )
        assertEquals("DisplayMode should contain GRID and LIST", expectedModes, modes.toSet())
    }

    @Test
    fun `test DisplayMode fromString with valid values`() {
        assertEquals(
            FileManagerSettingsManager.DisplayMode.GRID,
            FileManagerSettingsManager.DisplayMode.fromString("grid")
        )
        assertEquals(
            FileManagerSettingsManager.DisplayMode.GRID,
            FileManagerSettingsManager.DisplayMode.fromString("GRID")
        )
        assertEquals(
            FileManagerSettingsManager.DisplayMode.LIST,
            FileManagerSettingsManager.DisplayMode.fromString("list")
        )
        assertEquals(
            FileManagerSettingsManager.DisplayMode.LIST,
            FileManagerSettingsManager.DisplayMode.fromString("LIST")
        )
    }

    @Test
    fun `test DisplayMode fromString is case-insensitive`() {
        assertEquals(
            FileManagerSettingsManager.DisplayMode.GRID,
            FileManagerSettingsManager.DisplayMode.fromString("GrId")
        )
        assertEquals(
            FileManagerSettingsManager.DisplayMode.LIST,
            FileManagerSettingsManager.DisplayMode.fromString("LiSt")
        )
    }

    @Test
    fun `test DisplayMode fromString with invalid value returns default`() {
        val result = FileManagerSettingsManager.DisplayMode.fromString("INVALID")
        assertEquals("Invalid display mode should return GRID as default",
            FileManagerSettingsManager.DisplayMode.GRID,
            result)
    }

    @Test
    fun `test default values constants`() {
        assertEquals("Default sort mode should be DATE_ASC",
            "DATE_ASC", FileManagerSettingsManager.DEFAULT_SORT_MODE)
        assertEquals("Default display mode should be grid",
            "grid", FileManagerSettingsManager.DEFAULT_DISPLAY_MODE)
        assertEquals("Default span count should be 4",
            4, FileManagerSettingsManager.DEFAULT_SPAN_COUNT)
    }

    @Test
    fun `test span count should be within valid range`() {
        // Проверяем, что дефолтное значение находится в допустимом диапазоне 2-6
        val defaultSpanCount = FileManagerSettingsManager.DEFAULT_SPAN_COUNT
        assertTrue("Default span count should be between 2 and 6, got $defaultSpanCount",
            defaultSpanCount in 2..6)
    }
}
