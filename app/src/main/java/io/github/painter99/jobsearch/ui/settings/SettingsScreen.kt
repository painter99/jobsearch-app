package io.github.painter99.jobsearch.ui.settings

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.painter99.jobsearch.ui.dossier.openUrl
import io.github.painter99.jobsearch.ui.theme.AppScreen

/**
 * Settings obrazovka (M1.7 T5; M1.6c U5 AppScreen rámec): OpenRouter BYOK
 * klíč, modely per funkce (D7), souhlas s ToS/Model Terms (§5.2), privacy
 * doporučení (§6.1). Klíč se ukládá jen do DataStore na zařízení (PRD §11).
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onApiKeyInput = viewModel::onApiKeyInput,
        onRankerModel = viewModel::onRankerModel,
        onSummarizerModel = viewModel::onSummarizerModel,
        onTosConsent = viewModel::onTosConsent,
        onSave = viewModel::save,
        onClearKey = viewModel::clearKey,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onApiKeyInput: (String) -> Unit,
    onRankerModel: (String) -> Unit,
    onSummarizerModel: (String) -> Unit,
    onTosConsent: (Boolean) -> Unit,
    onSave: () -> Unit,
    onClearKey: () -> Unit,
) {
    AppScreen(title = "Nastavení", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator()
                return@Column
            }
            state.message?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }

            ApiKeySection(state = state, onApiKeyInput = onApiKeyInput, onClearKey = onClearKey)
            ModelsSection(state = state, onRankerModel = onRankerModel, onSummarizerModel = onSummarizerModel)
            TosSection(state = state, onTosConsent = onTosConsent)
            PrivacySection()

            Button(onClick = onSave, enabled = !state.saving) { Text(text = "Uložit nastavení") }
        }
    }
}

/** BYOK: klíč se nikdy nezobrazuje — jen indikace, že je uložen. */
@Composable
private fun ApiKeySection(
    state: SettingsUiState,
    onApiKeyInput: (String) -> Unit,
    onClearKey: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "OpenRouter API klíč (volitelné AI)", style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (state.hasKey) "Klíč je uložený na zařízení." else "Klíč není uložený — appka funguje i bez něj.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.apiKeyInput,
                onValueChange = onApiKeyInput,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = if (state.hasKey) "Nový klíč (nepovinné)" else "sk-or-v1-…") },
                singleLine = true,
            )
            if (state.hasKey) {
                TextButton(onClick = onClearKey) { Text(text = "Smazat klíč (vypnout AI)") }
            }
        }
    }
}

/** Modely per funkce (D7) — identifikátory modelů OpenRouter. */
@Composable
private fun ModelsSection(
    state: SettingsUiState,
    onRankerModel: (String) -> Unit,
    onSummarizerModel: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Modely (volba na tobě, ToS §5.6)", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.rankerModel,
                onValueChange = onRankerModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Model pro řazení kandidátů") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.summarizerModel,
                onValueChange = onSummarizerModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Model pro shrnutí dossieru") },
                singleLine = true,
            )
        }
    }
}

/** Souhlas s ToS/Model Terms (§5.2) — bez souhlasu se AI nezobrazí. */
@Composable
private fun TosSection(state: SettingsUiState, onTosConsent: (Boolean) -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Souhlas s podmínkami", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.tosConsent, onCheckedChange = onTosConsent)
                Text(
                    text = "Přebral(a) jsem OpenRouter Terms of Service a podmínky " +
                        "vybraných modelů (Model Terms) a souhlasím.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = { context.openUrl(OPENROUTER_TOS_URL) }) {
                Text(text = "Otevřít OpenRouter Terms of Service")
            }
        }
    }
}

/** Privacy doporučení (§6.1) — statická rada, nic neukládáme. */
@Composable
private fun PrivacySection() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Soukromí (doporučení)", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Doporučujeme v účtu OpenRouter vypnout prompt logging " +
                    "a volit modely se zásadami zero data retention (ZDR). " +
                    "Do AI volání se posílají data dossieru (profese, mzda, obec, " +
                    "IČO firem) — nikdy klíč ani osobní kontakty.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { context.openUrl(OPENROUTER_PRIVACY_URL) }) {
                Text(text = "OpenRouter — nastavení soukromí")
            }
        }
    }
}

private const val OPENROUTER_TOS_URL = "https://openrouter.ai/terms"
private const val OPENROUTER_PRIVACY_URL = "https://openrouter.ai/docs/framework"