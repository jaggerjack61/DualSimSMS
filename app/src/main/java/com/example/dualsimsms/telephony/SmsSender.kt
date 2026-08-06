package com.example.dualsimsms.telephony

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.dualsimsms.model.SendState
import com.example.dualsimsms.util.SmsManagerCompat

object SmsExtras {
    const val EXTRA_RUN_ID = "com.example.dualsimsms.extra.RUN_ID"
    const val EXTRA_ROW_ID = "com.example.dualsimsms.extra.ROW_ID"
    const val EXTRA_PART_COUNT = "com.example.dualsimsms.extra.PART_COUNT"
    const val EXTRA_PART_INDEX = "com.example.dualsimsms.extra.PART_INDEX"
    const val EXTRA_ADDRESS = "com.example.dualsimsms.extra.ADDRESS"
    const val EXTRA_BODY = "com.example.dualsimsms.extra.BODY"
    const val EXTRA_SUB_ID = "com.example.dualsimsms.extra.SUB_ID"
    const val EXTRA_THREAD_ID = "com.example.dualsimsms.extra.THREAD_ID"
    const val EXTRA_NUMBER = "com.example.dualsimsms.extra.NUMBER"
}

/**
 * Sends SMS through [SmsManager.createForSubscriptionId] so the selected SIM
 * is always explicit. Long messages are split with divideMessage() and sent
 * as multipart SMS; per-part result intents update the provider afterwards.
 */
class SmsSender(private val context: Context) {

    fun send(address: String, body: String, subId: Int, runId: Long, rowId: Long): SendState {
        val manager = SmsManagerCompat.managerFor(context, subId)
            ?: return SendState.MISSING_SIM

        return try {
            val parts = manager.divideMessage(body)
            if (parts.isEmpty()) return SendState.FAILED

            if (parts.size == 1) {
                manager.sendTextMessage(
                    address, null, parts.first(),
                    sentIntent(runId, rowId, parts.size, 0, address),
                    deliveryIntent(runId, rowId, parts.size, 0, address)
                )
            } else {
                val sentIntents = parts.mapIndexed { index, _ ->
                    sentIntent(runId, rowId, parts.size, index, address)
                }.toCollection(ArrayList())
                val deliveryIntents = parts.mapIndexed { index, _ ->
                    deliveryIntent(runId, rowId, parts.size, index, address)
                }.toCollection(ArrayList())
                manager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveryIntents)
            }
            SendState.SENDING
        } catch (_: SecurityException) {
            SendState.NOT_PERMITTED
        } catch (_: IllegalArgumentException) {
            SendState.FAILED
        } catch (_: Exception) {
            SendState.FAILED
        }
    }

    private fun sentIntent(
        runId: Long,
        rowId: Long,
        partCount: Int,
        partIndex: Int,
        address: String
    ): PendingIntent {
        val intent = Intent(context, SmsSentReceiver::class.java).apply {
            putExtra(SmsExtras.EXTRA_RUN_ID, runId)
            putExtra(SmsExtras.EXTRA_ROW_ID, rowId)
            putExtra(SmsExtras.EXTRA_PART_COUNT, partCount)
            putExtra(SmsExtras.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsExtras.EXTRA_ADDRESS, address)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(runId, partIndex), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun deliveryIntent(
        runId: Long,
        rowId: Long,
        partCount: Int,
        partIndex: Int,
        address: String
    ): PendingIntent {
        val intent = Intent(context, SmsDeliveredReceiver::class.java).apply {
            putExtra(SmsExtras.EXTRA_RUN_ID, runId)
            putExtra(SmsExtras.EXTRA_ROW_ID, rowId)
            putExtra(SmsExtras.EXTRA_PART_COUNT, partCount)
            putExtra(SmsExtras.EXTRA_PART_INDEX, partIndex)
            putExtra(SmsExtras.EXTRA_ADDRESS, address)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(runId, partIndex) + DELIVERY_OFFSET, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(runId: Long, partIndex: Int): Int =
        (runId % 1_000_000L).toInt() * 10 + partIndex

    private companion object {
        const val DELIVERY_OFFSET = 500_000
    }
}
