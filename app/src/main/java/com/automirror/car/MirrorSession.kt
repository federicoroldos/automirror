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
                // Se desconectó el auto: no tiene sentido seguir capturando la pantalla.
                MirrorService.stop(carContext)
            }
        })
    }

    override fun onCreateScreen(intent: Intent): Screen = MirrorScreen(carContext)
}
