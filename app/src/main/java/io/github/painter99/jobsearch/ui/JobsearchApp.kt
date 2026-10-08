package io.github.painter99.jobsearch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.painter99.jobsearch.ui.dossier.DossierDetailRoute
import io.github.painter99.jobsearch.ui.locations.LocationsRoute
import io.github.painter99.jobsearch.ui.offers.OffersRoute
import io.github.painter99.jobsearch.ui.offers.OffersTopBarActions
import io.github.painter99.jobsearch.ui.settings.SettingsRoute

/**
 * State-based navigace (M1.6 D2 GO; M1.7 přidává třetí obrazovku Settings;
 * M1.6c U3 zapojuje Lokality — dosud no-op placeholder z vlny A):
 * Seznam ↔ Dossier detail ↔ Lokality ↔ Settings. Žádná navigační knihovna —
 * obrazovky čtyři, back řeší stav.
 */
@Composable
fun JobsearchApp() {
    var selectedOfferKey by remember { mutableStateOf<String?>(null) }
    var showLocations by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    when {
        selectedOfferKey != null -> DossierDetailRoute(
            offerKey = selectedOfferKey!!,
            onBack = { selectedOfferKey = null },
        )
        showSettings -> SettingsRoute(onBack = { showSettings = false })
        showLocations -> LocationsRoute(onDone = { showLocations = false })
        else -> OffersRoute(
            onOpenDossier = { offerKey -> selectedOfferKey = offerKey },
            onManageLocations = { showLocations = true },
            onOpenSettings = { showSettings = true },
        )
    }
}