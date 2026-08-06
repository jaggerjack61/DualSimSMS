package com.example.dualsimsms.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.example.dualsimsms.data.SmsStore
import com.example.dualsimsms.model.SendState
import com.example.dualsimsms.util.SendStateMapper

/**
 * Receives per-part send results from SmsManager. Once every part of a
 * message has reported, the row is finalized in the provider (Sent on
 * success, Failed otherwise). The first failing part decides the failure
 * state of the whole message.
 */
class SmsSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val runId = intent.getLongExtra(SmsExtras.EXTRA_RUN_ID, -1L)
        val rowId = intent.getLongExtra(SmsExtras.EXTRA_ROW_ID, -1L)
        val partCount = intent.getIntExtra(SmsExtras.EXTRA_PART_COUNT, 1)
        if (runId < 0 || rowId < 0) return

        val state = SendStateMapper.fromSendResult(resultCode)

        val prefs = context.getSharedPreferences(PREFS_SEND_TRACKING, Context.MODE_PRIVATE)
        val key = "parts_$runId"
        val failKey = "fail_$runId"
        val remaining = prefs.getInt(key, partCount) - 1
        val failedOrdinal = prefs.getInt(failKey, -1).let { stored ->
            when {
                state != SendState.SENT && stored < 0 -> state.ordinal
                state != SendState.SENT -> stored
                else -> stored
            }
        }

        if (remaining <= 0) {
            val finalState = if (failedOrdinal >= 0) SendState.entries[failedOrdinal] else SendState.SENT
            SmsStore.finalizeOutgoing(context, rowId, finalState)
            prefs.edit {
                remove(key)
                remove(failKey)
            }
        } else {
            prefs.edit {
                putInt(key, remaining)
                if (failedOrdinal >= 0) putInt(failKey, failedOrdinal)
            }
        }
    }

    private companion object {
        const val PREFS_SEND_TRACKING = "send_tracking"
    }
}
