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

/** Transparent Activity that only shows Android's dialog to allow the capture. */
class ProjectionActivity : Activity() {

    companion object {
        private const val REQUEST_CAPTURE = 1

        fun launch(context: Context) {
            // Always on the phone's screen: the CarContext points to the car's screen, where
            // Android doesn't allow opening regular Activities.
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
            // Asks for the entire screen (not "a single app"), which is what makes sense to mirror.
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            mpm.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_CAPTURE)
    }

    @Deprecated("Activity without AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            MirrorService.start(this, resultCode, data)
        }
        finish()
    }
}
