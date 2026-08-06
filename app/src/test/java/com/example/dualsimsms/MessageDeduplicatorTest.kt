package com.example.dualsimsms

import android.provider.Telephony
import com.example.dualsimsms.model.Message
import com.example.dualsimsms.util.MessageDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDeduplicatorTest {

    @Test
    fun exactSentCopiesCollapseAndDeliveredCopyWins() {
        val pending = message(1, type = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT, status = Telephony.Sms.STATUS_PENDING)
        val delivered = message(2, type = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT, status = Telephony.Sms.STATUS_COMPLETE)

        assertEquals(listOf(delivered), MessageDeduplicator.deduplicate(listOf(pending, delivered)))
    }

    @Test
    fun staleDraftCopiesCollapseToNewestDraftEvenWhenSimChanged() {
        val old = message(
            3,
            date = 100,
            body = "old",
            type = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT,
            subId = 1
        )
        val newest = message(
            4,
            date = 200,
            body = "new",
            type = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT,
            subId = 2
        )

        assertEquals(listOf(newest), MessageDeduplicator.deduplicate(listOf(old, newest)))
    }

    @Test
    fun providerCopiesWithFormattedAddressesCollapseWithinAThread() {
        val formatted = message(7, address = "077 536 1584", status = Telephony.Sms.STATUS_NONE)
        val normalized = message(8, address = "0775361584", status = Telephony.Sms.STATUS_COMPLETE)

        assertEquals(
            listOf(normalized),
            MessageDeduplicator.deduplicate(listOf(formatted, normalized))
        )
    }

    @Test
    fun inboxRowWinsOverExactFailedMirror() {
        val inbox = message(
            9,
            type = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX,
            status = Telephony.Sms.STATUS_NONE
        )
        val failedMirror = message(
            10,
            type = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED,
            status = Telephony.Sms.STATUS_FAILED
        )

        assertEquals(
            listOf(inbox),
            MessageDeduplicator.deduplicate(listOf(inbox, failedMirror))
        )
    }

    @Test
    fun separateSentMessagesRemainSeparate() {
        val first = message(5, date = 100)
        val second = message(6, date = 101)

        assertEquals(2, MessageDeduplicator.deduplicate(listOf(first, second)).size)
    }

    private fun message(
        id: Long,
        date: Long = 100,
        body: String = "same",
        type: Int = Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT,
        status: Int = Telephony.Sms.STATUS_PENDING,
        threadId: Long = 10,
        address: String = "+10000000000",
        subId: Int? = 1
    ) = Message(
        id = id,
        threadId = threadId,
        address = address,
        body = body,
        date = date,
        read = true,
        subId = subId,
        type = type,
        status = status
    )
}
