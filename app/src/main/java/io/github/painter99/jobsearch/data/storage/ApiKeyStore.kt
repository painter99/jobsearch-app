package io.github.painter99.jobsearch.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Uložení OpenRouter API klíče (BYOK, M1.7) — DataStore Preferences (M1.5).
 *
 * PRD §11: klíč jen v DataStore na zařízení, nikdy do repa (repo je veřejné).
 * null = bez klíče → appka plně funkční i bez AI (AC4).
 */
class ApiKeyStore(private val dataStore: DataStore<Preferences>) {

    /** Vrací uložený klíč, nebo null (= bez klíče). */
    suspend fun get(): String? {
        return try {
            dataStore.data.first()[KEY_OPENROUTER]
        } catch (e: Exception) {
            null
        }
    }

    /** Uloží klíč. */
    suspend fun set(key: String) {
        dataStore.edit { it[KEY_OPENROUTER] = key }
    }

    /** Smaže klíč (uživatel AI vypne). */
    suspend fun clear() {
        dataStore.edit { it.remove(KEY_OPENROUTER) }
    }

    companion object {
        private val KEY_OPENROUTER = stringPreferencesKey("openrouter_api_key")
    }
}