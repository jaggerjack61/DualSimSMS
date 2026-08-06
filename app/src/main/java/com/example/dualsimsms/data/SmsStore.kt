package com.example.dualsimsms.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony
import android.util.Log
import com.example.dualsimsms.model.Message
import com.example.dualsimsms.model.SendState

/**
 * Low-level access to the SMS provider. Every write is wrapped in an
 * exception guard: the provider only accepts writes from the default SMS
 * app, so callers must degrade gracefully when the role is not held.
 */
object SmsStore {

    private const val TAG = "SmsStore"

    fun query(
        context: Context,
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): List<Message> {
        val projection = arrayOf(
            BaseColumns._ID,
            Telephony.TextBasedSmsColumns.THREAD_ID,
            Telephony.TextBasedSmsColumns.ADDRESS,
            Telephony.TextBasedSmsColumns.BODY,
            Telephony.TextBasedSmsColumns.DATE,
            Telephony.TextBasedSmsColumns.READ,
            Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID,
            Telephony.TextBasedSmsColumns.TYPE,
            Telephony.TextBasedSmsColumns.STATUS
        )
        val messages = mutableListOf<Message>()
        try {
            context.contentResolver.query(
                uri, projection, selection, selectionArgs, "${Telephony.TextBasedSmsColumns.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                val threadIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.THREAD_ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.DATE)
                val readIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.READ)
                val subIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.TYPE)
                val statusIndex = cursor.getColumnIndex(Telephony.TextBasedSmsColumns.STATUS)
                while (cursor.moveToNext()) {
                    val subId = if (subIndex >= 0) cursor.getInt(subIndex).takeIf { it > 0 } else null
                    messages += Message(
                        id = cursor.getLong(idIndex),
                        threadId = cursor.getLong(threadIndex),
                        address = cursor.getString(addressIndex) ?: "",
                        body = cursor.getString(bodyIndex) ?: "",
                        date = cursor.getLong(dateIndex),
                        read = cursor.getInt(readIndex) != 0,
                        subId = subId,
                        type = cursor.getInt(typeIndex),
                        status = if (statusIndex >= 0) cursor.getInt(statusIndex)
                        else Telephony.Sms.STATUS_NONE
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SMS read denied", e)
        }
        return messages
    }

    fun insert(context: Context, uri: Uri, values: ContentValues): Long? {
        return try {
            val uriResult = context.contentResolver.insert(uri, values)
            uriResult?.lastPathSegment?.toLongOrNull()
        } catch (e: SecurityException) {
            Log.w(TAG, "SMS write denied", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "SMS insert failed", e)
            null
        }
    }

    fun update(
        context: Context,
        uri: Uri,
        values: ContentValues,
        selection: String,
        selectionArgs: Array<String>
    ): Boolean = try {
        context.contentResolver.update(uri, values, selection, selectionArgs) > 0
    } catch (e: SecurityException) {
        Log.w(TAG, "SMS update denied", e)
        false
    } catch (e: Exception) {
        Log.w(TAG, "SMS update failed", e)
        false
    }

    fun delete(context: Context, uri: Uri, selection: String, selectionArgs: Array<String>): Boolean =
        try {
            context.contentResolver.delete(uri, selection, selectionArgs) > 0
        } catch (e: SecurityException) {
            Log.w(TAG, "SMS delete denied", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "SMS delete failed", e)
            false
        }

    fun resolveThreadId(context: Context, address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: SecurityException) {
            fallbackThreadId(address)
        } catch (e: Exception) {
            fallbackThreadId(address)
        }
    }

    fun fallbackThreadId(address: String): Long =
        if (address.isEmpty()) 0L else (address.hashCode().toLong() and Long.MAX_VALUE)

    fun insertDraft(context: Context, threadId: Long, address: String, body: String, subId: Int?): Long? {
        val values = ContentValues().apply {
            put(Telephony.TextBasedSmsColumns.ADDRESS, address)
            put(Telephony.TextBasedSmsColumns.BODY, body)
            put(Telephony.TextBasedSmsColumns.DATE, System.currentTimeMillis())
            put(Telephony.TextBasedSmsColumns.READ, 1)
            put(Telephony.TextBasedSmsColumns.THREAD_ID, threadId)
            put(Telephony.TextBasedSmsColumns.TYPE, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT)
            if (subId != null) put(Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID, subId)
        }
        return insert(context, Telephony.Sms.CONTENT_URI, values)
    }

    /** Updates exactly one draft. Box-specific provider URIs do not reliably
     * add their message type to update/delete selections on every OEM. */
    fun updateDraft(context: Context, draftId: Long, body: String, subId: Int?): Boolean {
        val values = ContentValues().apply {
            put(Telephony.TextBasedSmsColumns.BODY, body)
            put(Telephony.TextBasedSmsColumns.DATE, System.currentTimeMillis())
            if (subId != null) put(Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID, subId)
        }
        return update(
            context,
            Telephony.Sms.CONTENT_URI,
            values,
            "${BaseColumns._ID} = ? AND ${Telephony.TextBasedSmsColumns.TYPE} = ?",
            arrayOf(
                draftId.toString(),
                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT.toString()
            )
        )
    }

    fun deleteDraft(context: Context, threadId: Long): Boolean = delete(
        context,
        Telephony.Sms.CONTENT_URI,
        "${Telephony.TextBasedSmsColumns.THREAD_ID} = ? AND ${Telephony.TextBasedSmsColumns.TYPE} = ?",
        arrayOf(
            threadId.toString(),
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT.toString()
        )
    )

    fun deleteDraftById(context: Context, draftId: Long): Boolean = delete(
        context,
        Telephony.Sms.CONTENT_URI,
        "${BaseColumns._ID} = ? AND ${Telephony.TextBasedSmsColumns.TYPE} = ?",
        arrayOf(
            draftId.toString(),
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT.toString()
        )
    )

    fun insertOutgoing(context: Context, address: String, body: String, subId: Int): Long? {
        val values = ContentValues().apply {
            put(Telephony.TextBasedSmsColumns.ADDRESS, address)
            put(Telephony.TextBasedSmsColumns.BODY, body)
            put(Telephony.TextBasedSmsColumns.DATE, System.currentTimeMillis())
            put(Telephony.TextBasedSmsColumns.READ, 1)
            put(Telephony.TextBasedSmsColumns.TYPE, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX)
            put(Telephony.TextBasedSmsColumns.STATUS, Telephony.Sms.STATUS_PENDING)
            put(Telephony.TextBasedSmsColumns.SUBSCRIPTION_ID, subId)
        }
        return insert(context, Telephony.Sms.CONTENT_URI, values)
    }

    /**
     * Marks the send result in place. Keeping the provider row id stable lets
     * the later delivery PendingIntent update the same message to delivered.
     */
    fun finalizeOutgoing(context: Context, rowId: Long, state: SendState) {
        val sent = state == SendState.SENT
        update(
            context,
            Telephony.Sms.CONTENT_URI,
            ContentValues().apply {
                put(
                    Telephony.TextBasedSmsColumns.TYPE,
                    if (sent) Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT
                    else Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED
                )
                // A successful send is still awaiting the carrier delivery
                // report. SmsDeliveredReceiver changes this to COMPLETE.
                put(
                    Telephony.TextBasedSmsColumns.STATUS,
                    if (sent) Telephony.Sms.STATUS_PENDING else Telephony.Sms.STATUS_FAILED
                )
            },
            "${BaseColumns._ID} = ?",
            arrayOf(rowId.toString())
        )
    }

}

