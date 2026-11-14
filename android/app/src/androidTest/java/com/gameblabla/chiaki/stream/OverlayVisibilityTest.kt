package com.gameblabla.chiaki.stream

import android.test.InstrumentationTestCase

class OverlayVisibilityTest : InstrumentationTestCase() {
    fun testSystemBarsAppearingScheduleHideWithoutShowingOverlay() {
        var shown = 0
        var hidden = 0
        var scheduled = 0
        val policy = OverlayVisibilityPolicy({ shown++ }, { hidden++ }, { scheduled++ })
        policy.systemUiChanged(false)
        assertEquals(0, shown)
        assertEquals(1, scheduled)
        policy.systemUiChanged(true)
        assertEquals(1, hidden)
        assertEquals(0, shown)
    }
    fun testHoverShowsOverlay() {
        var shown = 0
        val policy = OverlayVisibilityPolicy({ shown++ }, {}, {})
        policy.hover()
        assertEquals(1, shown)
    }
}
