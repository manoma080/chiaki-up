// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.gameblabla.chiaki.stream

import android.content.Context
import org.json.JSONObject

/** Presets are kept separately from the active filter settings. Names are case-sensitive. */
class FilterPresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("filter_presets", Context.MODE_PRIVATE)

    fun names(): List<String> = prefs.all.keys.sorted()

    fun load(name: String): Map<String, Int>? {
        val encoded = prefs.getString(name, null) ?: return null
        return try {
            val json = JSONObject(encoded)
            val keys = json.keys()
            val values = mutableMapOf<String, Int>()
            while (keys.hasNext()) {
                val key = keys.next()
                values[key] = json.getInt(key)
            }
            values
        } catch (_: Exception) { null }
    }

    fun save(name: String, values: Map<String, Int>) {
        prefs.edit().putString(name, JSONObject(values).toString()).apply()
    }

    fun delete(name: String) { prefs.edit().remove(name).apply() }
}
