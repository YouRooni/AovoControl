package dev.rooni.aovo.ble

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.hobbywing.jni.Util
import java.io.File

class FirmwareRepository(private val context: Context) {

    private val databaseFile: File by lazy {
        val target = File(context.filesDir, DB_NAME)
        if (!target.exists() || target.length() == 0L) {
            runCatching {
                context.assets.open(DB_NAME).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure { Log.e(TAG, "cannot stage firmware catalogue", it) }
        }
        target
    }

    private fun <T> withDatabase(block: (SQLiteDatabase) -> T): T? {
        if (!databaseFile.exists()) return null
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            block(db)
        } catch (t: Throwable) {
            Log.e(TAG, "firmware catalogue query failed", t)
            null
        } finally {
            runCatching { db?.close() }
        }
    }

    /** Every firmware version published for this controller, newest first. */
    fun versionsFor(hardware: String): List<String> {
        if (hardware.isBlank()) return emptyList()
        return withDatabase { db ->
            db.rawQuery(SQL_VERSIONS, arrayOf(hardware)).use { cursor ->
                buildList {
                    val column = cursor.getColumnIndex("version")
                    if (column >= 0) {
                        while (cursor.moveToNext()) add(cursor.getString(column))
                    }
                }
            }
        }.orEmpty()
    }

    /** Decoded, flashable image for one catalogue entry. */
    fun load(hardware: String, version: String): ByteArray? {
        if (!Util.available) {
            Log.e(TAG, "native firmware unpacker unavailable")
            return null
        }
        val blob = withDatabase { db ->
            db.rawQuery(SQL_IMAGE, arrayOf(hardware, version)).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val column = cursor.getColumnIndex("bootloader")
                if (column < 0) null else cursor.getBlob(column)
            }
        } ?: return null

        return runCatching { Util.parse(blob, version, ByteArray(1)) }
            .getOrNull()
            ?.getOrNull(1)
    }

    private companion object {
        const val TAG = "FirmwareRepository"
        const val DB_NAME = "firmwares.db3"

        const val SQL_VERSIONS =
            "select a.version from software a left join controller b on a.controller_key=b.key " +
                "where b.name=? order by a.key desc"

        const val SQL_IMAGE =
            "select a.version, a.bootloader from software a " +
                "left join controller b on a.controller_key=b.key " +
                "where b.name=? and a.version=? order by a.key desc"

    }
}
