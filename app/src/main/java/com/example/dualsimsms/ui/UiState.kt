package com.example.dualsimsms.ui

sealed interface UiState<out T> {
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data object Error : UiState<Nothing>
}

data class TabModel(
    val subId: Int?,
    val label: String,
    /** SIM colour for the tab's dot; null for "All SIMs". */
    val color: Int? = null
)
