package com.gameblabla.chiaki.stream

import android.test.InstrumentationTestCase
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import androidx.preference.PreferenceManager
import com.gameblabla.chiaki.R
import com.gameblabla.chiaki.common.Preferences

@Suppress("DEPRECATION")
class FilterOverlayTest : InstrumentationTestCase() {
    fun testClosingPanelRestoresGameFocusAndFilterCanBeFocusedAgain() {
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = android.widget.FrameLayout(context)
            val button = Button(context)
            root.addView(button)
            assertTrue(button.requestFocus())
            var closed = false
            val panel = FilterOverlay(context) {
                returnFocusToGameplay(root)
                closed = true
            }
            root.addView(panel)
            panel.show()
            panel.close()
            assertTrue(closed)
            assertTrue(root.hasFocus())
            assertFalse(button.hasFocus())
            val input = com.gameblabla.chiaki.session.StreamInput(context, Preferences(context))
            val a = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_A)
            assertTrue(input.dispatchKeyEvent(a))
            assertTrue(input.controllerState.buttons != 0U)
            assertTrue(button.requestFocus())
            assertTrue(button.hasFocus())
        }
    }

    fun testBlockingClearsControllerAndSuppressesRemoteInput() {
        val context = instrumentation.targetContext
        val input = com.gameblabla.chiaki.session.StreamInput(context, Preferences(context))
        val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BUTTON_A)
        assertTrue(input.dispatchKeyEvent(down))
        assertTrue(input.controllerState.buttons != 0U)
        input.touchControllerState = com.gameblabla.chiaki.lib.ControllerState(buttons = 4U)
        input.setFilterInputBlocked(true)
        assertEquals(0, input.controllerState.buttons.toInt())
        assertFalse(input.dispatchKeyEvent(down))
        input.touchControllerState = com.gameblabla.chiaki.lib.ControllerState(buttons = 4U)
        assertEquals(0, input.controllerState.buttons.toInt())
        input.setFilterInputBlocked(false)
        assertTrue(input.dispatchKeyEvent(down))
        assertTrue(input.controllerState.buttons != 0U)
    }

    fun testSensorCallbacksAreSuppressedWhileBlockedAndResumeAfterClose() {
        val context = instrumentation.targetContext
        val manager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = manager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        assertNotNull("Accelerometer required for sensor routing test", sensor)
        val input = com.gameblabla.chiaki.session.StreamInput(context, Preferences(context))
        val field = input.javaClass.getDeclaredField("sensorEventListener").apply { isAccessible = true }
        val listener = field.get(input) as android.hardware.SensorEventListener
        val constructor = android.hardware.SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        constructor.isAccessible = true
        val event = constructor.newInstance(3)
        event.sensor = sensor
        event.values[1] = android.hardware.SensorManager.GRAVITY_EARTH
        var callbacks = 0
        input.controllerStateChangedCallback = { callbacks++ }
        listener.onSensorChanged(event)
        assertEquals(1, callbacks)
        input.setFilterInputBlocked(true)
        val afterBlocking = callbacks
        event.values[1] = 2 * android.hardware.SensorManager.GRAVITY_EARTH
        listener.onSensorChanged(event)
        assertEquals(afterBlocking, callbacks)
        assertEquals(0f, kotlin.math.abs(input.controllerState.accelX))
        input.setFilterInputBlocked(false)
        listener.onSensorChanged(event)
        assertEquals(afterBlocking + 1, callbacks)
        assertEquals(2f, kotlin.math.abs(input.controllerState.accelX))
    }

    fun testBlockingPublishesNeutralMotionAndDoesNotRestoreStaleValues() {
        val context = instrumentation.targetContext
        val input = com.gameblabla.chiaki.session.StreamInput(context, Preferences(context))
        val sensor = input.javaClass.getDeclaredField("sensorControllerState").apply { isAccessible = true }
            .get(input) as com.gameblabla.chiaki.lib.ControllerState
        sensor.accelX = 2f; sensor.accelY = 3f; sensor.accelZ = 4f
        sensor.gyroX = 5f; sensor.gyroY = 6f; sensor.gyroZ = 7f
        sensor.orientX = 0.1f; sensor.orientY = 0.2f; sensor.orientZ = 0.3f; sensor.orientW = 0.4f
        var published: com.gameblabla.chiaki.lib.ControllerState? = null
        input.controllerStateChangedCallback = { published = it }
        input.setFilterInputBlocked(true)
        fun assertNeutral(state: com.gameblabla.chiaki.lib.ControllerState) {
            assertEquals(0f, kotlin.math.abs(state.accelX)); assertEquals(1f, state.accelY); assertEquals(0f, kotlin.math.abs(state.accelZ))
            assertEquals(0f, kotlin.math.abs(state.gyroX)); assertEquals(0f, state.gyroY); assertEquals(0f, kotlin.math.abs(state.gyroZ))
            assertEquals(0f, kotlin.math.abs(state.orientX)); assertEquals(0f, state.orientY); assertEquals(0f, kotlin.math.abs(state.orientZ)); assertEquals(1f, state.orientW)
        }
        assertNotNull(published)
        assertNeutral(published!!)
        input.setFilterInputBlocked(false)
        assertNeutral(input.controllerState)
    }

    fun testTouchTrackerCancelReleasesPointer() {
        val tracker = com.gameblabla.chiaki.touchcontrols.TouchTracker()
        val down = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 3f, 4f, 0)
        val cancel = android.view.MotionEvent.obtain(0, 1, android.view.MotionEvent.ACTION_CANCEL, 3f, 4f, 0)
        try {
            tracker.touchEvent(down)
            assertNotNull(tracker.currentPosition)
            tracker.touchEvent(cancel)
            assertNull(tracker.currentPosition)
        } finally { down.recycle(); cancel.recycle() }
    }

    fun testReopenAndResetAllFilters() {
        val context = instrumentation.targetContext
        val prefs = Preferences(context)
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val keys = listOf("filter_brightness", "filter_saturation", "filter_gamma", "filter_contrast", "filter_hue", "filter_sharpness")
        val old = keys.associateWith { shared.all[it] }
        try {
            instrumentation.runOnMainSync {
                keys.forEachIndexed { i, key -> prefs.putFilterInt(key, if (i == 5) 3 else 30) }
                val panel = FilterOverlay(context) {}
                panel.show()
                fun descendants(view: View): List<View> = listOf(view) +
                    if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                val views = descendants(panel)
                val sliders = views.filterIsInstance<SeekBar>()
                assertEquals(6, sliders.size)
                assertEquals(listOf(130, 130, 130, 130, 130, 8), sliders.map { it.progress })
                views.filterIsInstance<Button>().first { it.text == context.getString(R.string.filter_reset_all) }.performClick()
                keys.forEach { assertEquals(0, Preferences(context).getFilterInt(it, -1)) }
                assertEquals(listOf(100, 100, 100, 100, 100, 5), sliders.map { it.progress })
                panel.visibility = View.GONE
                prefs.putFilterInt(keys[0], -25)
                panel.show()
                assertEquals(75, sliders[0].progress)
            }
        } finally {
            val edit = shared.edit()
            old.forEach { (key, value) -> when (value) {
                null -> edit.remove(key)
                is Int -> edit.putInt(key, value)
                is String -> edit.putString(key, value)
            } }
            edit.commit()
        }
    }
}
