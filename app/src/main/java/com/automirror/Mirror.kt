package com.automirror

import android.content.Context
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface

/**
 * State shared between the phone side (capture) and the car side (surface).
 *
 * The phone provides a [MediaProjection]; the car provides a [Surface]. When both are present,
 * a VirtualDisplay is created that mirrors the phone's screen straight onto the car's surface.
 * The system scales the image keeping its aspect ratio and centers it, so [carToPhone] only has
 * to undo that transformation.
 *
 * Everything is used from the main thread.
 */
object Mirror {
    private const val TAG = "AutoMirror"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()

    private lateinit var appContext: Context
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var carSurface: Surface? = null
    private var carWidth = 0
    private var carHeight = 0
    private var carDpi = 0

    val isMirroring: Boolean get() = projection != null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun addListener(listener: () -> Unit) { listeners += listener }

    fun removeListener(listener: () -> Unit) { listeners -= listener }

    private fun notifyChanged() = listeners.toList().forEach { it() }

    fun startProjection(newProjection: MediaProjection) {
        stopProjection()
        projection = newProjection
        newProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped by the system")
                if (projection === newProjection) releaseProjection()
            }
        }, mainHandler)
        bind()
        notifyChanged()
    }

    fun stopProjection() {
        val current = projection ?: return
        releaseProjection()
        current.stop()
    }

    private fun releaseProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        projection = null
        DisplaySize.reset(appContext)
        notifyChanged()
    }

    fun setCarSurface(surface: Surface?, width: Int, height: Int, dpi: Int) {
        carSurface = surface
        carWidth = width
        carHeight = height
        carDpi = dpi
        bind()
    }

    private fun bind() {
        val p = projection ?: return
        val surface = carSurface
        val vd = virtualDisplay
        // If the car changes size, the phone adapts. If the surface goes away (the user opened
        // another app on the car), it's left as is until mirroring ends.
        if (surface != null) DisplaySize.matchAspect(appContext, carWidth, carHeight)
        if (vd == null) {
            // Since Android 14 each MediaProjection can only create a single VirtualDisplay, so
            // it's created only once there's a surface, and after that only its target changes.
            if (surface == null) return
            virtualDisplay = p.createVirtualDisplay(
                "AutoMirror", carWidth, carHeight, carDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, mainHandler
            )
            Log.i(TAG, "VirtualDisplay created ${carWidth}x$carHeight @$carDpi")
        } else if (surface == null) {
            vd.surface = null
        } else {
            vd.resize(carWidth, carHeight, carDpi)
            vd.surface = surface
        }
    }

    /** Converts a point on the car's surface to coordinates on the phone's screen. */
    fun carToPhone(x: Float, y: Float): PointF? {
        if (carWidth == 0 || carHeight == 0) return null
        val phone = phoneSize()
        val scale = minOf(carWidth.toFloat() / phone.x, carHeight.toFloat() / phone.y)
        val offsetX = (carWidth - phone.x * scale) / 2f
        val offsetY = (carHeight - phone.y * scale) / 2f
        val px = (x - offsetX) / scale
        val py = (y - offsetY) / scale
        if (px < 0 || py < 0 || px >= phone.x || py >= phone.y) return null
        return PointF(px, py)
    }

    /** How many phone pixels one pixel of the car's surface is worth. */
    fun carToPhoneScale(): Float {
        if (carWidth == 0 || carHeight == 0) return 1f
        val phone = phoneSize()
        return 1f / minOf(carWidth.toFloat() / phone.x, carHeight.toFloat() / phone.y)
    }

    fun phoneSize(): PointF {
        val display = appContext.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val size = android.graphics.Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        return PointF(size.x.toFloat(), size.y.toFloat())
    }
}
