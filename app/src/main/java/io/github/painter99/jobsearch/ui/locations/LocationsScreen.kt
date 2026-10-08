package io.github.painter99.jobsearch.ui.locations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.painter99.jobsearch.core.model.Municipality
import io.github.painter99.jobsearch.ui.theme.AppScreen

/**
 * Obrazovka výběru lokalit (M1.6 vlna A, T2 — realizace PRD Q4 dle D1 GO;
 * M1.6c U3: zapojena do navigace + AppScreen rámec):
 * autocomplete přes oficiální číselník obcí (6 258), multi-select obcí,
 * jeden aktivní profil (DataStore přes [io.github.painter99.jobsearch.data.storage.ProfileStore]).
 */
@Composable
fun LocationsRoute(
    onDone: () -> Unit,
    viewModel: LocationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    LocationsScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onToggle = viewModel::toggle,
        onSave = { viewModel.save(); onDone() },
        // Back šipka = „uložit a zpět" (výběr obcí je levný znovu udělat,
        // ztratit ho je nepříjemné — picker konvence).
        onBack = { viewModel.save(); onDone() },
    )
}

/** Stateless tělo obrazovky (testovatelné bez ViewModelu). */
@Composable
fun LocationsScreen(
    state: LocationsUiState,
    onQueryChange: (String) -> Unit,
    onToggle: (Municipality) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    AppScreen(title = "Lokality", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Vyberte obce, ve kterých hledáte práci. Nabídky s celorepublikovou platností se zobrazují vždy.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text(text = "Hledat obec…") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text(
                text = "Vybráno: ${state.selected.size}",
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                state.loading -> CircularProgressIndicator()
                state.loadError -> Text(
                    text = "Číselník obcí se nepodařilo načíst. Zkontrolujte připojení a zkuste to znovu.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.results, key = { it.id }) { municipality ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = municipality.id in state.selected,
                                onCheckedChange = { onToggle(municipality) },
                            )
                            Text(text = municipality.name)
                        }
                    }
                }
            }
            Button(onClick = onSave, enabled = !state.saving) {
                Text(text = "Uložit profil")
            }
        }
    }
}