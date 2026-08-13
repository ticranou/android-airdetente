package com.airchecklists.app.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.airchecklists.app.MainActivity
import com.airchecklists.app.R

/**
 * Always-on foreground service that keeps AirDetente near-last on the OS reclaim
 * list during a flight. Shows a persistent "vol en cours" notification with a
 * "Quitter" action that closes the app cleanly. Started at app launch, stopped only
 * on explicit quit.
 */
class FlightService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_QUIT) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = ensureChannel()

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        // "Quitter" → tells MainActivity to shut everything down. The SINGLE_TOP +
        // CLEAR_TOP flags ensure the running activity receives this via onNewIntent
        // (otherwise Android just brings it to front and drops the quit action).
        val quit = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_QUIT_APP
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AirDetente — vol en cours")
            .setContentText("Instruments actifs. Touchez pour revenir.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            // A real icon is required: some OEM launchers silently drop actions whose
            // icon is 0, which hid the "Quitter" button on certain tablets.
            .addAction(
                NotificationCompat.Action.Builder(R.mipmap.ic_launcher, "Quitter", quit).build(),
            )
            .build()
    }

    private fun ensureChannel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Vol en cours", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Maintient l'application active pendant le vol."
                        setShowBadge(false)
                    },
                )
            }
        }
        return CHANNEL_ID
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    companion object {
        private const val CHANNEL_ID = "flight_service"
        private const val NOTIF_ID = 1001
        const val ACTION_QUIT = "com.airchecklists.app.FLIGHT_QUIT"

        fun start(context: Context) {
            val i = Intent(context, FlightService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FlightService::class.java))
        }
    }
}
