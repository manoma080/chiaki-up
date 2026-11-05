// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.gameblabla.chiaki.stream

import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import com.gameblabla.chiaki.R

/** Standalone playback sample; see android/FILTER_SAMPLE.md for the debug launcher. */
class FilterSampleActivity : Activity() {
    private lateinit var video: FilterVideoView
    private var player: MediaPlayer? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        video = FilterVideoView(this)
        val root = FrameLayout(this)
        root.addView(video, FrameLayout.LayoutParams(-1, -1))
        val panel = FilterOverlay(this) {}
        val button = Button(this).apply {
            setText(R.string.filter_open)
            setOnClickListener { panel.show() }
        }
        root.addView(button, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        root.addView(panel, FrameLayout.LayoutParams((320 * resources.displayMetrics.density).toInt(), -1, Gravity.END))
        setContentView(root)
        video.onInputSurface = { surface ->
            player?.release(); player = null
            if (surface != null) {
                val p = MediaPlayer()
                player = p
                resources.openRawResourceFd(R.raw.filter_sample).use { fd ->
                    p.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                p.setSurface(surface)
                p.isLooping = true
                p.setOnPreparedListener { it.start() }
                p.prepareAsync()
            }
        }
    }
    override fun onResume() { super.onResume(); video.resumeVideo() }
    override fun onPause() { video.pauseVideo(); super.onPause() }
}
