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
        get() = if (dateTakenMillis > 0L) dateTakenMillis else dateModifiedMillis
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
