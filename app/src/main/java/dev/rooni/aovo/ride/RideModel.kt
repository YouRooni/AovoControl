package dev.rooni.aovo.ride

import org.json.JSONArray
import org.json.JSONObject

data class RideSample(
    val timestamp: Long,
    val speed: Float,
    val current: Float,
    val voltage: Float,
    val powerWatts: Float,
    val battery: Int = -1,
    val escTemp: Float = 0f,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", timestamp)
        put("s", speed.toDouble())
        put("c", current.toDouble())
        put("v", voltage.toDouble())
        put("p", powerWatts.toDouble())
        if (battery >= 0) put("b", battery)
        if (escTemp > 0f) put("temp", escTemp.toDouble())
    }

    companion object {
        fun fromJson(json: JSONObject): RideSample = RideSample(
            timestamp = json.optLong("t"),
            speed = json.optDouble("s").toFloat(),
            current = json.optDouble("c").toFloat(),
            voltage = json.optDouble("v").toFloat(),
            powerWatts = json.optDouble("p").toFloat(),
            battery = json.optInt("b", -1),
            escTemp = json.optDouble("temp", 0.0).toFloat(),
        )
    }
}

data class RideSession(
    val id: Long = 0,
    val deviceName: String,
    val deviceAddress: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val distanceKm: Float,
    val maxSpeed: Float,
    val avgSpeed: Float,
    val startBattery: Int,
    val endBattery: Int,
    val batteryConsumed: Int,
    val startVoltage: Float,
    val endVoltage: Float,
    val maxCurrent: Float,
    val maxPowerWatts: Float,
    val avgEscTemp: Float = 0f,
    val samples: List<RideSample> = emptyList(),
) {
    fun samplesToJson(): String {
        val array = JSONArray()
        for (s in samples) {
            array.put(s.toJson())
        }
        return array.toString()
    }

    companion object {
        fun samplesFromJson(jsonStr: String?): List<RideSample> {
            if (jsonStr.isNullOrBlank()) return emptyList()
            return try {
                val array = JSONArray(jsonStr)
                val list = ArrayList<RideSample>(array.length())
                for (i in 0 until array.length()) {
                    list.add(RideSample.fromJson(array.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

data class OverallRideStats(
    val totalRides: Int = 0,
    val totalDistanceKm: Float = 0f,
    val totalDurationSeconds: Long = 0L,
    val totalBatteryConsumed: Int = 0,
    val maxSpeedRecord: Float = 0f,
    val maxPowerRecord: Float = 0f,
)
