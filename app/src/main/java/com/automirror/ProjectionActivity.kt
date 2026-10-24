package com.automirror

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Display

/** Activity transparente que solo muestra el diálogo de Android para permitir la captura. */
class ProjectionActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 1

        fun launch(context: Context) {
            // Siempre en la pantalla del celular: el CarContext apunta a la pantalla del auto,
            // donde Android no deja abrir Activities comunes.
            val app = context.applicationContext
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            app.startActivity(
                Intent(app, ProjectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                options.toBundle()
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Pide la pantalla completa (no "una sola app"), que es lo que tiene sentido espejar.
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CAPTURE)
    }

    @Deprecated("Activity sin AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            MirrorService.start(this, resultCode, data)
        }
        finish()
    }
}
