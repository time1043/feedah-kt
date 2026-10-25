package com.time1043.feedah_kt

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.time1043.feedah_kt.data.db.DriverFactory
import com.time1043.feedah_kt.data.db.UsageTracker
import com.time1043.feedah_kt.platform.TtsPlayer
import com.time1043.feedah_kt.ui.components.BookIcon
import com.time1043.feedah_kt.ui.components.ChartIcon
import com.time1043.feedah_kt.ui.components.GearIcon
import com.time1043.feedah_kt.ui.components.HomeIcon
import com.time1043.feedah_kt.ui.navigation.Feed
import com.time1043.feedah_kt.ui.navigation.Home
import com.time1043.feedah_kt.ui.navigation.Review
import com.time1043.feedah_kt.ui.navigation.Search
import com.time1043.feedah_kt.ui.navigation.Settings
import com.time1043.feedah_kt.ui.navigation.Stats
import com.time1043.feedah_kt.ui.navigation.WordDetail
import com.time1043.feedah_kt.ui.navigation.Words
import com.time1043.feedah_kt.ui.screens.FeedScreen
import com.time1043.feedah_kt.ui.screens.HomeScreen
import com.time1043.feedah_kt.ui.screens.ReviewScreen
import com.time1043.feedah_kt.ui.screens.SearchScreen
import com.time1043.feedah_kt.ui.screens.SettingsScreen
import com.time1043.feedah_kt.ui.screens.StatsScreen
import com.time1043.feedah_kt.ui.screens.WordDetailScreen
import com.time1043.feedah_kt.ui.screens.WordsScreen
import com.time1043.feedah_kt.ui.theme.DarkColors
import com.time1043.feedah_kt.ui.theme.FeedahTheme
import com.time1043.feedah_kt.ui.theme.LightColors
import com.time1043.feedah_kt.ui.theme.LocalFeedahColors
import com.time1043.feedah_kt.ui.theme.LocalScheme
import com.time1043.feedah_kt.ui.theme.Scheme
import com.time1043.feedah_kt.ui.theme.StatusBarEffect
import com.time1043.feedah_kt.ui.theme.withAlpha

/** Root composable: init gate, then themed navigation shell. */
@Composable
fun App(driverFactory: DriverFactory, tts: TtsPlayer) {
    var ready by remember { mutableStateOf(AppGraph.ready) }

    LaunchedEffect(driverFactory) {
        AppGraph.init(driverFactory, tts)
        UsageTracker.startAppUsage(AppGraph.appScope)
        ready = true
    }

    if (!ready) {
        // Splash: settings aren't loaded yet, so follow the system scheme.
        Box(
            Modifier
                .fillMaxSize()
                .background(if (isSystemInDarkTheme()) DarkColors.background else LightColors.background),
        )
        return
    }

    val settings by AppGraph.settings!!.settings.collectAsStateWithLifecycle()
    FeedahTheme(mode = settings.theme) {
        StatusBarEffect(darkIcons = LocalScheme.current == Scheme.light)
        RootNavigator()
    }
}

@Composable
private fun RootNavigator() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val onTabRoute = destination?.hierarchy?.any { it.isTabRoute() } == true

    Scaffold(
        containerColor = LocalFeedahColors.current.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { if (onTabRoute) FeedahBottomBar(navController, destination) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable<Home> { HomeScreen(navController) }
            composable<Words> { WordsScreen(navController) }
            composable<Stats> { StatsScreen(navController) }
            composable<Settings> { SettingsScreen(navController) }
            composable<Feed> { entry -> FeedScreen(navController, entry.toRoute<Feed>()) }
            composable<Review> { entry -> ReviewScreen(navController, entry.toRoute<Review>()) }
            composable<Search> { entry -> SearchScreen(navController, entry.toRoute<Search>()) }
            composable<WordDetail> { entry -> WordDetailScreen(navController, entry.toRoute<WordDetail>()) }
        }
    }
}

private fun NavDestination.isTabRoute(): Boolean =
    hasRoute(Home::class) || hasRoute(Words::class) || hasRoute(Stats::class) || hasRoute(Settings::class)

private data class TabSpec(
    val route: Any,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun FeedahBottomBar(navController: NavHostController, destination: NavDestination?) {
    val colors = LocalFeedahColors.current
    val tabs = listOf(
        TabSpec(Home, "Home", HomeIcon),
        TabSpec(Words, "Words", BookIcon),
        TabSpec(Stats, "Stats", ChartIcon),
        TabSpec(Settings, "Settings", GearIcon),
    )
    Column {
        HorizontalDivider(thickness = 0.5.dp, color = colors.separator)
        NavigationBar(containerColor = colors.background, tonalElevation = 0.dp) {
            for (tab in tabs) {
                NavigationBarItem(
                    selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                    onClick = {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accent,
                        selectedTextColor = colors.accent,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary,
                        indicatorColor = Color.Transparent,
                    ),
                )
            }
        }
    }
}
