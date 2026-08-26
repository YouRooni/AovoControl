package dev.rooni.aovo.ride

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RideRepository(context: Context) {

    private val db = RideDatabase(context.applicationContext)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _rides = MutableStateFlow<List<RideSession>>(emptyList())
    val rides = _rides.asStateFlow()

    private val _stats = MutableStateFlow(OverallRideStats())
    val stats = _stats.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _rides.value = db.getAllRides(includeSamples = false)
            _stats.value = db.getOverallStats()
        }
    }

    suspend fun saveRide(ride: RideSession): Long {
        val id = db.insertRide(ride)
        refresh()
        return id
    }

    suspend fun getRideDetails(id: Long): RideSession? {
        return db.getRideById(id)
    }

    suspend fun deleteRide(id: Long) {
        db.deleteRide(id)
        refresh()
    }

    suspend fun clearHistory() {
        db.clearAll()
        refresh()
    }
}
