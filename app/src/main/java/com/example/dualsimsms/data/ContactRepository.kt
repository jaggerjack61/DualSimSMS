package com.example.dualsimsms.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache

data class ContactSuggestion(
    val name: String,
    val number: String
)

/**
 * Resolves names and suggestions from ContactsContract. Read access is
 * optional at runtime; callers guard with READ_CONTACTS before use.
 */
class ContactRepository(context: Context) {

    private val resolver = context.contentResolver
    private val nameCache = LruCache<String, String?>(256)

    /** Display name for a phone number, or null when there is no contact. */
    fun nameFor(number: String): String? {
        if (number.isEmpty()) return null
        nameCache.get(number)?.let { return it }
        val name = queryName(number)
        // LruCache rejects null values, so only cache resolved names.
        name?.let { nameCache.put(number, it) }
        return name
    }

    /** Display name falling back to the raw number. */
    fun displayName(number: String): String = nameFor(number) ?: number

    fun suggest(query: String, limit: Int = 10): List<ContactSuggestion> {
        if (query.isBlank()) return emptyList()
        val suggestions = mutableListOf<ContactSuggestion>()
        val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")
        try {
            resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext() && suggestions.size < limit) {
                    val name = cursor.getString(0) ?: continue
                    val number = cursor.getString(1) ?: continue
                    if (name.isNotBlank() && number.isNotBlank()) {
                        suggestions += ContactSuggestion(name, number)
                    }
                }
            }
        } catch (_: SecurityException) {
            // READ_CONTACTS missing: numbers remain usable without suggestions.
        }
        return suggestions
    }

    private fun queryName(number: String): String? {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)?.takeIf { it.isNotBlank() }
            }
        } catch (_: SecurityException) {
            // READ_CONTACTS missing: fall back to the raw number.
        }
        return null
    }
}
