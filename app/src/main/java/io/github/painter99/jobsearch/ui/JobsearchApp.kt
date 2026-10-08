package io.github.painter99.jobsearch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.painter99.jobsearch.ui.dossier.DossierDetailRoute
import io.github.painter99.jobsearch.ui.offers.OffersRoute
import io.github.painter99.jobsearch.ui.settings.SettingsRoute

/**
 * State-based navigace (M1.6 D2 GO; M1.7 přidává třetí obrazovku Settings):
 * Seznam ↔ Dossier detail ↔ Settings. Žádná navigační knihovna — obrazovky
 * tři, back řeší stav (selectedOfferKey null = seznam; showSettings).
 */
@Composable
fun JobsearchApp() {
    var selectedOfferKey by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            selectedOfferKey != null -> DossierDetailRoute(
                offerKey = selectedOfferKey!!,
                onBack = { selectedOfferKey = null },
            )
            showSettings -> SettingsRoute(onBack = { showSettings = false })
            else -> OffersRoute(
                onOpenDossier = { offerKey -> selectedOfferKey = offerKey },
                onManageLocations = { /* vlna A: obrazovka lokalit (T2b) */ },
                onOpenSettings = { showSettings = true },
            )
        }
    }
}