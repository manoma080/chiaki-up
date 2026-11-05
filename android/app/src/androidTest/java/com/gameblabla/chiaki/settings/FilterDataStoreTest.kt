package com.gameblabla.chiaki.settings

import android.test.InstrumentationTestCase
import androidx.preference.PreferenceManager
import com.gameblabla.chiaki.common.Preferences

@Suppress("DEPRECATION")
class FilterDataStoreTest : InstrumentationTestCase() {
    fun testFilterIntegersPersistInRendererPreferences() {
        val context = instrumentation.targetContext
        val shared = PreferenceManager.getDefaultSharedPreferences(context)
        val keys = listOf("filter_brightness", "filter_saturation", "filter_gamma", "filter_contrast", "filter_hue", "filter_sharpness")
        val oldValues = keys.associateWith { if (shared.contains(it)) shared.all[it] else null }
        try {
            val store = DataStore(Preferences(context))
            keys.forEachIndexed { index, key ->
                shared.edit().remove(key).commit()
                assertEquals(17, store.getInt(key, 17))
                val value = if (index == 5) -3 else (index + 1) * -10
                store.putInt(key, value)
                assertEquals(value, shared.getInt(key, 0))
                assertEquals(value, DataStore(Preferences(context)).getInt(key, 0))
            }
        } finally {
            val editor = shared.edit()
            oldValues.forEach { (key, value) ->
                when (value) {
                    null -> editor.remove(key)
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.commit()
        }
    }
}
