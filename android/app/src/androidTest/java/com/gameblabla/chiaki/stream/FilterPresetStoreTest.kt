package com.gameblabla.chiaki.stream

import android.test.InstrumentationTestCase

@Suppress("DEPRECATION")
class FilterPresetStoreTest : InstrumentationTestCase() {
    fun testCaseSensitivePersistenceOverwriteAndDelete() {
        val context = instrumentation.targetContext
        val upper = "preset-test-Upper"
        val lower = "preset-test-upper"
        val first = mapOf("filter_brightness" to 42, "filter_sharpness" to -3)
        val second = mapOf("filter_brightness" to -55, "filter_sharpness" to 5)
        val store = FilterPresetStore(context)
        try {
            store.save(upper, first)
            store.save(lower, second)
            val reopened = FilterPresetStore(context)
            assertEquals(first, reopened.load(upper))
            assertEquals(second, reopened.load(lower))
            assertTrue(reopened.names().containsAll(listOf(upper, lower)))
            reopened.save(upper, second)
            assertEquals(second, FilterPresetStore(context).load(upper))
            reopened.delete(upper)
            assertNull(FilterPresetStore(context).load(upper))
            assertEquals(second, FilterPresetStore(context).load(lower))
        } finally {
            store.delete(upper)
            store.delete(lower)
        }
    }
}
