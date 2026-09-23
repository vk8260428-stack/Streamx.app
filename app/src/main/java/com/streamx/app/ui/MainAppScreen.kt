package com.streamx.app.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.streamx.app.ui.screens.HomeScreen
import com.streamx.app.ui.screens.ProfileScreen
import com.streamx.app.viewmodel.StreamXViewModel

data class NavItem(
    val tab: BottomTab,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val label: String
)

@Composable
fun MainAppScreen(
    navController: NavHostController,
    viewModel: StreamXViewModel
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var currentTab by remember { mutableStateOf<BottomTab>(BottomTab.Home) }
    var lastBackPressTimestamp by remember { mutableLongStateOf(0L) }

    // Double tap back handler on Home tab, or navigate back to Home if in another tab
    BackHandler {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (currentTab != BottomTab.Home) {
            currentTab = BottomTab.Home
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTimestamp < 2000L) {
                (context as? Activity)?.finish()
            } else {
                lastBackPressTimestamp = currentTime
                Toast.makeText(context, "Press back again to exit StreamX", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val items = listOf(
        NavItem(BottomTab.Home, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
        NavItem(BottomTab.Search, Icons.Filled.Search, Icons.Outlined.Search, "Search"),
        NavItem(BottomTab.Downloads, Icons.Filled.Download, Icons.Outlined.Download, "Downloads"),
        NavItem(BottomTab.Me, Icons.Filled.Person, Icons.Outlined.Person, "Me")
    )

    Scaffold(
        containerColor = Color(0xFF0A0D14),
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF10141E),
                tonalElevation = 8.dp
            ) {
                items.forEach { item ->
                    val isSelected = currentTab == item.tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentTab = item.tab
                        },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF00E5FF),
                            selectedTextColor = Color(0xFF00E5FF),
                            indicatorColor = Color(0xFF1E293B),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0A0D14))
        ) {
            when (currentTab) {
                BottomTab.Home -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onSeriesClick = { seriesId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                        }
                    )
                }
                BottomTab.Search -> {
                    SearchTabScreen(
                        viewModel = viewModel,
                        onSeriesClick = { seriesId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate(Screen.SeriesDetail.createRoute(seriesId))
                        }
                    )
                }
                BottomTab.Downloads -> {
                    DownloadsTabScreen()
                }
                BottomTab.Me -> {
                    ProfileScreen(
                        viewModel = viewModel,
                        onPlayEpisode = { seriesId, seasonNum, episodeId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate(
                                Screen.VideoPlayer.createRoute(seriesId, seasonNum, episodeId)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchTabScreen(
    viewModel: StreamXViewModel,
    onSeriesClick: (String) -> Unit
) {
    HomeScreen(
        viewModel = viewModel,
        onSeriesClick = onSeriesClick,
        startWithSearchActive = true
    )
}

@Composable
fun DownloadsTabScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Downloads",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = Color(0xFF475569),
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "No Offline Downloads Yet",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = "Download episodes over Wi-Fi to watch on the go with zero data usage.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp, start = 32.dp, end = 32.dp),
                    lineHeight = 20.sp
                )
            }
        }
    }
}
