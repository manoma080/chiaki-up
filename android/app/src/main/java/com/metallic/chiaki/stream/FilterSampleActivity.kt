// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.gameblabla.chiaki.stream

import android.app.Activity
import android.media.MediaPlayer
import android.os.Bundle
import com.gameblabla.chiaki.R

/** Standalone test entry point: adb shell am start -n com.gameblabla.chiaki/.stream.FilterSampleActivity */
class FilterSampleActivity : Activity() {
    private lateinit var video: FilterVideoView
    private var player: MediaPlayer? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        video = FilterVideoView(this)
        setContentView(video)
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
