package io.github.painter99.jobsearch.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * AI nastavení (M1.7): modely per funkce (D7), souhlas s ToS (§5.2).
 *
 * API klíč se NEukládá zde — perzistenci má [ApiKeyStore]; [AiSettingsStore]
 * ho jen agreguje do jednoho snímku pro ViewModels. Výchozí model je pouze
 * rozumný placeholder — volba modelu je na uživateli (OpenRouter ToS §5.6).
 */
data class AiSettings(
    val apiKey: String? = null,
    val rankerModel: String = DEFAULT_MODEL,
    val summarizerModel: String = DEFAULT_MODEL,
    val tosConsent: Boolean = false,
) {
    /** AC4 + §5.2: AI funkce jsou viditelné jen s klíčem A souhlasem s ToS. */
    val aiAvailable: Boolean
        get() = !apiKey.isNullOrBlank() && tosConsent

    companion object {
        const val DEFAULT_MODEL = "openai/gpt-4o-mini"
    }
}

/** Read-only přístup k AI nastavení — kontrakt pro ViewModels (fakovatelné v testech). */
interface AiSettingsProvider {
    suspend fun load(): AiSettings
}

/**
 * Perzistence AI nastavení — DataStore Preferences (M1.5 vzor).
 * Modely + souhlas zde; klíč delegován na [ApiKeyStore] (jediný vlastník).
 */
class AiSettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val apiKeyStore: ApiKeyStore,
) : AiSettingsProvider {

    override suspend fun load(): AiSettings {
        val prefs = try {
            dataStore.data.first()
        } catch (e: Exception) {
            return AiSettings(apiKey = apiKeyStore.get())
        }
        return AiSettings(
            apiKey = apiKeyStore.get(),
            rankerModel = prefs[KEY_RANKER_MODEL] ?: AiSettings.DEFAULT_MODEL,
            summarizerModel = prefs[KEY_SUMMARIZER_MODEL] ?: AiSettings.DEFAULT_MODEL,
            tosConsent = prefs[KEY_TOS_CONSENT] ?: false,
        )
    }

    /** Uloží modely per funkce (D7 — appka nespoléhá na jeden model). */
    suspend fun saveModels(rankerModel: String, summarizerModel: String) {
        dataStore.edit { prefs ->
            prefs[KEY_RANKER_MODEL] = rankerModel
            prefs[KEY_SUMMARIZER_MODEL] = summarizerModel
        }
    }

    /** Uloží/vezme souhlas s OpenRouter ToS a Model Terms (§5.2). */
    suspend fun setTosConsent(consent: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_TOS_CONSENT] = consent
        }
    }

    companion object {
        private val KEY_RANKER_MODEL = stringPreferencesKey("ai_ranker_model")
        private val KEY_SUMMARIZER_MODEL = stringPreferencesKey("ai_summarizer_model")
        private val KEY_TOS_CONSENT = booleanPreferencesKey("ai_tos_consent")
    }
}