package io.github.painter99.jobsearch.ui.offers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.ui.theme.AppScreen
import io.github.painter99.jobsearch.ui.theme.OfferRow
import io.github.painter99.jobsearch.ui.theme.key

/**
 * Obrazovka Seznam (M1.6 vlna A; M1.6c U2/U3 redesign dle WSW vzorů):
 * TopAppBar s ikonami Lokality/Nastavení, ElevatedCard řádky nabídek.
 */
@Composable
fun OffersRoute(
    onOpenDossier: (String) -> Unit,
    onManageLocations: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: OffersViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    OffersScreen(
        state = state,
        onOpenDossier = { offer ->
            viewModel.markSeen(offer.key())
            onOpenDossier(offer.key())
        },
        onManageLocations = onManageLocations,
        onOpenSettings = onOpenSettings,
        onSync = viewModel::syncDaily,
        onDownloadBootstrap = viewModel::downloadBootstrap,
        onRetry = viewModel::refresh,
    )
}

/**
 * Stateless tělo obrazovky (testovatelné bez ViewModelu).
 * U3: Lokality i Nastavení jsou reálné obrazovky (ikony v top baru).
 */
@Composable
fun OffersScreen(
    state: OffersUiState,
    onOpenDossier: (JobOffer) -> Unit,
    onManageLocations: () -> Unit,
    onOpenSettings: () -> Unit,
    onSync: () -> Unit,
    onDownloadBootstrap: () -> Unit,
    onRetry: () -> Unit,
) {
    AppScreen(title = "Nabídky (${state.offers.size})") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = state.criteriaSummary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            state.syncMessage?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            when {
                state.loading -> CircularProgressIndicator()
                state.bootstrapNeeded -> BootstrapPrompt(
                    downloading = state.downloading,
                    onDownload = onDownloadBootstrap,
                )
                state.offers.isEmpty() -> EmptyState(onRetry = onRetry)
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.offers, key = { it.key() }) { offer ->
                        OfferRow(
                            offer = offer,
                            seen = offer.key() in state.seenKeys,
                            onClick = { onOpenDossier(offer) },
                        )
                    }
                }
            }
        }
    }
}

/** R5: 187 MB jen na Wi-Fi s potvrzením — potvrzení dává tlačítko. */
@Composable
private fun BootstrapPrompt(downloading: Boolean, onDownload: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
            Text(text = "Databáze nabídek ještě není stažena.", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "První stažení = ~187 MB (celý stav volných míst ÚP). Stahujte na Wi-Fi. " +
                    "Další dny už jen malé denní přírůstky (~1 MB).",
                style = MaterialTheme.typography.bodySmall,
            )
            if (downloading) {
                CircularProgressIndicator()
                Text(text = "Stahuji…", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(onClick = onDownload) {
                    Text(text = "Stáhnout databázi (187 MB)")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Žádné nabídky nevyhovují latce ani lokalitám.")
        TextButton(onClick = onRetry) { Text(text = "Obnovit") }
    }
}

/** Akce top baru (U3): Lokality (📍) a Nastavení (⚙). */
@Composable
fun OffersTopBarActions(onManageLocations: () -> Unit, onOpenSettings: () -> Unit) {
    IconButton(onClick = onManageLocations) {
        Icon(imageVector = Icons.Filled.Place, contentDescription = "Lokality")
    }
    IconButton(onClick = onOpenSettings) {
        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Nastavení")
    }
}