package com.streamx.app.data

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Domain & Firestore models for StreamX video streaming application.
 */

@Keep
@IgnoreExtraProperties
data class Series(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val thumbnailUrl: String = "",
    val backdropUrl: String = "",
    val category: String = "Action",
    @PropertyName("uploadTimestamp")
    val uploadTimestamp: Long = System.currentTimeMillis(),
    val rating: Double = 8.5,
    val releaseYear: Int = 2024,
    val seasons: List<Season> = emptyList()
)

@Keep
@IgnoreExtraProperties
data class Season(
    val seasonNumber: Int = 1,
    val title: String = "Season 1",
    val episodes: List<Episode> = emptyList()
)

@Keep
@IgnoreExtraProperties
data class Episode(
    val id: String = "",
    val episodeNumber: Int = 1,
    val title: String = "",
    val description: String = "",
    val durationMinutes: Int = 45,
    val videoUrl: String = "",
    val thumbnailUrl: String = ""
)

/**
 * Filter Categories for the StreamX HomeScreen.
 */
enum class StreamCategory(val displayName: String) {
    ALL("All"),
    TRENDING("Trending"),
    ACTION("Action"),
    SCI_FI("Sci-Fi"),
    DRAMA("Drama"),
    THRILLER("Thriller"),
    ANIME("Anime"),
    DOCUMENTARY("Docuseries")
}
