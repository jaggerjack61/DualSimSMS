package com.example.dualsimsms.model

import android.provider.Telephony

enum class MessageDeliveryState {
    NONE,
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val read: Boolean,
    val subId: Int?,
    val type: Int,
    val status: Int = Telephony.Sms.STATUS_NONE
) {
    val isIncoming: Boolean
        get() = type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX

    val deliveryState: MessageDeliveryState
        get() = when {
            isIncoming -> MessageDeliveryState.NONE
            type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_FAILED ||
                status == Telephony.Sms.STATUS_FAILED -> MessageDeliveryState.FAILED
            type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX ||
                type == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_QUEUED ->
                MessageDeliveryState.SENDING
            type != Telephony.TextBasedSmsColumns.MESSAGE_TYPE_SENT -> MessageDeliveryState.NONE
            status == Telephony.Sms.STATUS_COMPLETE -> MessageDeliveryState.DELIVERED
            else -> MessageDeliveryState.SENT
        }
}
