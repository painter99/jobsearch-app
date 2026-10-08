package io.github.painter99.jobsearch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.painter99.jobsearch.ui.JobsearchApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // M1.6c U1 (WSW Round 5 vzor): targetSdk 35 vynucuje
                // edge-to-edge — na světlém pozadí musí systém malovat
                // TMAVÉ ikony stavové lišty, jinak jsou neviditelné.
                // isSystemInDarkTheme() je @Composable — hodnotí se tady,
                // do SideEffectu jde jen hotová hodnota (lekce CI #42).
                val darkTheme = isSystemInDarkTheme()
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        WindowCompat
                            .getInsetsController(window, view)
                            .isAppearanceLightStatusBars = !darkTheme
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    JobsearchApp()
                }
            }
        }
    }
}