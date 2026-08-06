package com.example.dualsimsms.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.example.dualsimsms.model.Message
import com.example.dualsimsms.model.SendState
import com.example.dualsimsms.util.MessageDeduplicator
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Folder(val messageTypes: Set<Int>) {
    INBOX(setOf(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX)),
    SENT(
        setOf(
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT,
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX,
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED,
            Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED
        )
    ),
    DRAFTS(setOf(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT));

    fun contains(message: Message): Boolean = message.type in messageTypes
}

private val THREAD_MESSAGE_TYPES = setOf(
    Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX,
    Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT,
    Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX,
    Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED,
    Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED
)

private fun typeSelection(types: Set<Int>): String =
    "${Telephony.TextBasedSmsColumns.TYPE} IN (${types.joinToString { "?" }})"

private fun typeArguments(types: Set<Int>): Array<String> =
    types.map(Int::toString).toTypedArray()

/**
 * Reads messages from the SMS provider and observes changes so the UI
 * refreshes automatically when messages are received, sent or changed.
 */
class SmsRepository(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val draftMutex = Mutex()

    fun messagesFor(folder: Folder): Flow<List<Message>> = callbackFlow {
        fun load(): List<Message> {
            // Inbox and Sent are deduplicated together before selecting the
            // folder. This removes exact rows that an OEM exposes once in each
            // box (for example, an Inbox row mirrored as Failed).
            val queryTypes = if (folder == Folder.DRAFTS) {
                folder.messageTypes
            } else {
                THREAD_MESSAGE_TYPES
            }
            val queried = SmsStore.query(
                context,
                Telephony.Sms.CONTENT_URI,
                typeSelection(queryTypes),
                typeArguments(queryTypes)
            )
            val sanitized = if (folder == Folder.DRAFTS) {
                removeStaleDraftCopies(queried.filter(folder::contains))
            } else {
                queried
            }
            return MessageDeduplicator.deduplicate(sanitized)
                .filter(folder::contains)
                .sortedByDescending { it.date }
        }

        trySend(load())

        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(load())
            }
        }
        try {
            // Observe the provider root: many devices only notify this URI even
            // when a draft/sent box URI was used for the write.
            context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (_: SecurityException) {
            // Read permission missing; the initial load already returned empty.
        }

        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun messagesForThread(threadId: Long, address: String): Flow<List<Message>> = callbackFlow {
        fun load(): List<Message> {
            val ownerColumn = if (threadId > 0) {
                Telephony.TextBasedSmsColumns.THREAD_ID
            } else {
                Telephony.TextBasedSmsColumns.ADDRESS
            }
            val ownerValue = if (threadId > 0) threadId.toString() else address
            val messages = SmsStore.query(
                context,
                Telephony.Sms.CONTENT_URI,
                "$ownerColumn = ? AND ${typeSelection(THREAD_MESSAGE_TYPES)}",
                arrayOf(ownerValue) + typeArguments(THREAD_MESSAGE_TYPES)
            ).filter { it.type in THREAD_MESSAGE_TYPES }
            // Drafts are deliberately excluded: their body belongs in the
            // composer, never in the conversation as a second message bubble.
            return MessageDeduplicator.deduplicate(messages).sortedBy { it.date }
        }

        trySend(load())

        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(load())
            }
        }
        try {
            context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (_: SecurityException) {
            // Ignore: read permission missing.
        }
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun draftBodyForThread(threadId: Long): Flow<String> = callbackFlow {
        fun load(): String = draftsForThread(threadId).firstOrNull()?.body.orEmpty()

        trySend(load())

        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(load())
            }
        }
        try {
            context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        } catch (_: SecurityException) {
            // Ignore: read permission missing.
        }
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    suspend fun saveDraft(threadId: Long, address: String, body: String, subId: Int?) {
        if (threadId <= 0) return
        draftMutex.withLock {
            if (body.isBlank()) {
                SmsStore.deleteDraft(context, threadId)
                return@withLock
            }

            val existing = draftsForThread(threadId)
            if (existing.isEmpty()) {
                SmsStore.insertDraft(context, threadId, address, body, subId)
            } else {
                // Keep one canonical draft and mutate only that row. Updating a
                // box URI by thread can overwrite every SMS in a thread on some
                // providers because the URI's type is not added to updates.
                val keep = existing.maxWith(compareBy<Message>(Message::date).thenBy(Message::id))
                existing.asSequence()
                    .filter { it.id != keep.id }
                    .forEach { duplicate -> SmsStore.deleteDraftById(context, duplicate.id) }
                SmsStore.updateDraft(context, keep.id, body, subId)
            }
        }
    }

    suspend fun deleteDraft(threadId: Long) {
        if (threadId <= 0) return
        draftMutex.withLock { SmsStore.deleteDraft(context, threadId) }
    }

    private fun draftsForThread(threadId: Long): List<Message> = removeStaleDraftCopies(
        SmsStore.query(
            context,
            Telephony.Sms.CONTENT_URI,
            "${Telephony.TextBasedSmsColumns.THREAD_ID} = ? AND " +
                "${Telephony.TextBasedSmsColumns.TYPE} = ?",
            arrayOf(
                threadId.toString(),
                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT.toString()
            )
        ).filter { it.type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT }
    )

    /**
     * Older builds could leave a draft row that was byte-for-byte stamped like
     * a real message. Such a row is not user work: it is the sent/received row
     * mirrored into the composer. Remove only exact thread/body/date matches.
     */
    private fun removeStaleDraftCopies(drafts: List<Message>): List<Message> =
        drafts.filterNot { draft ->
            val ownerColumn = if (draft.threadId > 0) {
                Telephony.TextBasedSmsColumns.THREAD_ID
            } else {
                Telephony.TextBasedSmsColumns.ADDRESS
            }
            val ownerValue = if (draft.threadId > 0) draft.threadId.toString() else draft.address
            val isCopy = SmsStore.query(
                context,
                Telephony.Sms.CONTENT_URI,
                "$ownerColumn = ? AND ${typeSelection(THREAD_MESSAGE_TYPES)} AND " +
                    "${Telephony.TextBasedSmsColumns.BODY} = ? AND " +
                    "${Telephony.TextBasedSmsColumns.DATE} = ?",
                arrayOf(ownerValue) + typeArguments(THREAD_MESSAGE_TYPES) +
                    arrayOf(draft.body, draft.date.toString())
            ).isNotEmpty()
            if (isCopy) SmsStore.deleteDraftById(context, draft.id)
            isCopy
        }

    suspend fun markThreadAsRead(threadId: Long) {
        if (threadId <= 0) return
        SmsStore.update(
            context,
            Telephony.Sms.CONTENT_URI,
            android.content.ContentValues().apply {
                put(Telephony.TextBasedSmsColumns.READ, 1)
            },
            "${Telephony.TextBasedSmsColumns.THREAD_ID} = ? AND " +
                "${Telephony.TextBasedSmsColumns.TYPE} = ? AND " +
                "${Telephony.TextBasedSmsColumns.READ} = 0",
            arrayOf(
                threadId.toString(),
                Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX.toString()
            )
        )
    }

    suspend fun insertOutgoing(address: String, body: String, subId: Int): Long? =
        SmsStore.insertOutgoing(context, address, body, subId)

    suspend fun finalizeOutgoing(rowId: Long, state: SendState) {
        SmsStore.finalizeOutgoing(context, rowId, state)
    }
}
