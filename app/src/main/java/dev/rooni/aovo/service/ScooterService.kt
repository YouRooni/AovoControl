package dev.rooni.aovo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.rooni.aovo.AovoApp
import dev.rooni.aovo.MainActivity
import dev.rooni.aovo.R
import dev.rooni.aovo.ble.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

class ScooterService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observerJob: Job? = null
    private var lastNotificationUpdate = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_LIGHT -> {
                val core = AovoApp.instance.core
                val current = core.ride.value.headLight
                core.applyRide { it.copy(headLight = !current) }
            }
            ACTION_DISCONNECT -> {
                AovoApp.instance.core.disconnect()
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
        }

        startInForeground()
        observeScooterState()

        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification(
            deviceName = AovoApp.instance.core.connectedDevice.value?.name ?: getString(R.string.app_name),
            speed = AovoApp.instance.core.telemetry.value.speed,
            battery = AovoApp.instance.core.telemetry.value.battery,
            voltage = AovoApp.instance.core.telemetry.value.voltage,
            lightOn = AovoApp.instance.core.ride.value.headLight,
            connected = AovoApp.instance.core.connection.value == ConnectionState.CONNECTED,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeScooterState() {
        observerJob?.cancel()
        val core = AovoApp.instance.core
        val prefs = AovoApp.instance.prefs

        observerJob = serviceScope.launch {
            combine(
                core.connection,
                core.connectedDevice,
                core.telemetry,
                core.ride,
                prefs.settings,
            ) { connection, device, telemetry, ride, settings ->
                if (!settings.backgroundService) {
                    stopForegroundService()
                    return@combine
                }

                if (connection == ConnectionState.DISCONNECTED || connection == ConnectionState.IDLE) {
                    stopForegroundService()
                    return@combine
                }

                val now = System.currentTimeMillis()
                if (now - lastNotificationUpdate >= 1000L) {
                    lastNotificationUpdate = now
                    val notification = buildNotification(
                        deviceName = device?.name ?: getString(R.string.app_name),
                        speed = telemetry.speed,
                        battery = telemetry.battery,
                        voltage = telemetry.voltage,
                        lightOn = ride.headLight,
                        connected = connection == ConnectionState.CONNECTED,
                    )
                    val manager = getSystemService(NotificationManager::class.java)
                    manager?.notify(NOTIFICATION_ID, notification)
                }
            }.collect {}
        }
    }

    private fun buildNotification(
        deviceName: String,
        speed: Float,
        battery: Int,
        voltage: Float,
        lightOn: Boolean,
        connected: Boolean,
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLightIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScooterService::class.java).apply {
                action = ACTION_TOGGLE_LIGHT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ScooterService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (connected) deviceName else "$deviceName (${getString(R.string.connecting)})"
        val contentText = if (connected) {
            String.format(
                Locale.getDefault(),
                "%.1f км/ч  ·  🔋 %d%% (%.1f V)",
                speed,
                battery,
                voltage
            )
        } else {
            getString(R.string.connecting)
        }

        val lightActionTitle = if (lightOn) getString(R.string.headlight) + " ✕" else getString(R.string.headlight) + " ✓"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, lightActionTitle, toggleLightIntent)
            .addAction(0, getString(R.string.disconnect), disconnectIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.scooter_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.scooter_service_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        observerJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "scooter_live_service"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "dev.rooni.aovo.action.START_SERVICE"
        const val ACTION_STOP = "dev.rooni.aovo.action.STOP_SERVICE"
        const val ACTION_TOGGLE_LIGHT = "dev.rooni.aovo.action.TOGGLE_LIGHT"
        const val ACTION_DISCONNECT = "dev.rooni.aovo.action.DISCONNECT"

        fun start(context: Context) {
            val intent = Intent(context, ScooterService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScooterService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
            }
        }
    }
}
