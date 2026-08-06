package com.example.dualsimsms

import android.provider.Telephony
import com.example.dualsimsms.data.Folder
import com.example.dualsimsms.model.Message
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderIsolationTest {

    @Test
    fun draftsAcceptOnlyDraftRows() {
        assertTrue(Folder.DRAFTS.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT)))
        assertFalse(Folder.DRAFTS.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT)))
        assertFalse(Folder.DRAFTS.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX)))
    }

    @Test
    fun sentAcceptsAllOutgoingStatesButNotDraftsOrInbox() {
        assertTrue(Folder.SENT.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT)))
        assertTrue(Folder.SENT.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX)))
        assertTrue(Folder.SENT.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED)))
        assertTrue(Folder.SENT.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED)))
        assertFalse(Folder.SENT.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT)))
        assertFalse(Folder.SENT.contains(message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX)))
    }

    private fun message(type: Int) = Message(
        id = 1,
        threadId = 1,
        address = "+10000000000",
        body = "body",
        date = 1,
        read = true,
        subId = 1,
        type = type
    )
}
