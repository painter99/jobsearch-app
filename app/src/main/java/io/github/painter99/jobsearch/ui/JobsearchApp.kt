package io.github.painter99.jobsearch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.painter99.jobsearch.ui.offers.OffersRoute

/**
 * State-based navigace mezi dvěma obrazovkami (M1.6, D2 GO):
 * Seznam ↔ Dossier detail. Žádná navigační knihovna — obrazovky dvě,
 * back řeší stav [selectedOfferKey] (null = seznam).
 */
@Composable
fun JobsearchApp() {
    var selectedOfferKey by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedOfferKey == null) {
            OffersRoute(
                onOpenDossier = { offerKey -> selectedOfferKey = offerKey },
                onManageLocations = { /* vlna A: obrazovka lokalit (T2b) */ },
            )
        } else {
            DossierDetailScreen(
                offerKey = selectedOfferKey!!,
                onBack = { selectedOfferKey = null },
            )
        }
    }
}

/**
 * Placeholder detailu dossieru (vlna B — naplní DossierViewModel).
 */
@Composable
fun DossierDetailScreen(
    offerKey: String,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onBack) {
            Text(text = "Dossier $offerKey — vlna B (zpět)")
        }
    }
}