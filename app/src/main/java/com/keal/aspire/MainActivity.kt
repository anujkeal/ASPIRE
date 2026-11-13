package com.keal.aspire

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.keal.aspire.ui.home.AspireHomeScreen
import com.keal.aspire.ui.library.AspireLibraryScreen
import com.keal.aspire.ui.nowplaying.AspireNowPlayingScreen
import com.keal.aspire.ui.splash.AspireSplashScreen
import com.keal.aspire.ui.theme.AspireTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            AspireTheme {
                var showHome by remember { mutableStateOf(false) }
                var showLibrary by remember { mutableStateOf(false) }
                var showNowPlaying by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(2600)
                    showHome = true
                }
                BackHandler(enabled = showNowPlaying) { showNowPlaying = false }
                BackHandler(enabled = showLibrary && !showNowPlaying) { showLibrary = false }
                when {
                    !showHome -> AspireSplashScreen()
                    showNowPlaying -> AspireNowPlayingScreen(onBack = { showNowPlaying = false })
                    showLibrary -> AspireLibraryScreen(
                        onBack = { showLibrary = false },
                        onOpenNowPlaying = { showNowPlaying = true }
                    )
                    else -> AspireHomeScreen(
                        onOpenLibrary = { showLibrary = true },
                        onOpenNowPlaying = { showNowPlaying = true }
                    )
                }
            }
        }
    }
}
