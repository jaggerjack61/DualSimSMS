package com.example.dualsimsms.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.dualsimsms.data.SmsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives incoming SMS broadcasts, reassembles multipart messages, inserts
 * the completed message into the provider (preserving the receiving
 * subscription id) and issues a notification.
 */
class SmsReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse SMS", e)
            return
        }
        if (messages.isEmpty()) return

        val subId = readSubscriptionId(intent)
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val address = messages.firstOrNull()?.originatingAddress ?: ""
        if (address.isBlank() || body.isBlank()) return

        // Keep the broadcast alive until the provider insert and notification
        // complete; a receiver process may otherwise be killed after onReceive.
        val pendingResult = goAsync()
        scope.launch {
            try {
                val threadId = SmsStore.resolveThreadId(context, address)
                val inserted = SmsStore.insert(
                    context,
                    Telephony.Sms.Inbox.CONTENT_URI,
                    android.content.ContentValues().apply {
                        put(Telephony.TextBasedSmsColumns.ADDRESS, address)
                        put(Telephony.TextBasedSmsColumns.BODY, body)
                        put(Telephony.TextBasedSmsColumns.DATE, timestamp)
                        put(Telephony.TextBasedSmsColumns.READ, 0)
                        put(Telephony.TextBasedSmsColumns.THREAD_ID, threadId)
                        if (subId != null) put(Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID, subId)
                    }
                )
                SmsNotificationHelper.notifyIncoming(
                    context, address, body, subId,
                    threadId = if (inserted != null) threadId else null
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not store or notify for incoming SMS", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Devices report the receiving subscription under varying extra names. */
    private fun readSubscriptionId(intent: Intent): Int? {
        val candidates = listOf("subscription", "subId", "simId", "slot", "phone")
        for (key in candidates) {
            val value = intent.getIntExtra(key, -1)
            if (value > 0) return value
        }
        return null
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
