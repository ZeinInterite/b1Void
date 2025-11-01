package com.example.b1void.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for CameraSettingsManager torch state persistence
 * Проверяет сохранение и восстановление состояния фонарика
 */
@RunWith(AndroidJUnit4::class)
class CameraSettingsManagerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var settingsManager: CameraSettingsManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = CameraSettingsManager(context)

        // Clear DataStore before each test
        runBlocking {
            context.dataStore.edit { it.clear() }
        }
    }

    @After
    fun tearDown() {
        // Clean up DataStore after each test
        runBlocking {
            context.dataStore.edit { it.clear() }
        }
    }

    @Test
    fun testTorchStateDefaultValue() = runBlocking {
        // When: Reading torch state without setting it
        val torchEnabled = settingsManager.getTorchEnabled().first()

        // Then: Should return default value (false)
        assertFalse("Torch should be OFF by default", torchEnabled)
    }

    @Test
    fun testSaveTorchStateEnabled() = runBlocking {
        // When: Saving torch state as enabled
        settingsManager.setTorchEnabled(true)

        // Then: Should be able to read it back
        val torchEnabled = settingsManager.getTorchEnabled().first()
        assertTrue("Torch state should be saved as ON", torchEnabled)
    }

    @Test
    fun testSaveTorchStateDisabled() = runBlocking {
        // Given: Torch is enabled
        settingsManager.setTorchEnabled(true)

        // When: Disabling torch
        settingsManager.setTorchEnabled(false)

        // Then: Should be able to read it back as disabled
        val torchEnabled = settingsManager.getTorchEnabled().first()
        assertFalse("Torch state should be saved as OFF", torchEnabled)
    }

    @Test
    fun testTorchStatePersistenceAcrossInstances() = runBlocking {
        // Given: Torch is enabled in first instance
        settingsManager.setTorchEnabled(true)

        // When: Creating a new instance of settings manager
        val newSettingsManager = CameraSettingsManager(context)
        val torchEnabled = newSettingsManager.getTorchEnabled().first()

        // Then: State should persist across instances
        assertTrue("Torch state should persist across instances", torchEnabled)
    }

    @Test
    fun testTorchStateToggle() = runBlocking {
        // Given: Initial state is OFF
        val initialState = settingsManager.getTorchEnabled().first()
        assertFalse("Initial torch state should be OFF", initialState)

        // When: Toggling ON
        settingsManager.setTorchEnabled(true)
        val stateAfterOn = settingsManager.getTorchEnabled().first()
        assertTrue("Torch should be ON after toggle", stateAfterOn)

        // When: Toggling OFF
        settingsManager.setTorchEnabled(false)
        val stateAfterOff = settingsManager.getTorchEnabled().first()
        assertFalse("Torch should be OFF after second toggle", stateAfterOff)
    }

    @Test
    fun testTorchStateIndependentOfOtherSettings() = runBlocking {
        // Given: Setting torch and flash mode
        settingsManager.setTorchEnabled(true)
        settingsManager.setFlashMode(1) // Flash ON

        // When: Reading torch state
        val torchEnabled = settingsManager.getTorchEnabled().first()
        val flashMode = settingsManager.getFlashMode().first()

        // Then: Both should be independently stored
        assertTrue("Torch state should be ON", torchEnabled)
        assertEquals("Flash mode should be ON", 1, flashMode)

        // When: Changing flash mode
        settingsManager.setFlashMode(0) // Flash OFF

        // Then: Torch state should remain unchanged
        val torchEnabledAfter = settingsManager.getTorchEnabled().first()
        assertTrue("Torch state should remain ON after changing flash mode", torchEnabledAfter)
    }

    @Test
    fun testMultipleRapidToggles() = runBlocking {
        // When: Rapidly toggling torch state multiple times
        settingsManager.setTorchEnabled(true)
        settingsManager.setTorchEnabled(false)
        settingsManager.setTorchEnabled(true)
        settingsManager.setTorchEnabled(false)
        settingsManager.setTorchEnabled(true)

        // Then: Final state should be ON
        val finalState = settingsManager.getTorchEnabled().first()
        assertTrue("Final torch state should be ON", finalState)
    }

    @Test
    fun testTorchStateKeyNamingConvention() {
        // Then: Key should follow snake_case convention
        assertEquals(
            "Torch enabled key should follow snake_case",
            "torch_enabled",
            CameraSettingsManager.TORCH_ENABLED_KEY.name
        )
    }
}
