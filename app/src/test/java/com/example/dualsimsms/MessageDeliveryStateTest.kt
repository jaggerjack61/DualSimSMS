package com.example.dualsimsms

import android.provider.Telephony
import com.example.dualsimsms.model.Message
import com.example.dualsimsms.model.MessageDeliveryState
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDeliveryStateTest {

    @Test
    fun outgoingStatusMapsToChecksAndFailure() {
        assertEquals(
            MessageDeliveryState.SENT,
            message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_PENDING).deliveryState
        )
        assertEquals(
            MessageDeliveryState.DELIVERED,
            message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT, Telephony.Sms.STATUS_COMPLETE).deliveryState
        )
        assertEquals(
            MessageDeliveryState.FAILED,
            message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED, Telephony.Sms.STATUS_FAILED).deliveryState
        )
    }

    @Test
    fun incomingMessagesNeverShowDeliveryState() {
        assertEquals(
            MessageDeliveryState.NONE,
            message(Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX, Telephony.Sms.STATUS_COMPLETE).deliveryState
        )
    }

    private fun message(type: Int, status: Int) = Message(
        id = 1,
        threadId = 1,
        address = "+10000000000",
        body = "body",
        date = 1,
        read = true,
        subId = 1,
        type = type,
        status = status
    )
}
