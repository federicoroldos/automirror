package com.automirror

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Changes the phone's logical resolution (same as `adb shell wm size`) so it has the same
 * aspect ratio as the car's screen and the image fills it completely.
 *
 * Needs WRITE_SECURE_SETTINGS, which can only be granted once via adb:
 *   adb shell pm grant com.automirror android.permission.WRITE_SECURE_SETTINGS
 * Without that permission everything still works, with black bars.
 */
object DisplaySize {
    private const val TAG = "AutoMirror"
    private const val PREFS = "display_size"
    private const val KEY_FORCED = "forced"

    private val windowManager: Any? by lazy {
        try {
            HiddenApiBypass.addHiddenApiExemptions("L")
            Class.forName("android.view.WindowManagerGlobal")
                .getMethod("getWindowManagerService").invoke(null)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't access IWindowManager", e)
            null
        }
    }

    fun canChange(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Adjusts the resolution so that, in landscape, it has the car's width/height ratio. */
    fun matchAspect(context: Context, carWidth: Int, carHeight: Int) {
        if (!canChange(context) || carWidth <= 0 || carHeight <= 0) return
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        val physShort = min(mode.physicalWidth, mode.physicalHeight)
        val physLong = max(mode.physicalWidth, mode.physicalHeight)

        // The car's aspect ratio in landscape (long side / short side).
        val aspect = max(carWidth, carHeight).toFloat() / min(carWidth, carHeight)
        var short = physShort
        var long = even(physShort * aspect)
        if (long > physLong) {
            long = physLong
            short = even(physLong / aspect)
        }

        // The resolution is set in the device's natural orientation (portrait on a phone).
        val portraitNatural = mode.physicalWidth <= mode.physicalHeight
        val w = if (portraitNatural) short else long
        val h = if (portraitNatural) long else short
        if (call("setForcedDisplaySize", w, h)) {
            prefs(context).edit().putBoolean(KEY_FORCED, true).commit()
            Log.i(TAG, "Resolution forced to ${w}x$h (car ${carWidth}x$carHeight)")
        }
    }

    /** Restores the original resolution, only if this app changed it. */
    fun reset(context: Context) {
        if (!prefs(context).getBoolean(KEY_FORCED, false) || !canChange(context)) return
        if (call("clearForcedDisplaySize")) {
            prefs(context).edit().putBoolean(KEY_FORCED, false).commit()
            Log.i(TAG, "Resolution restored")
        }
    }

    private fun call(method: String, vararg size: Int): Boolean {
        val wm = windowManager ?: return false
        return try {
            if (size.isEmpty()) {
                wm.javaClass.getMethod(method, Int::class.javaPrimitiveType)
                    .invoke(wm, Display.DEFAULT_DISPLAY)
            } else {
                wm.javaClass.getMethod(method, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .invoke(wm, Display.DEFAULT_DISPLAY, size[0], size[1])
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "$method failed", e)
            false
        }
    }

    private fun even(value: Float) = (value / 2f).roundToInt() * 2

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
