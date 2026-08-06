package com.example.dualsimsms.model

data class SimProfile(
    val subscriptionId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String?,
    val isActive: Boolean
)
