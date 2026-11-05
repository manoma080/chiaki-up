// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.gameblabla.chiaki.stream

import android.content.Context
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

    fun close() { visibility = View.GONE; onClosed() }

    fun show() {
        specs.forEachIndexed { index, spec ->
            sliders[index].progress = preferences.getFilterInt(spec.key, 0).coerceIn(-spec.range, spec.range) + spec.range
        }
        visibility = View.VISIBLE
        sliders.firstOrNull()?.requestFocus()
    }
}
