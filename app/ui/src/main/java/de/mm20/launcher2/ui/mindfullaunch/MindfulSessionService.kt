package de.mm20.launcher2.ui.mindfullaunch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import de.mm20.launcher2.ui.R

/**
 * Does nothing by itself: while a watched app is in use it keeps the launcher process in the
 * foreground state, so HyperOS/MIUI doesn't freeze it and [MindfulInterrupter] keeps ticking.
 */
class MindfulSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService<NotificationManager>()!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.mindful_launch),
                    NotificationManager.IMPORTANCE_MIN
                ).apply { setShowBadge(false) }
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.timer_24px)
            .setContentTitle(getString(R.string.mindful_launch_session_notification))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setOngoing(true)
            .build()
        try {
            ServiceCompat.startForeground(
                this, ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            Log.e("MindfulSessionService", "startForeground failed", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "mindful_launch"
        private const val ID = 4711

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, MindfulSessionService::class.java))
            } catch (e: Exception) {
                Log.e("MindfulSessionService", "Could not start", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MindfulSessionService::class.java))
        }
    }
}
