package com.example.model

import android.net.Uri

enum class MediaFilter {
    ALL,
    PHOTOS,
    VIDEOS,
}

enum class PermissionAccessState {
    UNKNOWN,
    FULL,
    PARTIAL,
    DENIED,
}

data class MediaItem(
    val id: Long,
    val contentUri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateTakenMillis: Long,
    val dateModifiedMillis: Long,
    val sizeBytes: Long,
    val durationMillis: Long,
    val width: Int,
    val height: Int,
    val bucketId: Long,
    val bucketName: String,
    val relativePath: String,
    val isVideo: Boolean,
    val isTrashed: Boolean = false,
    val isOsTrashed: Boolean = false,
) {
    val effectiveTimestampMillis: Long
        get() = when {
            dateTakenMillis > 0L -> dateTakenMillis
            dateModifiedMillis > 0L -> dateModifiedMillis
            else -> 0L
        }
}

data class MediaDateGroup(
    val key: String,
    val label: String,
    val items: List<MediaItem>,
)

data class TimelineMonthGroup(
    val year: Int,
    val month: Int, // 1..12
    val monthName: String,
    val items: List<MediaItem>,
)

data class TimelineYearGroup(
    val year: Int,
    val totalCount: Int,
    val months: List<TimelineMonthGroup>,
)

enum class AlbumKind {
    LOGICAL,
    FOLDER,
}

data class AlbumCollection(
    val id: String,
    val name: String,
    val kind: AlbumKind,
    val coverUri: Uri,
    val coverIsVideo: Boolean,
    val count: Int,
    val subtitle: String,
    val items: List<MediaItem>,
)

enum class FileCategory(val label: String) {
    IMAGES("Images"),
    VIDEOS("Videos"),
    AUDIO("Audio"),
    DOCUMENTS("Documents"),
    DOWNLOADS("Downloads"),
}

enum class FileSortOption(val label: String) {
    NAME("Name"),
    DATE("Date"),
    SIZE("Size"),
}

enum class SortOrder {
    ASCENDING,
    DESCENDING,
}

enum class AlbumSortOption {
    NEWEST_FIRST,
    OLDEST_FIRST,
    LARGEST_FIRST,
    SMALLEST_FIRST,
}

fun sortAlbumMedia(items: List<MediaItem>, sortOption: AlbumSortOption): List<MediaItem> {
    if (items.size <= 1) return items
    val comparator = when (sortOption) {
        AlbumSortOption.OLDEST_FIRST ->
            compareBy<MediaItem> { it.effectiveTimestampMillis }.thenBy { it.id }
        AlbumSortOption.NEWEST_FIRST ->
            compareByDescending<MediaItem> { it.effectiveTimestampMillis }.thenByDescending { it.id }
        AlbumSortOption.LARGEST_FIRST ->
            compareByDescending<MediaItem> { it.sizeBytes.coerceAtLeast(0L) }
                .thenBy { it.effectiveTimestampMillis }
                .thenBy { it.id }
        AlbumSortOption.SMALLEST_FIRST ->
            compareBy<MediaItem> { it.sizeBytes.coerceAtLeast(0L) }
                .thenBy { it.effectiveTimestampMillis }
                .thenBy { it.id }
    }
    return items.sortedWith(comparator)
}

data class FileEntry(
    val id: String,
    val uri: Uri,
    val mediaStoreId: Long?,
    val name: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val mimeType: String,
    val isDirectory: Boolean,
    val isMediaStoreBacked: Boolean,
    val isImage: Boolean,
    val isVideo: Boolean,
    val durationMillis: Long = 0L,
    val relativePath: String = "",
)

sealed interface TrashAction {
    val uris: List<Uri>

    data class MoveToTrash(override val uris: List<Uri>) : TrashAction
    data class RestoreFromTrash(override val uris: List<Uri>) : TrashAction
}
