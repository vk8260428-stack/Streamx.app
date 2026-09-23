package com.streamx.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.streamx.app.data.Episode
import com.streamx.app.data.Season
import com.streamx.app.data.Series
import com.streamx.app.data.StreamCategory
import com.streamx.app.data.StreamXDatabase
import com.streamx.app.data.WatchedEpisodeEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StreamXViewModel(application: Application) : AndroidViewModel(application) {

    private val db = StreamXDatabase.getDatabase(application)
    private val watchHistoryDao = db.watchHistoryDao()
    private val firestore = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    // Series catalog from Firestore / curated fallback
    private val _allSeries = MutableStateFlow<List<Series>>(emptyList())
    val allSeries: StateFlow<List<Series>> = _allSeries.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Selected filter category
    private val _selectedCategory = MutableStateFlow(StreamCategory.ALL)
    val selectedCategory: StateFlow<StreamCategory> = _selectedCategory.asStateFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Watch history observed from Room DB
    val watchHistory: StateFlow<List<WatchedEpisodeEntity>> = watchHistoryDao.getAllWatchHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered Series based on Category & Search
    val filteredSeries: StateFlow<List<Series>> = combine(
        _allSeries,
        _selectedCategory,
        _searchQuery
    ) { seriesList, category, query ->
        seriesList.filter { series ->
            val matchesCategory = when (category) {
                StreamCategory.ALL -> true
                StreamCategory.TRENDING -> series.rating >= 8.5
                else -> series.category.equals(category.displayName, ignoreCase = true)
            }
            val matchesQuery = query.isBlank() || series.title.contains(query, ignoreCase = true) ||
                    series.description.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Latest releases sorted by uploadTimestamp descending
    val latestReleases: StateFlow<List<Series>> = _allSeries.combine(_allSeries) { list, _ ->
        list.sortedByDescending { it.uploadTimestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadSeriesCatalog()
    }

    fun selectCategory(category: StreamCategory) {
        _selectedCategory.value = category
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private fun loadSeriesCatalog() {
        _isLoading.value = true
        if (firestore != null) {
            firestore.collection("series")
                .orderBy("uploadTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || snapshot.isEmpty) {
                        // Use rich curated data if firestore is not seeded or returns empty
                        _allSeries.value = getCuratedStreamingCatalog()
                    } else {
                        val list = snapshot.toObjects(Series::class.java)
                        _allSeries.value = if (list.isNotEmpty()) list else getCuratedStreamingCatalog()
                    }
                    _isLoading.value = false
                }
        } else {
            _allSeries.value = getCuratedStreamingCatalog()
            _isLoading.value = false
        }
    }

    fun getSeriesById(seriesId: String): Series? {
        return _allSeries.value.find { it.id == seriesId }
    }

    // Room DB operations
    fun recordPlaybackProgress(
        seriesId: String,
        seriesTitle: String,
        episode: Episode,
        seasonNumber: Int,
        currentPositionMs: Long,
        durationMs: Long
    ) {
        viewModelScope.launch {
            val isCompleted = durationMs > 0 && (currentPositionMs.toFloat() / durationMs.toFloat()) >= 0.90f
            val entity = WatchedEpisodeEntity(
                episodeId = episode.id,
                seriesId = seriesId,
                seriesTitle = seriesTitle,
                episodeTitle = episode.title,
                seasonNumber = seasonNumber,
                episodeNumber = episode.episodeNumber,
                thumbnailUrl = episode.thumbnailUrl.ifEmpty { "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop" },
                lastPlaybackPositionMs = currentPositionMs,
                durationMs = durationMs,
                isCompleted = isCompleted,
                lastWatchedTimestamp = System.currentTimeMillis()
            )
            watchHistoryDao.saveWatchProgress(entity)
        }
    }

    fun markEpisodeAsCompleted(episodeId: String) {
        viewModelScope.launch {
            watchHistoryDao.markAsCompleted(episodeId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            watchHistoryDao.clearAllHistory()
        }
    }

    /**
     * Curated catalog with verified real test video streams (HLS/MP4) for out-of-the-box readiness.
     */
    private fun getCuratedStreamingCatalog(): List<Series> {
        val testVideos = listOf(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4"
        )

        return listOf(
            Series(
                id = "cyber-neon-2099",
                title = "Cyber Neon 2099",
                description = "In a neon-drenched metropolis ruled by neural conglomerates, an exiled cyber-runner races to decrypt an ancient protocol that holds the key to humanity's autonomy.",
                thumbnailUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&auto=format&fit=crop",
                backdropUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=1600&auto=format&fit=crop",
                category = "Sci-Fi",
                rating = 9.4,
                releaseYear = 2025,
                uploadTimestamp = System.currentTimeMillis() - 3600000L * 2,
                seasons = listOf(
                    Season(
                        seasonNumber = 1,
                        title = "Season 1: Protocol Zero",
                        episodes = (1..6).map { ep ->
                            Episode(
                                id = "cn2099-s1-e$ep",
                                episodeNumber = ep,
                                title = "Episode $ep: ${listOf("The Grid Awakening", "Silicon Shadows", "Neural Drift", "Sub-Level Echoes", "Binary Breach", "Singularity Dawn")[ep - 1]}",
                                description = "The rogue network triggers an unexpected signal through the city underworld.",
                                durationMinutes = 48 + ep,
                                videoUrl = testVideos[(ep - 1) % testVideos.size],
                                thumbnailUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=800&auto=format&fit=crop"
                            )
                        }
                    ),
                    Season(
                        seasonNumber = 2,
                        title = "Season 2: Dark Singularity",
                        episodes = (1..4).map { ep ->
                            Episode(
                                id = "cn2099-s2-e$ep",
                                episodeNumber = ep,
                                title = "Episode $ep: ${listOf("Quantum Veil", "Ghost in Orbit", "Fractured Lineage", "Nexus Collapse")[ep - 1]}",
                                description = "A new faction emerges from beyond the atmospheric perimeter.",
                                durationMinutes = 52,
                                videoUrl = testVideos[ep % testVideos.size],
                                thumbnailUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&auto=format&fit=crop"
                            )
                        }
                    )
                )
            ),
            Series(
                id = "shadow-cartel",
                title = "Shadow Cartel",
                description = "An undercover investigative tactician infiltrates the international syndicate controlling black-market deep sea lithium pipelines.",
                thumbnailUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=800&auto=format&fit=crop",
                backdropUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=1600&auto=format&fit=crop",
                category = "Action",
                rating = 8.9,
                releaseYear = 2024,
                uploadTimestamp = System.currentTimeMillis() - 3600000L * 14,
                seasons = listOf(
                    Season(
                        seasonNumber = 1,
                        title = "Season 1: Depth of Greed",
                        episodes = (1..5).map { ep ->
                            Episode(
                                id = "sc-s1-e$ep",
                                episodeNumber = ep,
                                title = "Episode $ep: ${listOf("Deep Harbor", "Cold Extraction", "Double Blind", "Point of No Return", "Vortex Strike")[ep - 1]}",
                                description = "Tensions erupt when an offshore intercept exposes a mole in high command.",
                                durationMinutes = 44 + ep,
                                videoUrl = testVideos[(ep + 1) % testVideos.size],
                                thumbnailUrl = "https://images.unsplash.com/photo-1509281373149-e957c6296406?w=800&auto=format&fit=crop"
                            )
                        }
                    )
                )
            ),
            Series(
                id = "solaris-odyssey",
                title = "Solaris Odyssey",
                description = "Humanity's first deep interstellar jump craft encounters a gravitational anomaly that reflects memories into physical matter.",
                thumbnailUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&auto=format&fit=crop",
                backdropUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=1600&auto=format&fit=crop",
                category = "Sci-Fi",
                rating = 9.1,
                releaseYear = 2024,
                uploadTimestamp = System.currentTimeMillis() - 3600000L * 30,
                seasons = listOf(
                    Season(
                        seasonNumber = 1,
                        title = "Season 1: Event Horizon",
                        episodes = (1..6).map { ep ->
                            Episode(
                                id = "so-s1-e$ep",
                                episodeNumber = ep,
                                title = "Episode $ep: ${listOf("Launch Window", "The Cosmic Rift", "Echoes of Terra", "Starlight Mirage", "Event Horizon", "Homecoming")[ep - 1]}",
                                description = "The crew wrestles with temporal illusions as life support reaches critical levels.",
                                durationMinutes = 55,
                                videoUrl = testVideos[(ep + 2) % testVideos.size],
                                thumbnailUrl = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?w=800&auto=format&fit=crop"
                            )
                        }
                    )
                )
            ),
            Series(
                id = "tokyo-requiem",
                title = "Tokyo Requiem",
                description = "A legendary katana artisan and a disgraced detective unite to dismantle an underworld conspiracy in the rainy alleys of Shinjuku.",
                thumbnailUrl = "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=800&auto=format&fit=crop",
                backdropUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=1600&auto=format&fit=crop",
                category = "Thriller",
                rating = 8.8,
                releaseYear = 2024,
                uploadTimestamp = System.currentTimeMillis() - 3600000L * 60,
                seasons = listOf(
                    Season(
                        seasonNumber = 1,
                        title = "Season 1",
                        episodes = (1..5).map { ep ->
                            Episode(
                                id = "tr-s1-e$ep",
                                episodeNumber = ep,
                                title = "Episode $ep: Blood and Steel",
                                description = "The trail leads to an abandoned subway hangar beneath the city.",
                                durationMinutes = 47,
                                videoUrl = testVideos[ep % testVideos.size],
                                thumbnailUrl = "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=800&auto=format&fit=crop"
                            )
                        }
                    )
                )
            ),
            Series(
                id = "apex-predators",
                title = "Apex: Planet Untamed",
                description = "Witness the ultimate survival strategies of nature's most formidable predators captured in ultra-high speed 8K cinematography.",
                thumbnailUrl = "https://images.unsplash.com/photo-1564349683136-77e08dba1ef6?w=800&auto=format&fit=crop",
                backdropUrl = "https://images.unsplash.com/photo-1534188753412-3e26d0d618d6?w=1600&auto=format&fit=crop",
                category = "Docuseries",
                rating = 9.6,
                releaseYear = 2025,
                uploadTimestamp = System.currentTimeMillis() - 3600000L * 5,
                seasons = listOf(
                    Season(
                        seasonNumber = 1,
                        title = "Season 1: The Wild Frontier",
                        episodes = (1..4).map { ep ->
                            Episode(
                                id = "apex-s1-e$ep",
                                episodeNumber = ep,
                                title = "Episode $ep: ${listOf("Arctic Ghosts", "Sahara Ambush", "Deep Ocean Hunters", "Taiga Reign")[ep - 1]}",
                                description = "A cinematic journey tracking solitary wolves through blinding blizzards.",
                                durationMinutes = 50,
                                videoUrl = testVideos[(ep + 3) % testVideos.size],
                                thumbnailUrl = "https://images.unsplash.com/photo-1564349683136-77e08dba1ef6?w=800&auto=format&fit=crop"
                            )
                        }
                    )
                )
            )
        )
    }
}
