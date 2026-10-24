package com.automirror

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Mirror.init(this)
        // Si el proceso arranca, no hay espejado en curso: si quedó una resolución forzada
        // (por ejemplo, porque la app se cerró de golpe) se restaura.
        DisplaySize.reset(this)
    }
}
