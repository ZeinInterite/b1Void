package com.example.b1void.camera.ui.camera

import com.example.b1void.camera.core.camera.FocusState
import com.example.b1void.camera.data.camera.FocusRepository
import com.example.b1void.camera.domain.camera.FocusInteractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for CameraViewModel focusing on exposure compensation functionality
 */
class CameraViewModelTest {

    private lateinit var viewModel: CameraViewModel
    private lateinit var fakeRepo: FakeFocusRepository
    private lateinit var interactor: FocusInteractor

    @Before
    fun setup() {
        fakeRepo = FakeFocusRepository()
        interactor = FocusInteractor(fakeRepo)
        viewModel = CameraViewModel(interactor)
    }

    @Test
    fun setExposureCompensation_updatesState() = runBlocking {
        // When: Set EV to positive value
        viewModel.setExposureCompensation(1.5f)
        kotlinx.coroutines.delay(100)

        // Then: EV value is updated
        assertEquals(1.5f, viewModel.evValue.value, 0.01f)
    }

    @Test
    fun setExposureCompensation_negativeValue_updatesState() = runBlocking {
        // When: Set EV to negative value
        viewModel.setExposureCompensation(-1.0f)
        kotlinx.coroutines.delay(100)

        // Then: EV value is updated
        assertEquals(-1.0f, viewModel.evValue.value, 0.01f)
    }

    @Test
    fun resetExposureCompensation_setsToZero() = runBlocking {
        // Given: EV is set to non-zero
        viewModel.setExposureCompensation(2.0f)
        kotlinx.coroutines.delay(100)

        // When: Reset is called
        viewModel.resetExposureCompensation()
        kotlinx.coroutines.delay(100)

        // Then: EV is reset to 0
        assertEquals(0.0f, viewModel.evValue.value, 0.01f)
    }

    @Test
    fun initializeEvRange_updatesRange() = runBlocking {
        // When: Initialize EV range
        viewModel.initializeEvRange()
        kotlinx.coroutines.delay(100)

        // Then: Range is set correctly
        val range = viewModel.evRange.value
        assertEquals(-2.0f, range.start, 0.01f)
        assertEquals(2.0f, range.endInclusive, 0.01f)
    }

    @Test
    fun evValue_initiallyZero() {
        // Then: Initial EV value is 0
        assertEquals(0.0f, viewModel.evValue.value, 0.01f)
    }

    /**
     * Fake implementation of FocusRepository for testing
     */
    private class FakeFocusRepository : FocusRepository {
        private val _state = MutableStateFlow<FocusState>(FocusState.Idle())
        override val state: Flow<FocusState> = _state.asStateFlow()

        private val _ev = MutableStateFlow(0f)
        override val evValue: Flow<Float> = _ev.asStateFlow()

        private val _step = MutableStateFlow(0.3333f)
        override val exposureStep: Flow<Float> = _step.asStateFlow()

        override suspend fun tapToFocus(x: Float, y: Float) {
            _state.value = FocusState.Metering(x, y)
        }

        override suspend fun longPressLock(x: Float, y: Float) {
            _state.value = FocusState.Locked(x, y)
        }

        override suspend fun unlock() {
            _state.value = FocusState.Idle()
        }

        override suspend fun startTracking(
            rect: android.graphics.RectF?,
            x: Float?,
            y: Float?
        ) {
            _state.value = FocusState.Tracking(rect, x, y)
        }

        override suspend fun stopTracking() {
            _state.value = FocusState.Idle()
        }

        override suspend fun setEv(delta: Float) {
            _ev.value += delta
        }

        override suspend fun setAbsoluteEv(evValue: Float) {
            _ev.value = evValue
        }

        override suspend fun getEvRange(): ClosedFloatingPointRange<Float> {
            return -2.0f..2.0f
        }

        override suspend fun enableMacro(enabled: Boolean) {
            // No-op for tests
        }

        override suspend fun enableTorchAssist(enabled: Boolean) {
            // No-op for tests
        }
    }
}
