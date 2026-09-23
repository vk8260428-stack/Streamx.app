package com.streamx.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.streamx.app.ui.AppNavGraph
import com.streamx.app.viewmodel.StreamXViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: StreamXViewModel by viewModels()
    private var isPipModeActive: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            StreamXTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatically enter Picture-in-Picture when leaving app during video playback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                // Ignore if not currently in playback
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPipModeActive = isInPictureInPictureMode
    }
}

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF0A0D14),
    primaryContainer = Color(0xFF004D5A),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    background = Color(0xFF0A0D14),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF10141E),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8)
)

@Composable
fun StreamXTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
