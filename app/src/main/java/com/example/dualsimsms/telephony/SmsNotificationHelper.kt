package com.example.dualsimsms.telephony

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.dualsimsms.R
import com.example.dualsimsms.SmsAppContainer
import com.example.dualsimsms.ui.ConversationActivity

object SmsNotificationHelper {

    private const val CHANNEL_INCOMING = "sms_incoming"
    private const val CHANNEL_SEND_RESULT = "sms_send_result"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INCOMING,
                context.getString(R.string.notification_channel_incoming),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notification_channel_incoming_desc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SEND_RESULT,
                context.getString(R.string.notification_channel_send_result),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.notification_channel_send_result_desc) }
        )
    }

    fun notifyIncoming(context: Context, address: String, body: String, subId: Int?, threadId: Long?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = PendingIntent.getActivity(
            context,
            (threadId ?: address.hashCode()).toInt(),
            ConversationActivity.intent(context, threadId ?: -1L, address, subId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val senderName = runCatching { SmsAppContainer.contactName(context, address) }
            .getOrNull() ?: address
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(senderName)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(ContextCompat.getColor(context, R.color.brand_primary))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val id = (threadId ?: address.hashCode().toLong()).toInt() and 0x7FFFFFFF
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Notification permission denied: skip.
        }
    }
}
