package com.gameblabla.chiaki.stream

/** System bar changes can be caused by focus or device changes, not a gesture. */
internal class OverlayVisibilityPolicy(
    private val show: () -> Unit,
    private val hide: () -> Unit,
    private val scheduleHide: () -> Unit
) {
    fun systemUiChanged(fullscreen: Boolean) {
        if (fullscreen) hide() else scheduleHide()
    }

    fun hover() = show()
}
