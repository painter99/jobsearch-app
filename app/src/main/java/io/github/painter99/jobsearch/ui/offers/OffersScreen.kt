package io.github.painter99.jobsearch.ui.offers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.ui.theme.OfferRow
import io.github.painter99.jobsearch.ui.theme.key

/**
 * Obrazovka Seznam (M1.6 vlna A): nabídky dle latky + lokalitního profilu.
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Nabídky (${state.offers.size})", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.TextButton(onClick = onManageLocations) {
                    Text(text = "Lokality")
                }
                androidx.compose.material3.TextButton(onClick = onOpenSettings) {
                    Text(text = "Nastavení")
                }
            }
        }
        Text(
            text = state.criteriaSummary,
            style = MaterialTheme.typography.bodySmall,
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
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/** R5: 187 MB jen na Wi-Fi s potvrzením — potvrzení dává tlačítko. */
@Composable
private fun BootstrapPrompt(downloading: Boolean, onDownload: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Databáze nabídek ještě není stažena.")
            Text(
                text = "První stažení = ~187 MB (celý stav volných míst ÚP). Stahujte na Wi-Fi. " +
                    "Další dny už jen malé denní přírůstky (~1 MB).",
                style = MaterialTheme.typography.bodySmall,
            )
            if (downloading) {
                CircularProgressIndicator()
                Text(text = "Stahuji…", style = MaterialTheme.typography.bodySmall)
            } else {
                androidx.compose.material3.Button(onClick = onDownload) {
                    Text(text = "Stáhnout databázi (187 MB)")
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Žádné nabídky nevyhovují latce ani lokalitám.")
        androidx.compose.material3.TextButton(onClick = onRetry) { Text(text = "Obnovit") }
    }
}