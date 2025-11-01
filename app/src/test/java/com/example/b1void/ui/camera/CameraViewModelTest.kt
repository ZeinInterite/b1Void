package com.example.b1void.ui.camera

import com.example.b1void.camera.ev.ExposureInteractor
import com.example.b1void.data.CameraSettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeExposureInteractor : ExposureInteractor() {
    private var range: ClosedFloatingPointRange<Float> = -1.0f..1.0f
    private var current: Float = 0f
    override fun evRange(): ClosedFloatingPointRange<Float> = range
    override fun currentEv(): Float = current
    override fun setEv(ev: Float) { current = ev }
}

private class FakeCameraSettingsManager : CameraSettingsManager(null as android.content.Context?) {
    private val flow = MutableStateFlow(0f)
    override suspend fun setEvCompensation(ev: Float) { flow.value = ev }
    override fun getEvCompensation() = flow
}

class CameraViewModelTest {
    @Test
    fun setExposureCompensation_clampsToRangeAndPersists() = runTest {
        val interactor = object : ExposureInteractor() {
            override fun evRange(): ClosedFloatingPointRange<Float> = -1.0f..1.0f
            var applied: Float = 0f
            override fun setEv(ev: Float) { applied = ev }
        }
        val settings = FakeCameraSettingsManager()
        val vm = CameraViewModel(interactor, settings)
        vm.setExposureCompensation(5f)
        assertEquals(1.0f, vm.evCompensation.value, 0.0001f)
    }
}

