package dev.openandroiduse.companion.agent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import dev.openandroiduse.companion.CompanionService
import dev.openandroiduse.companion.R
import dev.openandroiduse.companion.StopAgentReceiver

/**
 * An ongoing notification shown while a task runs, with a Stop action — so the
 * user can halt the agent from the shade even with the app backgrounded.
 * Deliberately NOT a foreground service (the accessibility service is already
 * kept alive; foreground-service types are a Play-readiness concern). Guarded by
 * POST_NOTIFICATIONS: without it, this silently no-ops and the in-control badge
 * remains the guaranteed control.
 */
object AgentNotification {

    private const val CHANNEL_ID = "agent_activity"
    private const val NOTIFICATION_ID = 4421

    fun show(service: CompanionService) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            service.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                service.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = service.getString(R.string.notif_channel_desc)
            },
        )

        val stopPending = PendingIntent.getBroadcast(
            service,
            0,
            Intent(service, StopAgentReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val openPending = PendingIntent.getActivity(
            service,
            1,
            Intent(service, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(service, R.drawable.ic_launcher_monochrome),
            service.getString(R.string.action_stop),
            stopPending,
        ).build()

        val notification = Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(service.getString(R.string.notif_title))
            .setContentText(service.getString(R.string.notif_text))
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(stopAction)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    fun cancel(service: CompanionService) {
        try {
            (service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }
}
