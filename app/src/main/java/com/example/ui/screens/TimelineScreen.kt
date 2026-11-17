package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.R
import com.example.model.PermissionAccessState
import com.example.model.TimelineMonthGroup
import com.example.ui.components.HeaderTrashButton
import com.example.ui.components.PermissionGateView
import com.example.ui.components.rememberSweepImageLoader
import com.example.ui.state.MediaLibraryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    uiState: MediaLibraryUiState,
    onSelectYear: (Int) -> Unit,
    onOpenMonth: (year: Int, month: Int) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTrash: () -> Unit = {},
) {
    val imageLoader = rememberSweepImageLoader()
    val activeYearGroup = uiState.timelineYears.firstOrNull { it.year == uiState.selectedYear }
        ?: uiState.timelineYears.firstOrNull()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.nav_timeline),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    },
                    actions = {
                        HeaderTrashButton(
                            trashCount = uiState.trashedMedia.size,
                            onClick = onOpenTrash,
                            buttonTestTag = "timeline_trash_button",
                            badgeTestTag = "timeline_trash_badge",
                        )
                    },
                )
                if (uiState.timelineYears.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(
                            items = uiState.timelineYears,
                            key = { it.year },
                        ) { yearGroup ->
                            val selected = yearGroup.year == activeYearGroup?.year
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectYear(yearGroup.year) },
                                label = {
                                    Text(
                                        text = "${yearGroup.year}",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = null,
                                modifier = Modifier.testTag("timeline_year_${yearGroup.year}"),
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.permissionState == PermissionAccessState.DENIED ||
                    uiState.permissionState == PermissionAccessState.UNKNOWN -> {
                    PermissionGateView(
                        onGrantClick = onRequestPermission,
                        onOpenSettingsClick = onOpenSettings,
                    )
                }

                uiState.isLoading -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                }

                activeYearGroup == null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No dated media available",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("timeline_list"),
                    ) {
                        item(key = "year_summary_${activeYearGroup.year}") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "${activeYearGroup.year}",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    text = "${activeYearGroup.totalCount} items",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        items(
                            items = activeYearGroup.months,
                            key = { "${it.year}_${it.month}" },
                        ) { monthGroup ->
                            TimelineMonthCard(
                                monthGroup = monthGroup,
                                imageLoader = imageLoader,
                                onClick = { onOpenMonth(monthGroup.year, monthGroup.month) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineMonthCard(
    monthGroup: TimelineMonthGroup,
    imageLoader: coil.ImageLoader,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val previewItems = monthGroup.items.take(6)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(12.dp)
            .testTag("timeline_month_${monthGroup.year}_${monthGroup.month}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(
                    text = monthGroup.monthName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "${monthGroup.items.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Open ${monthGroup.monthName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        val rows = previewItems.chunked(3)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            rows.forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowItems.forEach { item ->
                        val request = ImageRequest.Builder(context)
                            .data(item.contentUri)
                            .size(320)
                            .apply { if (item.isVideo) videoFrameMillis(500L) }
                            .build()
                        AsyncImage(
                            model = request,
                            imageLoader = imageLoader,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
