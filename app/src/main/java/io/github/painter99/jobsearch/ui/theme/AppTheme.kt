package io.github.painter99.jobsearch.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Společný UI rámec (M1.6c U1, převzatý vzor z WSW MainActivity Round 2–3):
 * Scaffold + TopAppBar řeší statusBarsPadding (targetSdk 35 vynucuje
 * edge-to-edge — bez něj obsah teče pod hodiny) a jednotný vzhled
 * obrazovek. Obsah dostane správné [PaddingValues] ze Scaffoldu.
 */

/** TopAppBar s volitelným back šipkou (detaile/settings/lokality). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zpět",
                    )
                }
            }
        },
    )
}

/** Obálka obrazovky: Scaffold s [AppTopBar]; obsah si layout řeší sám. */
@Composable
fun AppScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title = title, onBack = onBack) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        content(padding)
    }
}