package com.streamx.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.streamx.app.ui.player.VideoPlayerScreen
import com.streamx.app.ui.screens.SeriesDetailScreen
import com.streamx.app.viewmodel.StreamXViewModel

sealed class Screen(val route: String) {
    object Main : Screen("main_host")
    object SeriesDetail : Screen("series_detail/{seriesId}") {
        fun createRoute(seriesId: String) = "series_detail/$seriesId"
    }
    object VideoPlayer : Screen("player/{seriesId}/{seasonNumber}/{episodeId}") {
        fun createRoute(seriesId: String, seasonNumber: Int, episodeId: String) =
            "player/$seriesId/$seasonNumber/$episodeId"
    }
}

sealed class BottomTab(val route: String, val title: String) {
    object Home : BottomTab("tab_home", "Home")
    object Search : BottomTab("tab_search", "Search")
    object Downloads : BottomTab("tab_downloads", "Downloads")
    object Me : BottomTab("tab_me", "Me")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: StreamXViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        // Main tabs host
        composable(Screen.Main.route) {
            MainAppScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        // Series details with seasons & episode square boxes
        composable(
            route = Screen.SeriesDetail.route,
            arguments = listOf(
                navArgument("seriesId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            SeriesDetailScreen(
                seriesId = seriesId,
                viewModel = viewModel,
                navController = navController
            )
        }

        // Dedicated Media3 Video Player with gestures and PiP
        composable(
            route = Screen.VideoPlayer.route,
            arguments = listOf(
                navArgument("seriesId") { type = NavType.StringType },
                navArgument("seasonNumber") { type = NavType.IntType },
                navArgument("episodeId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
            val seasonNumber = backStackEntry.arguments?.getInt("seasonNumber") ?: 1
            val episodeId = backStackEntry.arguments?.getString("episodeId") ?: ""

            VideoPlayerScreen(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeId = episodeId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
