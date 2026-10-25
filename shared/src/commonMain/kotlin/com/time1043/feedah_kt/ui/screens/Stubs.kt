package com.time1043.feedah_kt.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.time1043.feedah_kt.ui.navigation.Feed
import com.time1043.feedah_kt.ui.navigation.Review
import com.time1043.feedah_kt.ui.navigation.Search
import com.time1043.feedah_kt.ui.navigation.WordDetail

// Phase-1 placeholders so the navigation shell compiles; each is replaced by
// the real screen port in later commits.

@Composable
fun StatsScreen(nav: NavHostController) = Stub("Stats")

@Composable
fun SettingsScreen(nav: NavHostController) = Stub("Settings")

@Composable
fun FeedScreen(nav: NavHostController, route: Feed) = Stub("Feed ${route.bucket}")

@Composable
fun ReviewScreen(nav: NavHostController, route: Review) = Stub("Review")

@Composable
fun SearchScreen(nav: NavHostController, route: Search) = Stub("Search")

@Composable
fun WordDetailScreen(nav: NavHostController, route: WordDetail) = Stub("Word ${route.position}")

@Composable
private fun Stub(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label)
    }
}
