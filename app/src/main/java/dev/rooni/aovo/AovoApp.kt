package dev.rooni.aovo

import android.app.Application
import dev.rooni.aovo.ble.AovoCore
import dev.rooni.aovo.data.Prefs

class AovoApp : Application() {

    lateinit var core: AovoCore
        private set

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        core = AovoCore(this)
        prefs = Prefs(this)
    }

    companion object {
        lateinit var instance: AovoApp
            private set
    }
}
