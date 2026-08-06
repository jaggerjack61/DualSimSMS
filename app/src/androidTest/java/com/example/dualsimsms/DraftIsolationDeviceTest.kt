package com.example.dualsimsms

import android.Manifest
import android.content.ContentValues
import android.provider.BaseColumns
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.dualsimsms.data.Folder
import com.example.dualsimsms.data.SmsRepository
import com.example.dualsimsms.data.SmsStore
import com.example.dualsimsms.util.SmsCapabilities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-provider regression test: draft writes must never mutate sent rows. */
@RunWith(AndroidJUnit4::class)
class DraftIsolationDeviceTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS
    )

    @Test
    fun draftFolderQueryNeverReturnsInboxOrSentRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drafts = SmsRepository(context).messagesFor(Folder.DRAFTS).first()

        assertTrue(
            "Draft query leaked non-draft types: ${drafts.map { it.type }}",
            drafts.all { it.type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_DRAFT }
        )
    }

    @Test
    fun updatingAndDeletingDraftLeavesSentMessageUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Test requires the target app to hold the SMS role", SmsCapabilities.isDefaultSmsHandler(context))

        val suffix = (System.currentTimeMillis() % 10_000_000L).toString().padStart(7, '0')
        val address = "+1555$suffix"
        val threadId = SmsStore.resolveThreadId(context, address)
        val sentBody = "sent-isolation-$suffix"
        val draftBody = "draft-isolation-$suffix"
        var sentId: Long? = null
        var draftId: Long? = null

        try {
            sentId = SmsStore.insert(
                context,
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.TextBasedSmsColumns.THREAD_ID, threadId)
                    put(Telephony.TextBasedSmsColumns.ADDRESS, address)
                    put(Telephony.TextBasedSmsColumns.BODY, sentBody)
                    put(Telephony.TextBasedSmsColumns.DATE, System.currentTimeMillis())
                    put(Telephony.TextBasedSmsColumns.READ, 1)
                    put(Telephony.TextBasedSmsColumns.TYPE, Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT)
                }
            )
            assertNotNull("Could not insert synthetic sent row", sentId)

            draftId = SmsStore.insertDraft(context, threadId, address, draftBody, null)
            assertNotNull("Could not insert synthetic draft row", draftId)
            assertTrue(SmsStore.updateDraft(context, draftId!!, "$draftBody-edited", null))

            val sentAfterUpdate = messageById(sentId!!)
            assertEquals(sentBody, sentAfterUpdate?.body)
            assertEquals(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT, sentAfterUpdate?.type)

            SmsStore.deleteDraft(context, threadId)
            val sentAfterDelete = messageById(sentId!!)
            assertEquals(sentBody, sentAfterDelete?.body)
            assertEquals(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT, sentAfterDelete?.type)
            assertTrue(messageById(draftId!!) == null)
        } finally {
            listOfNotNull(sentId, draftId).forEach { id ->
                SmsStore.delete(
                    context,
                    Telephony.Sms.CONTENT_URI,
                    "${BaseColumns._ID} = ?",
                    arrayOf(id.toString())
                )
            }
        }
    }

    private fun messageById(id: Long) = SmsStore.query(
        InstrumentationRegistry.getInstrumentation().targetContext,
        Telephony.Sms.CONTENT_URI,
        "${BaseColumns._ID} = ?",
        arrayOf(id.toString())
    ).singleOrNull()
}
