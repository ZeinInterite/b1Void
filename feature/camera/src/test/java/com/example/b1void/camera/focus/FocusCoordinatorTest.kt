package com.example.b1void.camera.focus

import org.junit.Assert.*
import org.junit.Test

private class FakeEngine : FocusCoordinator.FocusEngine {
    var lastStart: Triple<Float, Float, Int>? = null
    var lastCenterAutoCancel: Int? = null
    var aeLockState = false
    var cancelled = false
    var supported = true
    var nextResultSuccess = true

    override fun isSupportedAt(x: Float, y: Float, includeAeAwb: Boolean): Boolean = supported

    override fun startAt(
        x: Float,
        y: Float,
        includeAeAwb: Boolean,
        autoCancelSeconds: Int,
        onResult: (Boolean) -> Unit
    ) {
        lastStart = Triple(x, y, autoCancelSeconds)
        onResult(nextResultSuccess)
    }

    override fun startCenter(
        includeAeAwb: Boolean,
        autoCancelSeconds: Int,
        onResult: (Boolean) -> Unit
    ) {
        lastCenterAutoCancel = autoCancelSeconds
        onResult(nextResultSuccess)
    }

    override fun cancel() { cancelled = true }

    override fun setAeLock(locked: Boolean) { aeLockState = locked }
}

private class FakeCallbacks : FocusCoordinator.Callbacks {
    var showed = false
    var hid = false
    var lastResult: Boolean? = null
    var locked: Boolean = false
    override fun showIndicator(x: Float, y: Float) { showed = true }
    override fun hideIndicator() { hid = true }
    override fun onFocusResult(success: Boolean) { lastResult = success }
    override fun onLockChanged(locked: Boolean) { this.locked = locked }
}

class FocusCoordinatorTest {
    @Test
    fun singleTap_startsFocus_withAutoCancel() {
        val engine = FakeEngine()
        val cb = FakeCallbacks()
        val coordinator = FocusCoordinator(engine, cb)

        coordinator.onSingleTap(100f, 200f)

        assertEquals(Triple(100f, 200f, 5), engine.lastStart)
        assertTrue(cb.showed)
        assertEquals(true, cb.lastResult)
        assertFalse(cb.locked)
    }

    @Test
    fun longPress_locksFocus_noAutoCancel_andAeLocked() {
        val engine = FakeEngine()
        val cb = FakeCallbacks()
        val coordinator = FocusCoordinator(engine, cb)

        coordinator.onLongPress(10f, 20f)

        assertEquals(Triple(10f, 20f, 0), engine.lastStart)
        assertTrue(engine.aeLockState)
        assertTrue(cb.locked)
        assertTrue(coordinator.isLocked())
    }

    @Test
    fun resetToCenter_cancelsAndUnlocks() {
        val engine = FakeEngine()
        val cb = FakeCallbacks()
        val coordinator = FocusCoordinator(engine, cb)

        coordinator.onLongPress(1f, 1f)
        assertTrue(coordinator.isLocked())

        coordinator.resetToCenter()

        assertFalse(coordinator.isLocked())
        assertFalse(engine.aeLockState)
        assertNotNull(engine.lastCenterAutoCancel)
    }

    @Test
    fun tap_ignored_whenLocked() {
        val engine = FakeEngine()
        val cb = FakeCallbacks()
        val coordinator = FocusCoordinator(engine, cb)
        coordinator.onLongPress(5f, 5f)
        engine.lastStart = null

        coordinator.onSingleTap(7f, 7f)
        assertNull(engine.lastStart)
    }
}

