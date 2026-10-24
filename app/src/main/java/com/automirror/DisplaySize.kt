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
 * Cambia la resolución lógica del celular (lo mismo que `adb shell wm size`) para que tenga la
 * misma proporción que la pantalla del auto y la imagen la llene entera.
 *
 * Necesita WRITE_SECURE_SETTINGS, que solo se puede dar una vez por adb:
 *   adb shell pm grant com.automirror android.permission.WRITE_SECURE_SETTINGS
 * Sin ese permiso todo sigue funcionando, con franjas negras.
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
            Log.w(TAG, "No se pudo acceder a IWindowManager", e)
            null
        }
    }

    fun canChange(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** Ajusta la resolución para que, en horizontal, tenga la proporción ancho/alto del auto. */
    fun matchAspect(context: Context, carWidth: Int, carHeight: Int) {
        if (!canChange(context) || carWidth <= 0 || carHeight <= 0) return
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val mode = display.mode
        val physShort = min(mode.physicalWidth, mode.physicalHeight)
        val physLong = max(mode.physicalWidth, mode.physicalHeight)

        // Proporción del auto en horizontal (lado largo / lado corto).
        val aspect = max(carWidth, carHeight).toFloat() / min(carWidth, carHeight)
        var short = physShort
        var long = even(physShort * aspect)
        if (long > physLong) {
            long = physLong
            short = even(physLong / aspect)
        }

        // La resolución se fija en la orientación natural del equipo (vertical en un celular).
        val portraitNatural = mode.physicalWidth <= mode.physicalHeight
        val w = if (portraitNatural) short else long
        val h = if (portraitNatural) long else short
        if (call("setForcedDisplaySize", w, h)) {
            prefs(context).edit().putBoolean(KEY_FORCED, true).apply()
            Log.i(TAG, "Resolución forzada a ${w}x$h (auto ${carWidth}x$carHeight)")
        }
    }

    /** Vuelve a la resolución original, solo si la cambió esta app. */
    fun reset(context: Context) {
        if (!prefs(context).getBoolean(KEY_FORCED, false) || !canChange(context)) return
        if (call("clearForcedDisplaySize")) {
            prefs(context).edit().putBoolean(KEY_FORCED, false).apply()
            Log.i(TAG, "Resolución restaurada")
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
            Log.w(TAG, "Falló $method", e)
            false
        }
    }

    private fun even(value: Float) = (value / 2f).roundToInt() * 2

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
