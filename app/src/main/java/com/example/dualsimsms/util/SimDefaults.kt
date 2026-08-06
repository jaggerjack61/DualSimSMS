package com.example.dualsimsms.util

import android.graphics.Color

/**
 * Defaults for SIM presentation: slot-based names and colors, plus the
 * accessible preset color palette offered in settings.
 */
object SimDefaults {

    const val COLOR_SIM_1: Int = 0xFF1565C0.toInt()
    const val COLOR_SIM_2: Int = 0xFF2E7D32.toInt()
    const val COLOR_UNKNOWN: Int = 0xFF757575.toInt()

    val PALETTE: List<Int> = listOf(
        0xFF1565C0.toInt(),
        0xFF2E7D32.toInt(),
        0xFFC62828.toInt(),
        0xFF6A1B9A.toInt(),
        0xFFEF6C00.toInt(),
        0xFF00838F.toInt(),
        0xFFAD1457.toInt(),
        0xFF303F9F.toInt()
    )

    fun defaultName(slotIndex: Int): String = "SIM ${slotIndex + 1}"

    fun defaultColor(slotIndex: Int): Int = when (slotIndex) {
        0 -> COLOR_SIM_1
        1 -> COLOR_SIM_2
        else -> COLOR_UNKNOWN
    }

    /** Custom names must be non-empty after trimming; otherwise null. */
    fun normalizeName(customName: String?): String? =
        customName?.trim()?.takeIf { it.isNotEmpty() }
}
