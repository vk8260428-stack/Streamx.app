package com.streamx.app.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.streamx.app.data.Episode
import com.streamx.app.ui.Screen
import com.streamx.app.viewmodel.StreamXViewModel

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    viewModel: StreamXViewModel,
    navController: NavHostController
) {
    val haptic = LocalHapticFeedback.current
    val series = viewModel.getSeriesById(seriesId)
    val watchHistory by viewModel.watchHistory.collectAsState()

    // Map of watched episode IDs for rapid lookup
    val watchedEpisodeMap = remember(watchHistory) {
        watchHistory.associateBy { it.episodeId }
    }

    if (series == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0D14)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Series Not Found", color = Color.White, fontSize = 16.sp)
        }
        return
    }

    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    val currentSeason = series.seasons.getOrNull(selectedSeasonIndex) ?: series.seasons.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D14))
    ) {
        // Hero Backdrop with back button & gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        ) {
            AsyncImage(
                model = series.backdropUrl.ifEmpty { series.thumbnailUrl },
                contentDescription = series.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Scrim gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color(0xFF0A0D14)
                            )
                        )
                    )
            )

            // Back button
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController.popBackStack()
                },
                modifier = Modifier
                    .padding(top = 16.dp, start = 12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
        }

        // Details content container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = series.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Metadata row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = series.category,
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${series.rating}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "${series.releaseYear}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "${series.seasons.size} Season${if (series.seasons.size > 1) "s" else ""}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = series.description,
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Seasons Tabs
            if (series.seasons.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedSeasonIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF00E5FF),
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedSeasonIndex]),
                            color = Color(0xFF00E5FF),
                            height = 3.dp
                        )
                    }
                ) {
                    series.seasons.forEachIndexed { index, season ->
                        val isSelected = selectedSeasonIndex == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedSeasonIndex = index
                            },
                            text = {
                                Text(
                                    text = "Season ${season.seasonNumber}",
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Episode Grid: Square Boxes "E1", "E2" etc. with Room DB Watched Tinting
        if (currentSeason != null && currentSeason.episodes.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentSeason.episodes, key = { it.id }) { episode ->
                    val watchRecord = watchedEpisodeMap[episode.id]
                    val isWatched = watchRecord?.isCompleted == true || (watchRecord?.lastPlaybackPositionMs ?: 0L) > 5000L

                    EpisodeSquareBox(
                        episode = episode,
                        isWatched = isWatched,
                        isCompleted = watchRecord?.isCompleted == true,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate(
                                Screen.VideoPlayer.createRoute(
                                    seriesId = series.id,
                                    seasonNumber = currentSeason.seasonNumber,
                                    episodeId = episode.id
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeSquareBox(
    episode: Episode,
    isWatched: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    // Check Room DB to tint watched episodes
    val backgroundColor = when {
        isCompleted -> Color(0xFF0F3A47) // Tinted cyan for completed
        isWatched -> Color(0xFF1E293B)   // Tinted slate for partially watched
        else -> Color(0xFF131826)        // Default dark
    }

    val borderColor = when {
        isCompleted -> Color(0xFF00E5FF)
        isWatched -> Color(0xFF38BDF8)
        else -> Color(0xFF222B40)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "E${episode.episodeNumber}",
                color = if (isWatched) Color(0xFF00E5FF) else Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "${episode.durationMinutes}m",
                color = if (isWatched) Color(0xFF94A3B8) else Color(0xFF64748B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Watched or Completed Checkmark Badge in corner
        if (isCompleted) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Watched",
                    tint = Color.Black,
                    modifier = Modifier.size(11.dp)
                )
            }
        } else if (isWatched) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .background(Color(0xFF38BDF8), CircleShape)
            )
        }
    }
}
