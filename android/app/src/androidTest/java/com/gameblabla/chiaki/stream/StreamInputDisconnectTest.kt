package com.gameblabla.chiaki.stream

import android.test.InstrumentationTestCase
import android.view.InputDevice
import android.view.KeyEvent
import com.gameblabla.chiaki.common.Preferences
import com.gameblabla.chiaki.lib.ControllerState
import com.gameblabla.chiaki.session.StreamInput

class StreamInputDisconnectTest : InstrumentationTestCase() {
    fun testDisconnectReleasesOnlyRemovedDevice() {
        val context = instrumentation.targetContext
        val input = StreamInput(context, Preferences(context))
        fun key(id: Int, code: Int, action: Int) = KeyEvent(0, 0, action, code, 0, 0, id, 0, 0, InputDevice.SOURCE_GAMEPAD)
        input.dispatchKeyEvent(key(1, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN))
        input.dispatchKeyEvent(key(2, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.ACTION_DOWN))
        val before = input.controllerState.buttons
        assertTrue(before and ControllerState.BUTTON_CROSS != 0U)
        assertTrue(before and ControllerState.BUTTON_MOON != 0U)
        input.releaseGamepadControls(1)
        val after = input.controllerState.buttons
        assertEquals(0, (after and ControllerState.BUTTON_CROSS).toInt())
        assertTrue(after and ControllerState.BUTTON_MOON != 0U)
    }
    fun testSharedButtonAndAxesSurviveOtherControllerDisconnect() {
        val context = instrumentation.targetContext
        val input = StreamInput(context, Preferences(context))
        fun key(id: Int, action: Int) = KeyEvent(0, 0, action, KeyEvent.KEYCODE_BUTTON_A, 0, 0, id, 0, 0, InputDevice.SOURCE_GAMEPAD)
        fun motion(id: Int, x: Float): android.view.MotionEvent {
            val props = android.view.MotionEvent.PointerProperties().apply { this.id = 0 }
            val coords = android.view.MotionEvent.PointerCoords().apply { setAxisValue(android.view.MotionEvent.AXIS_X, x) }
            return android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_MOVE, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, id, 0, InputDevice.SOURCE_JOYSTICK, 0)
        }
        input.dispatchKeyEvent(key(1, KeyEvent.ACTION_DOWN))
        input.dispatchKeyEvent(key(2, KeyEvent.ACTION_DOWN))
        motion(1, .8f).let { input.onGenericMotionEvent(it); it.recycle() }
        motion(2, -.4f).let { input.onGenericMotionEvent(it); it.recycle() }
        input.dispatchKeyEvent(key(1, KeyEvent.ACTION_UP))
        assertTrue(input.controllerState.buttons and ControllerState.BUTTON_CROSS != 0U)
        input.releaseGamepadControls(1)
        assertTrue(input.controllerState.buttons and ControllerState.BUTTON_CROSS != 0U)
        assertTrue(input.controllerState.leftX < 0)
        input.releaseGamepadControls(2)
        assertEquals(0, input.controllerState.buttons.toInt())
        assertEquals(0, input.controllerState.leftX.toInt())
    }
}
