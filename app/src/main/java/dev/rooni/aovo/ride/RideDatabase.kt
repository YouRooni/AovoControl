package dev.rooni.aovo.ride

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RideDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_RIDES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DEVICE_NAME TEXT NOT NULL,
                $COL_DEVICE_ADDRESS TEXT NOT NULL,
                $COL_START_TIME INTEGER NOT NULL,
                $COL_END_TIME INTEGER NOT NULL,
                $COL_DURATION INTEGER NOT NULL,
                $COL_DISTANCE REAL NOT NULL,
                $COL_MAX_SPEED REAL NOT NULL,
                $COL_AVG_SPEED REAL NOT NULL,
                $COL_START_BATTERY INTEGER NOT NULL,
                $COL_END_BATTERY INTEGER NOT NULL,
                $COL_BATTERY_CONSUMED INTEGER NOT NULL,
                $COL_START_VOLTAGE REAL NOT NULL,
                $COL_END_VOLTAGE REAL NOT NULL,
                $COL_MAX_CURRENT REAL NOT NULL,
                $COL_MAX_POWER REAL NOT NULL,
                $COL_AVG_ESC_TEMP REAL NOT NULL,
                $COL_SAMPLES TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_rides_start_time ON $TABLE_RIDES ($COL_START_TIME DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations
    }

    suspend fun insertRide(ride: RideSession): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_DEVICE_NAME, ride.deviceName)
            put(COL_DEVICE_ADDRESS, ride.deviceAddress)
            put(COL_START_TIME, ride.startTime)
            put(COL_END_TIME, ride.endTime)
            put(COL_DURATION, ride.durationSeconds)
            put(COL_DISTANCE, ride.distanceKm)
            put(COL_MAX_SPEED, ride.maxSpeed)
            put(COL_AVG_SPEED, ride.avgSpeed)
            put(COL_START_BATTERY, ride.startBattery)
            put(COL_END_BATTERY, ride.endBattery)
            put(COL_BATTERY_CONSUMED, ride.batteryConsumed)
            put(COL_START_VOLTAGE, ride.startVoltage)
            put(COL_END_VOLTAGE, ride.endVoltage)
            put(COL_MAX_CURRENT, ride.maxCurrent)
            put(COL_MAX_POWER, ride.maxPowerWatts)
            put(COL_AVG_ESC_TEMP, ride.avgEscTemp)
            put(COL_SAMPLES, ride.samplesToJson())
        }
        writableDatabase.insert(TABLE_RIDES, null, values)
    }

    suspend fun getAllRides(includeSamples: Boolean = false): List<RideSession> = withContext(Dispatchers.IO) {
        val columns = if (includeSamples) null else arrayOf(
            COL_ID, COL_DEVICE_NAME, COL_DEVICE_ADDRESS, COL_START_TIME, COL_END_TIME,
            COL_DURATION, COL_DISTANCE, COL_MAX_SPEED, COL_AVG_SPEED, COL_START_BATTERY,
            COL_END_BATTERY, COL_BATTERY_CONSUMED, COL_START_VOLTAGE, COL_END_VOLTAGE,
            COL_MAX_CURRENT, COL_MAX_POWER, COL_AVG_ESC_TEMP
        )
        val list = mutableListOf<RideSession>()
        val cursor: Cursor = readableDatabase.query(
            TABLE_RIDES,
            columns,
            null,
            null,
            null,
            null,
            "$COL_START_TIME DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.toRideSession(includeSamples))
            }
        }
        list
    }

    suspend fun getRideById(id: Long): RideSession? = withContext(Dispatchers.IO) {
        val cursor = readableDatabase.query(
            TABLE_RIDES,
            null,
            "$COL_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                it.toRideSession(includeSamples = true)
            } else {
                null
            }
        }
    }

    suspend fun deleteRide(id: Long): Int = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_RIDES, "$COL_ID = ?", arrayOf(id.toString()))
    }

    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_RIDES, null, null)
    }

    suspend fun getOverallStats(): OverallRideStats = withContext(Dispatchers.IO) {
        val query = """
            SELECT 
                COUNT(*) as count,
                SUM($COL_DISTANCE) as total_dist,
                SUM($COL_DURATION) as total_dur,
                SUM($COL_BATTERY_CONSUMED) as total_bat,
                MAX($COL_MAX_SPEED) as max_speed,
                MAX($COL_MAX_POWER) as max_power
            FROM $TABLE_RIDES
        """.trimIndent()
        val cursor = readableDatabase.rawQuery(query, null)
        cursor.use {
            if (it.moveToFirst()) {
                OverallRideStats(
                    totalRides = it.getInt(it.getColumnIndexOrThrow("count")),
                    totalDistanceKm = it.getFloat(it.getColumnIndexOrThrow("total_dist")),
                    totalDurationSeconds = it.getLong(it.getColumnIndexOrThrow("total_dur")),
                    totalBatteryConsumed = it.getInt(it.getColumnIndexOrThrow("total_bat")),
                    maxSpeedRecord = it.getFloat(it.getColumnIndexOrThrow("max_speed")),
                    maxPowerRecord = it.getFloat(it.getColumnIndexOrThrow("max_power")),
                )
            } else {
                OverallRideStats()
            }
        }
    }

    private fun Cursor.toRideSession(includeSamples: Boolean): RideSession {
        val samplesJson = if (includeSamples && getColumnIndex(COL_SAMPLES) >= 0) {
            getString(getColumnIndexOrThrow(COL_SAMPLES))
        } else {
            null
        }
        return RideSession(
            id = getLong(getColumnIndexOrThrow(COL_ID)),
            deviceName = getString(getColumnIndexOrThrow(COL_DEVICE_NAME)),
            deviceAddress = getString(getColumnIndexOrThrow(COL_DEVICE_ADDRESS)),
            startTime = getLong(getColumnIndexOrThrow(COL_START_TIME)),
            endTime = getLong(getColumnIndexOrThrow(COL_END_TIME)),
            durationSeconds = getLong(getColumnIndexOrThrow(COL_DURATION)),
            distanceKm = getFloat(getColumnIndexOrThrow(COL_DISTANCE)),
            maxSpeed = getFloat(getColumnIndexOrThrow(COL_MAX_SPEED)),
            avgSpeed = getFloat(getColumnIndexOrThrow(COL_AVG_SPEED)),
            startBattery = getInt(getColumnIndexOrThrow(COL_START_BATTERY)),
            endBattery = getInt(getColumnIndexOrThrow(COL_END_BATTERY)),
            batteryConsumed = getInt(getColumnIndexOrThrow(COL_BATTERY_CONSUMED)),
            startVoltage = getFloat(getColumnIndexOrThrow(COL_START_VOLTAGE)),
            endVoltage = getFloat(getColumnIndexOrThrow(COL_END_VOLTAGE)),
            maxCurrent = getFloat(getColumnIndexOrThrow(COL_MAX_CURRENT)),
            maxPowerWatts = getFloat(getColumnIndexOrThrow(COL_MAX_POWER)),
            avgEscTemp = getFloat(getColumnIndexOrThrow(COL_AVG_ESC_TEMP)),
            samples = if (includeSamples) RideSession.samplesFromJson(samplesJson) else emptyList(),
        )
    }

    companion object {
        const val DATABASE_NAME = "rides.db"
        const val DATABASE_VERSION = 1

        const val TABLE_RIDES = "rides"
        const val COL_ID = "id"
        const val COL_DEVICE_NAME = "device_name"
        const val COL_DEVICE_ADDRESS = "device_address"
        const val COL_START_TIME = "start_time"
        const val COL_END_TIME = "end_time"
        const val COL_DURATION = "duration"
        const val COL_DISTANCE = "distance"
        const val COL_MAX_SPEED = "max_speed"
        const val COL_AVG_SPEED = "avg_speed"
        const val COL_START_BATTERY = "start_battery"
        const val COL_END_BATTERY = "end_battery"
        const val COL_BATTERY_CONSUMED = "battery_consumed"
        const val COL_START_VOLTAGE = "start_voltage"
        const val COL_END_VOLTAGE = "end_voltage"
        const val COL_MAX_CURRENT = "max_current"
        const val COL_MAX_POWER = "max_power"
        const val COL_AVG_ESC_TEMP = "avg_esc_temp"
        const val COL_SAMPLES = "samples"
    }
}
