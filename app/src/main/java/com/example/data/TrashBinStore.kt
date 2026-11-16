package com.example.data

import android.content.Context
import android.net.Uri

/**
 * Tracks URIs moved into Sweep's Trash Bin without introducing a database.
 * Persists only the set of trashed URI strings in SharedPreferences so trashed
 * state stays tracked across app restarts while undo/redo remains memory-only.
 */
class TrashBinStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun getTrashedUriStrings(): Set<String> {
        return prefs.getStringSet(KEY_TRASHED_URIS, emptySet())?.toSet() ?: emptySet()
    }

    @Synchronized
    fun getTrashedUris(): Set<Uri> {
        return getTrashedUriStrings().mapNotNull { raw ->
            runCatching { Uri.parse(raw) }.getOrNull()
        }.toSet()
    }

    @Synchronized
    fun addToTrash(uris: Collection<Uri>): List<Uri> {
        if (uris.isEmpty()) return emptyList()
        val current = getTrashedUriStrings().toMutableSet()
        val newlyAdded = mutableListOf<Uri>()
        for (uri in uris) {
            val raw = uri.toString()
            if (current.add(raw)) {
                newlyAdded.add(uri)
            }
        }
        if (newlyAdded.isNotEmpty()) {
            prefs.edit().putStringSet(KEY_TRASHED_URIS, current).apply()
        }
        return newlyAdded
    }

    @Synchronized
    fun removeFromTrash(uris: Collection<Uri>): List<Uri> {
        if (uris.isEmpty()) return emptyList()
        val current = getTrashedUriStrings().toMutableSet()
        val removed = mutableListOf<Uri>()
        for (uri in uris) {
            if (current.remove(uri.toString())) {
                removed.add(uri)
            }
        }
        if (removed.isNotEmpty()) {
            prefs.edit().putStringSet(KEY_TRASHED_URIS, current).apply()
        }
        return removed
    }

    @Synchronized
    fun pruneMissingUris(existingNonOsTrashedUris: Set<Uri>) {
        val current = getTrashedUriStrings()
        if (current.isEmpty()) return
        val existingStrings = existingNonOsTrashedUris.map { it.toString() }.toSet()
        val retained = current.filter { it in existingStrings }.toSet()
        if (retained.size != current.size) {
            prefs.edit().putStringSet(KEY_TRASHED_URIS, retained).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "sweep_trash_bin"
        private const val KEY_TRASHED_URIS = "trashed_uris"
    }
}
