package com.gameblabla.chiaki.stream

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Debug-only bridge; the sample itself stays private. */
class FilterSampleLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, FilterSampleActivity::class.java))
        finish()
    }
}
