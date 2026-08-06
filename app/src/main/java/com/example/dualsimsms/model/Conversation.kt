package com.example.dualsimsms.model

data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val subId: Int?,
    val messageCount: Int,
    val contactName: String? = null
)
