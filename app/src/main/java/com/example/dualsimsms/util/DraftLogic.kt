package com.example.dualsimsms.util

/**
 * Pure decision logic for auto-saving message drafts.
 */
object DraftLogic {

    const val DEBOUNCE_MILLIS = 600L

    /** A draft should be saved when the body is non-blank and changed. */
    fun shouldSave(body: String, lastSaved: String?): Boolean =
        body.isNotBlank() && body != lastSaved

    /** An emptied body should remove the stored draft. */
    fun shouldDelete(body: String): Boolean = body.isBlank()
}
