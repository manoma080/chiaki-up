// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.gameblabla.chiaki.settings

import android.app.AlertDialog
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SeekBarPreference
import com.gameblabla.chiaki.R
import com.gameblabla.chiaki.common.Preferences
import com.gameblabla.chiaki.stream.FilterPresetStore

class SettingsVideoFiltersFragment : PreferenceFragmentCompat(), TitleFragment {
    private val keys = listOf("filter_brightness", "filter_saturation", "filter_gamma", "filter_contrast", "filter_hue", "filter_sharpness")
    private lateinit var preferences: Preferences
    private lateinit var presets: FilterPresetStore

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferences = Preferences(requireContext())
        presets = FilterPresetStore(requireContext())
        preferenceManager.preferenceDataStore = DataStore(preferences)
        setPreferencesFromResource(R.xml.preferences_video_filters, rootKey)
        findPreference<Preference>("reset_filters")?.setOnPreferenceClickListener {
            applyValues(emptyMap()); true
        }
        findPreference<Preference>("save_filter_preset")?.setOnPreferenceClickListener {
            showSaveDialog(); true
        }
        refreshPresets()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.setOnApplyWindowInsetsListener(listView) { v, insets ->
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }
    }

    override fun getTitle(resources: Resources) = resources.getString(R.string.preferences_category_title_video_filter)

    private fun applyValues(values: Map<String, Int>) {
        keys.forEach { key ->
            val limit = if (key == "filter_sharpness") 5 else 100
            val value = (values[key] ?: 0).coerceIn(-limit, limit)
            preferences.putFilterInt(key, value)
            findPreference<SeekBarPreference>(key)?.value = value
        }
    }

    private fun refreshPresets() {
        val category = findPreference<PreferenceCategory>("filter_presets") ?: return
        category.removeAll()
        presets.names().forEach { name ->
            category.addPreference(LongPressPreference(requireContext()) {
                AlertDialog.Builder(requireContext())
                    .setMessage(getString(R.string.filter_preset_delete_confirm, name))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.filter_preset_delete) { _, _ -> presets.delete(name); refreshPresets() }
                    .show()
            }.apply {
                title = name
                isPersistent = false
                setOnPreferenceClickListener { presets.load(name)?.let { applyValues(it) }; true }
            })
        }
    }

    private fun showSaveDialog() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = getString(R.string.filter_preset_name)
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.filter_save_preset).setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.filter_save_preset, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString()
                if (name.isBlank()) {
                    input.error = getString(R.string.filter_preset_name_required)
                } else {
                    val values = keys.associateWith { preferences.getFilterInt(it, 0) }
                    fun save() { presets.save(name, values); refreshPresets(); dialog.dismiss() }
                    if (name in presets.names()) {
                        AlertDialog.Builder(requireContext())
                            .setMessage(getString(R.string.filter_preset_overwrite_confirm, name))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.filter_preset_overwrite) { _, _ -> save() }.show()
                    } else save()
                }
            }
        }
        dialog.show()
    }
}

private class LongPressPreference(context: Context, private val onLongPress: () -> Unit) : Preference(context) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.setOnLongClickListener { onLongPress(); true }
    }
}
