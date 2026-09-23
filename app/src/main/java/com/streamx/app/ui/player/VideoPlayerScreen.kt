package com.streamx.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.streamx.app.data.Episode
import com.streamx.app.viewmodel.StreamXViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    seriesId: String,
    seasonNumber: Int,
    episodeId: String,
    viewModel: StreamXViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val haptic = LocalHapticFeedback.current

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val allSeries by viewModel.allSeries.collectAsState()
    val currentSeries = remember(allSeries, seriesId) {
        allSeries.find { it.id == seriesId }
    }
    val currentSeason = remember(currentSeries, seasonNumber) {
        currentSeries?.seasons?.find { it.seasonNumber == seasonNumber }
    }
    val currentEpisode: Episode? = remember(currentSeason, episodeId) {
        currentSeason?.episodes?.find { it.id == episodeId }
            ?: currentSeason?.episodes?.firstOrNull()
    }

    // Force Landscape & Immersive Mode
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // ExoPlayer initialization
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // HUD gesture overlays
    var showVolumeHUD by remember { mutableStateOf(false) }
    var currentVolumePercent by remember {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(curVol.toFloat() / maxVol.toFloat())
    }

    var showBrightnessHUD by remember { mutableStateOf(false) }
    var currentBrightnessPercent by remember {
        val currentAttr = activity?.window?.attributes?.screenBrightness ?: 0.5f
        mutableFloatStateOf(if (currentAttr < 0f) 0.5f else currentAttr)
    }

    var seekIndicatorText by remember { mutableStateOf<String?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Load media URL into ExoPlayer
    LaunchedEffect(currentEpisode) {
        val videoUrl = currentEpisode?.videoUrl
        if (!videoUrl.isNullOrEmpty()) {
            val mediaItem = MediaItem.fromUri(videoUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Player state listener & completion handling
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    totalDuration = exoPlayer.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    currentEpisode?.let { ep ->
                        viewModel.markEpisodeAsCompleted(ep.id)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            // Save progress to Room DB on player exit
            if (currentSeries != null && currentEpisode != null) {
                viewModel.recordPlaybackProgress(
                    seriesId = currentSeries.id,
                    seriesTitle = currentSeries.title,
                    episode = currentEpisode,
                    seasonNumber = seasonNumber,
                    currentPositionMs = exoPlayer.currentPosition,
                    durationMs = exoPlayer.duration.coerceAtLeast(1L)
                )
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Progress ticker coroutine & auto-save to Room DB every 3 seconds
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            totalDuration = exoPlayer.duration.coerceAtLeast(0L)
            if (currentSeries != null && currentEpisode != null && totalDuration > 0) {
                viewModel.recordPlaybackProgress(
                    seriesId = currentSeries.id,
                    seriesTitle = currentSeries.title,
                    episode = currentEpisode,
                    seasonNumber = seasonNumber,
                    currentPositionMs = currentPosition,
                    durationMs = totalDuration
                )
            }
            delay(1000)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // PiP Mode Trigger
    val enterPiPMode = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                activity.enterPictureInPictureMode(params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    BackHandler {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val width = containerSize.width.toFloat()
                        when {
                            // Left side double tap -> Seek backward 10s
                            offset.x < width * 0.35f -> {
                                val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                seekIndicatorText = "-10s"
                            }
                            // Right side double tap -> Seek forward 10s
                            offset.x > width * 0.65f -> {
                                val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(target)
                                seekIndicatorText = "+10s"
                            }
                            // Center double tap -> Play/Pause
                            else -> {
                                if (exoPlayer.isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var startX = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val screenWidth = containerSize.width.toFloat()
                        val screenHeight = containerSize.height.toFloat()
                        val deltaPercent = -dragAmount / screenHeight

                        if (startX < screenWidth / 2f) {
                            // Left Side: Brightness control
                            val newBrightness = (currentBrightnessPercent + deltaPercent).coerceIn(0.05f, 1f)
                            currentBrightnessPercent = newBrightness
                            showBrightnessHUD = true
                            activity?.window?.attributes = activity?.window?.attributes?.apply {
                                screenBrightness = newBrightness
                            }
                        } else {
                            // Right Side: Volume control
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val newVolumePercent = (currentVolumePercent + deltaPercent).coerceIn(0f, 1f)
                            currentVolumePercent = newVolumePercent
                            showVolumeHUD = true
                            val targetIndex = (newVolumePercent * maxVol).toInt()
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
                        }
                    },
                    onDragEnd = {
                        showBrightnessHUD = false
                        showVolumeHUD = false
                    },
                    onDragCancel = {
                        showBrightnessHUD = false
                        showVolumeHUD = false
                    }
                )
            }
    ) {
        // Pure native Media3 ExoPlayer AndroidView without default controls
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false // STRICT: Hide ExoPlayer default controls
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.player = exoPlayer
            }
        )

        // Buffering spinner
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color(0xFF00E5FF),
                strokeWidth = 3.dp,
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.Center)
            )
        }

        // Quick Seek Indicator ("+10s" or "-10s")
        LaunchedEffect(seekIndicatorText) {
            if (seekIndicatorText != null) {
                delay(800)
                seekIndicatorText = null
            }
        }
        seekIndicatorText?.let { text ->
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (text.startsWith("+")) Icons.Filled.Forward10 else Icons.Filled.Replay10,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Gesture HUD for Brightness
        if (showBrightnessHUD) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrightnessHigh,
                        contentDescription = "Brightness",
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${(currentBrightnessPercent * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { currentBrightnessPercent },
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFFFD54F),
                        trackColor = Color.DarkGray
                    )
                }
            }
        }

        // Gesture HUD for Volume
        if (showVolumeHUD) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Volume",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${(currentVolumePercent * 100).toInt()}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { currentVolumePercent },
                        modifier = Modifier
                            .width(60.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF00E5FF),
                        trackColor = Color.DarkGray
                    )
                }
            }
        }

        // Custom StreamX Player Control Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Action Bar (Back, Title, PiP)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onBack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = currentSeries?.title ?: "StreamX Video",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "S$seasonNumber : E${currentEpisode?.episodeNumber ?: 1} - ${currentEpisode?.title ?: ""}",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Picture-in-Picture Button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                enterPiPMode()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Picture-in-Picture",
                                tint = Color(0xFF00E5FF)
                            )
                        }
                    }
                }

                // Center Play/Pause & Quick Seek Buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val target = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Replay10,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF00E5FF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color(0xFF0A0D14),
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val target = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                            exoPlayer.seekTo(target)
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Bottom Timeline & Duration Slider
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimestamp(currentPosition),
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatTimestamp(totalDuration),
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Slider(
                        value = if (totalDuration > 0) currentPosition.toFloat() / totalDuration.toFloat() else 0f,
                        onValueChange = { frac ->
                            if (totalDuration > 0) {
                                val target = (frac * totalDuration).toLong()
                                currentPosition = target
                                exoPlayer.seekTo(target)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
