package com.example.dualsimsms.util

import com.example.dualsimsms.model.Message

object SimFilter {

    /**
     * Filters messages by subscription id. A null filter keeps every message
     * (the "All SIMs" view). Messages without a known subscription id only
     * ever appear when the filter is null.
     */
    fun filter(messages: List<Message>, subId: Int?): List<Message> =
        if (subId == null) messages else messages.filter { it.subId != null && it.subId == subId }
}
