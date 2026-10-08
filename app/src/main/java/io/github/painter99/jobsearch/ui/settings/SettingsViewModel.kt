package io.github.painter99.jobsearch.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.painter99.jobsearch.data.storage.AiSettings
import io.github.painter99.jobsearch.data.storage.AiSettingsStore
import io.github.painter99.jobsearch.data.storage.ApiKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Stav Settings obrazovky (M1.7 T5). [apiKeyInput] je jen nový vstup —
 * uložený klíč se v UI nikdy nezobrazuje (hasKey = bool, klíč neopustí
 * DataStore kromě samotného OpenRouter volání).
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val hasKey: Boolean = false,
    val apiKeyInput: String = "",
    val rankerModel: String = AiSettings.DEFAULT_MODEL,
    val summarizerModel: String = AiSettings.DEFAULT_MODEL,
    val tosConsent: Boolean = false,
    val saving: Boolean = false,
    val message: String? = null,
)

/**
 * ViewModel Settings (M1.7 T5): OpenRouter API klíč (BYOK přes [ApiKeyStore]),
 * modely per funkce (D7), souhlas s ToS/Model Terms (§5.2).
 * Privacy doporučení (prompt logging off / ZDR, §6.1) je statický text v UI.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
    private val aiSettingsStore: AiSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = aiSettingsStore.load()
            _state.update {
                it.copy(
                    loading = false,
                    hasKey = !settings.apiKey.isNullOrBlank(),
                    rankerModel = settings.rankerModel,
                    summarizerModel = settings.summarizerModel,
                    tosConsent = settings.tosConsent,
                )
            }
        }
    }

    fun onApiKeyInput(value: String) = _state.update { it.copy(apiKeyInput = value) }

    fun onRankerModel(value: String) = _state.update { it.copy(rankerModel = value) }

    fun onSummarizerModel(value: String) = _state.update { it.copy(summarizerModel = value) }

    fun onTosConsent(consent: Boolean) = _state.update { it.copy(tosConsent = consent) }

    /**
     * Uloží nastavení. Klíč se přepíše JEN když je vstup neprázdný —
     * prázdné pole = beze změny (neriskujeme smazání klíče omylem).
     */
    fun save() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, message = null) }
            val s = _state.value
            val key = s.apiKeyInput.trim()
            if (key.isNotEmpty()) apiKeyStore.set(key)
            aiSettingsStore.saveModels(s.rankerModel.trim(), s.summarizerModel.trim())
            aiSettingsStore.setTosConsent(s.tosConsent)
            val settings = aiSettingsStore.load()
            _state.update {
                it.copy(
                    saving = false,
                    hasKey = !settings.apiKey.isNullOrBlank(),
                    apiKeyInput = "",
                    message = "Nastavení uloženo.",
                )
            }
        }
    }

    /** Smaže klíč → AI se vypne; zbytek appky funguje dál (AC4). */
    fun clearKey() {
        viewModelScope.launch {
            apiKeyStore.clear()
            _state.update {
                it.copy(hasKey = false, apiKeyInput = "", message = "Klíč smazán — AI je vypnuté.")
            }
        }
    }
}