package com.example.b1void.camera.ev

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureInteractorTest {
    @Test
    fun defaultRangeWithoutCamera_isMinus2to2() {
        val interactor = ExposureInteractor()
        assertEquals(-2.0f..2.0f, interactor.evRange())
    }

    @Test
    fun defaultCurrentEvWithoutCamera_isZero() {
        val interactor = ExposureInteractor()
        assertEquals(0f, interactor.currentEv())
    }
}

