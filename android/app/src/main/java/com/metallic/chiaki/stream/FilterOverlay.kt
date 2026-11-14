// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.gameblabla.chiaki.stream

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.graphics.Color

import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.gameblabla.chiaki.R
import com.gameblabla.chiaki.common.Preferences

/** Inline controls shared by the real stream and the playback sample. */
class FilterOverlay(context: Context, private val onClosed: () -> Unit) : ScrollView(context) {
    private data class Spec(val key: String, val title: Int, val range: Int)
    private val specs = listOf(
        Spec("filter_brightness", R.string.preferences_filter_brightness_title, 100),
        Spec("filter_saturation", R.string.preferences_filter_saturation_title, 100),
        Spec("filter_gamma", R.string.preferences_filter_gamma_title, 100),
        Spec("filter_contrast", R.string.preferences_filter_contrast_title, 100),
        Spec("filter_hue", R.string.preferences_filter_hue_title, 100),
        Spec("filter_sharpness", R.string.preferences_filter_sharpness_title, 5)
    )
    private val preferences = Preferences(context)
    private val sliders = mutableListOf<SeekBar>()
    private val presetStore = FilterPresetStore(context)
    private lateinit var presetContainer: LinearLayout

    init {
        visibility = View.GONE
        isClickable = true
        isFillViewport = true
        setBackgroundColor(Color.rgb(35, 35, 35))
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (12 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        addView(column)
        specs.forEach { spec ->
            val label = TextView(context).apply { setTextColor(Color.WHITE) }
            val seek = SeekBar(context).apply {
                max = spec.range * 2
                progress = preferences.getFilterInt(spec.key, 0).coerceIn(-spec.range, spec.range) + spec.range
                contentDescription = context.getString(spec.title)
            }
            fun updateLabel(value: Int) { label.text = "${context.getString(spec.title)}: $value" }
            updateLabel(seek.progress - spec.range)
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    val value = progress - spec.range
                    updateLabel(value)
                    if (fromUser) preferences.putFilterInt(spec.key, value)
                }
                override fun onStartTrackingTouch(bar: SeekBar) {}
                override fun onStopTrackingTouch(bar: SeekBar) {}
            })
            column.addView(label)
            column.addView(seek)
            sliders.add(seek)
        }
        presetContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(presetContainer)
        refreshPresets()
        column.addView(Button(context).apply {
            setText(R.string.filter_save_preset)
            setOnClickListener { showSaveDialog() }
        })
        column.addView(Button(context).apply {
            setText(R.string.filter_reset_all)
            setOnClickListener {
                specs.forEachIndexed { index, spec ->
                    preferences.putFilterInt(spec.key, 0)
                    sliders[index].progress = spec.range
                }
            }
        })
        column.addView(Button(context).apply {
            setText(R.string.filter_close)
            setOnClickListener { close() }
        })
    }

    private fun refreshPresets() {
        presetContainer.removeAllViews()
        presetStore.names().forEach { name ->
            presetContainer.addView(Button(context).apply {
                text = name
                isAllCaps = false
                setOnClickListener {
                    val values = presetStore.load(name) ?: return@setOnClickListener
                    specs.forEachIndexed { index, spec ->
                        val value = (values[spec.key] ?: 0).coerceIn(-spec.range, spec.range)
                        preferences.putFilterInt(spec.key, value)
                        sliders[index].progress = value + spec.range
                    }
                }
                setOnLongClickListener {
                    AlertDialog.Builder(context)
                        .setMessage(context.getString(R.string.filter_preset_delete_confirm, name))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.filter_preset_delete) { _, _ ->
                            presetStore.delete(name)
                            refreshPresets()
                        }.show()
                    true
                }
            })
        }
    }

    private fun showSaveDialog() {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = context.getString(R.string.filter_preset_name)
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.filter_save_preset)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.filter_save_preset, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString()
                if (name.isBlank()) {
                    input.error = context.getString(R.string.filter_preset_name_required)
                } else {
                    val values = specs.associate { spec ->
                        spec.key to preferences.getFilterInt(spec.key, 0).coerceIn(-spec.range, spec.range)
                    }
                    if (name in presetStore.names()) {
                        AlertDialog.Builder(context)
                            .setMessage(context.getString(R.string.filter_preset_overwrite_confirm, name))
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.filter_preset_overwrite) { _, _ ->
                                presetStore.save(name, values)
                                refreshPresets()
                                dialog.dismiss()
                            }.show()
                    } else {
                        presetStore.save(name, values)
                        refreshPresets()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    fun close() { visibility = View.GONE; onClosed() }

    fun show() {
        refreshPresets()
        specs.forEachIndexed { index, spec ->
            sliders[index].progress = preferences.getFilterInt(spec.key, 0).coerceIn(-spec.range, spec.range) + spec.range
        }
        visibility = View.VISIBLE
        sliders.firstOrNull()?.requestFocus()
    }
}
