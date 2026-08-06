package com.example.dualsimsms.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.BaseColumns
import android.provider.Telephony
import androidx.core.content.edit
import com.example.dualsimsms.data.SmsStore

/**
 * Updates delivery status in the provider. A delivery report (RESULT_OK)
 * marks the message as delivered.
 */
class SmsDeliveredReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getLongExtra(SmsExtras.EXTRA_RUN_ID, -1L)
        val rowId = intent.getLongExtra(SmsExtras.EXTRA_ROW_ID, -1L)
        val partCount = intent.getIntExtra(SmsExtras.EXTRA_PART_COUNT, 1)
        if (runId < 0 || rowId < 0) return

        // Multipart messages are delivered one part at a time. Show the
        // second check only after every part has a successful report.
        val prefs = context.getSharedPreferences(PREFS_DELIVERY_TRACKING, Context.MODE_PRIVATE)
        val remainingKey = "parts_$runId"
        val failedKey = "failed_$runId"
        val remaining = prefs.getInt(remainingKey, partCount) - 1
        val failed = prefs.getBoolean(failedKey, false) || resultCode != Activity.RESULT_OK

        if (remaining <= 0) {
            if (!failed) {
                val values = android.content.ContentValues().apply {
                    put(Telephony.TextBasedSmsColumns.STATUS, Telephony.Sms.STATUS_COMPLETE)
                }
                SmsStore.update(
                    context,
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "${BaseColumns._ID} = ? AND ${Telephony.TextBasedSmsColumns.TYPE} = ?",
                    arrayOf(
                        rowId.toString(),
                        Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT.toString()
                    )
                )
            }
            prefs.edit {
                remove(remainingKey)
                remove(failedKey)
            }
        } else {
            prefs.edit {
                putInt(remainingKey, remaining)
                putBoolean(failedKey, failed)
            }
        }
    }

    private companion object {
        const val PREFS_DELIVERY_TRACKING = "delivery_tracking"
    }
}

