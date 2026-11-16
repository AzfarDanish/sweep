package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object PhotosRoute

@Serializable
data object TimelineRoute

@Serializable
data class MonthDetailRoute(
    val year: Int,
    val month: Int,
)

@Serializable
data object AlbumsRoute

@Serializable
data class AlbumDetailRoute(
    val albumId: String,
)

@Serializable
data object FilesRoute

@Serializable
data object TrashRoute

@Serializable
data class ViewerRoute(
    val initialMediaId: Long,
    val isTrashPreview: Boolean = false,
)
