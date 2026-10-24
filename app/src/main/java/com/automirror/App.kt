package com.automirror

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Mirror.init(this)
        // If the process is starting, nothing is being mirrored: if a forced resolution was left
        // behind (for example, because the app was killed abruptly), restore it.
        DisplaySize.reset(this)
    }
}
