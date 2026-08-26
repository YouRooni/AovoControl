package dev.rooni.aovo

import android.app.Application
import dev.rooni.aovo.ble.AovoCore
import dev.rooni.aovo.data.Prefs

class AovoApp : Application() {

    lateinit var core: AovoCore
        private set

    lateinit var prefs: Prefs
        private set

    lateinit var rideRepository: dev.rooni.aovo.ride.RideRepository
        private set

    lateinit var rideTracker: dev.rooni.aovo.ride.RideTracker
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        core = AovoCore(this)
        prefs = Prefs(this)
        rideRepository = dev.rooni.aovo.ride.RideRepository(this)
        rideTracker = dev.rooni.aovo.ride.RideTracker(rideRepository, core).apply { start() }
    }

    companion object {
        lateinit var instance: AovoApp
            private set
    }
}
