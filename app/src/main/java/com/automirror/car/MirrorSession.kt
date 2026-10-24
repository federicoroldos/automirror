package com.automirror.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.automirror.MirrorService

class MirrorSession : Session() {
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                // The car disconnected: there's no point in keeping the screen capture going.
                MirrorService.stop(carContext)
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen = MirrorScreen(carContext)
}
