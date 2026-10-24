package com.automirror.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

class MirrorCarAppService : CarAppService() {
    // App de uso personal, instalada a mano: se acepta cualquier host (Android Auto, DHU).
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onCreateSession(): Session = MirrorSession()
}
