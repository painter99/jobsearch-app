package io.github.painter99.jobsearch.ui.dossier

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.painter99.jobsearch.core.model.DossierVerdict
import io.github.painter99.jobsearch.pipeline.AgencyStatus
import io.github.painter99.jobsearch.pipeline.ResolverCandidate
import io.github.painter99.jobsearch.ui.theme.shiftLabel

/**
 * Detail dossieru (US4, M1.6 vlna B): hlavička nabídky s deep linky,
 * checklist latky (auto-fill), poznámky, verdikt; agenturní sekce
 * (status + kandidáti resolveru) jen u agenturních nabídek (N7).
 */
@Composable
fun DossierDetailRoute(
    offerKey: String,
    onBack: () -> Unit,
    viewModel: DossierViewModel = viewModel(),
) {
    LaunchedEffect(offerKey) { viewModel.load(offerKey) }
    val state by viewModel.state.collectAsState()
    DossierDetailScreen(
        offerKey = offerKey,
        state = state,
        onBack = onBack,
        onVerdict = viewModel::setVerdict,
        onNotes = viewModel::setNotes,
        onToggleChecklist = viewModel::toggleChecklist,
    )
}

@Composable
fun DossierDetailScreen(
    offerKey: String,
    state: DossierUiState,
    onBack: () -> Unit,
    onVerdict: (DossierVerdict) -> Unit,
    onNotes: (String) -> Unit,
    onToggleChecklist: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text(text = "← Zpět na seznam") }
        when {
            state.loading -> CircularProgressIndicator()
            state.loadError -> Text(text = "Nabídka nenalezena v lokální databázi ($offerKey).")
            else -> {
                val offer = state.offer
                if (offer == null) {
                    Text(text = "Nabídka nenalezena.")
                } else {
                    OfferHeader(state = state)
                    ChecklistSection(state = state, onToggle = onToggleChecklist)
                    NotesSection(notes = state.notes, onNotes = onNotes)
                    VerdictSection(verdict = state.verdict, onVerdict = onVerdict)
                    AgencySection(state = state)
                }
            }
        }
    }
}

/** Hlavička: profese, zaměstnavatel, mzda, směna + deep linky (D5 fallback). */
@Composable
private fun OfferHeader(state: DossierUiState) {
    val context = LocalContext.current
    val offer = state.offer ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = offer.profession, style = MaterialTheme.typography.titleLarge)
            offer.employer?.let {
                Text(text = it.name, style = MaterialTheme.typography.bodyMedium)
                it.ico?.let { ico -> Text(text = "IČO: $ico", style = MaterialTheme.typography.bodySmall) }
            }
            offer.salaryFrom?.let { from ->
                Text(
                    text = "$from Kč/měs" + (offer.salaryTo?.let { " – $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            offer.shiftPattern?.let {
                Text(text = shiftLabel(it), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "Referenční číslo: ${offer.referenceNumber}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val url = offer.url
                if (!url.isNullOrBlank()) {
                    Button(onClick = { context.openUrl(url) }) { Text(text = "Otevřít inzerát") }
                } else {
                    // D5 fallback: urlAdresa většinou null → vyhledávání ÚP + referenční číslo
                    Button(onClick = { context.openUrl(UP_SEARCH_URL) }) { Text(text = "Vyhledat na ÚP") }
                }
            }
        }
    }
}

/** Checklist latky: auto položky předvyplněny, vše přepisovatelné uživatelem. */
@Composable
private fun ChecklistSection(state: DossierUiState, onToggle: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = "Checklist latky", style = MaterialTheme.typography.titleMedium)
            state.checklist.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = row.checked, onCheckedChange = { onToggle(row.item.key) })
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = row.item.label, style = MaterialTheme.typography.bodyMedium)
                        if (row.autoFilled && !row.checked) {
                            Text(
                                text = "Data naznačují splněno — odškrtni, pokud ne",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Poznámky uživatele (Room, okamžitý zápis). */
@Composable
private fun NotesSection(notes: String, onNotes: (String) -> Unit) {
    var text by remember(notes) { mutableStateOf(notes) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Poznámky", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                placeholder = { Text(text = "Co jsi zjistil(a) — telefonát, recenze, dojezd…") },
            )
            TextButton(onClick = { onNotes(text) }) { Text(text = "Uložit poznámky") }
        }
    }
}

/** Verdikt: OPEN/GO/PODMÍNĚNĚ/VYŘAZENO jako chips (US4). */
@Composable
private fun VerdictSection(verdict: DossierVerdict, onVerdict: (DossierVerdict) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Verdikt", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DossierVerdict.OPEN to "otevřeno",
                    DossierVerdict.GO to "GO",
                    DossierVerdict.CONDITIONAL to "PODMÍNĚNĚ",
                    DossierVerdict.REJECTED to "VYŘAZENO",
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = verdict == value,
                        onClick = { onVerdict(value) },
                        label = { Text(text = label) },
                    )
                }
            }
        }
    }
}

/**
 * Agenturní sekce: status vždy (US1), kandidáti resolveru jen u agentur
 * (US2, N7 — „appka navrhuje, potvrzuje uživatel").
 */
@Composable
private fun AgencySection(state: DossierUiState) {
    val status = state.agencyStatus ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Agentura", style = MaterialTheme.typography.titleMedium)
            when (status) {
                AgencyStatus.AGENCY_MPSV -> Text(
                    text = "⚠️ Personální agentura (oficiální MPSV seznam)" +
                        (state.agencyName?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AgencyStatus.AGENCY_VR -> Text(
                    text = "⚠️ Personální agentura (ARES: zprostředkování zaměstnání)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AgencyStatus.NOT_AGENCY -> Text(
                    text = "Není evidovaná agentura (podle MPSV seznamu a ARES VR).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AgencyStatus.UNDETERMINED -> Text(
                    text = "Nelze určit (bez dat / nevalidní IČO).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when {
                state.resolverLoading -> CircularProgressIndicator()
                state.resolverError -> Text(
                    text = "Kandidáty koncové firmy se nepodařilo načíst (ARES nedostupný).",
                    style = MaterialTheme.typography.bodySmall,
                )
                state.resolverNote != null -> Text(
                    text = state.resolverNote,
                    style = MaterialTheme.typography.bodySmall,
                )
                state.resolverCandidates.isNotEmpty() -> {
                    Text(
                        text = "Kandidáti koncové firmy (návrh, potvrzuješ ty):",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.resolverCandidates.forEach { candidate ->
                        CandidateRow(candidate = candidate)
                    }
                }
            }
        }
    }
}

/** Řádka kandidáta resolveru: skóre + název + zdůvodnění (N7). */
@Composable
private fun CandidateRow(candidate: ResolverCandidate) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "${candidate.company.businessName} (skóre ${candidate.score})",
            style = MaterialTheme.typography.bodyMedium,
        )
        candidate.reasons.forEach { reason ->
            Text(text = "• $reason", style = MaterialTheme.typography.bodySmall)
        }
    }
}