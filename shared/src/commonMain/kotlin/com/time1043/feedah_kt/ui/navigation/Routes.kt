package com.time1043.feedah_kt.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe stand-ins for the expo-router file routes. */
@Serializable data object Home

@Serializable data object Words

@Serializable data object Stats

@Serializable data object Settings

/** Full-screen card feed for a bucket. */
@Serializable data class Feed(val bucket: String)

/**
 * Review in three flavors: the current flag set (`flagged:<bucket>`), a
 * round's historical flag snapshot (`round:<bucket>:<round>`), or a day's
 * completed words across buckets (`day:<day>`).
 */
@Serializable data class Review(
    val bucket: String? = null,
    val round: Int? = null,
    val day: String? = null,
)

@Serializable data class Search(val pinnedBucket: String? = null)

/** Word browser page; zero-record (never advances the pointer). */
@Serializable data class WordDetail(
    val bucket: String,
    val position: Int,
    val fromSearch: Boolean = false,
)
