package com.wifiradarx.app.ar

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import com.google.ar.core.Session

class DisplayRotationHelper(private val context: Context) : DisplayManager.DisplayListener {

    private var viewportChanged = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private val display: Display

    init {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        display = windowManager.defaultDisplay
    }

    fun onResume() {
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .registerDisplayListener(this, null)
    }

    fun onPause() {
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .unregisterDisplayListener(this)
    }

    fun onDisplayChanged() { viewportChanged = true }

    fun onSurfaceChanged(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    fun updateSessionIfNeeded(session: Session) {
        if (viewportChanged) {
            @Suppress("DEPRECATION")
            val displayRotation = display.rotation
            session.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
            viewportChanged = false
        }
    }

    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}
    override fun onDisplayChanged(displayId: Int) { viewportChanged = true }
}
