package io.github.painter99.jobsearch.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import io.github.painter99.jobsearch.pipeline.SyncAnchorStore
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Perzistentní sync kotva [SyncAnchorStore] — DataStore Preferences (M1.5).
 *
 * Kotva (poslední úspěšně aplikovaný den + 1) přežije restart procesu —
 * catch-up [io.github.painter99.jobsearch.pipeline.OfferSyncEngine.syncDaily]
 * pokračuje tam, kde skončil. LocalDate se ukládá jako epochDay (Long).
 *
 * Rozhraní [SyncAnchorStore] je synchronní (M1.4 kontrakt, volá ho sync engine) —
 * DataStore operace se bridguje přes runBlocking (krátké operace, vlastní scope
 * DataStore = bez deadlocku).
 */
class DataStoreSyncAnchorStore(private val dataStore: DataStore<Preferences>) : SyncAnchorStore {

    override fun load(): LocalDate {
        return try {
            val epochDay = kotlinx.coroutines.runBlocking { dataStore.data.first()[KEY_ANCHOR] }
            if (epochDay != null) LocalDate.ofEpochDay(epochDay) else DEFAULT_ANCHOR
        } catch (e: Exception) {
            DEFAULT_ANCHOR
        }
    }

    override fun save(date: LocalDate) {
        kotlinx.coroutines.runBlocking {
            dataStore.edit { prefs -> prefs[KEY_ANCHOR] = date.toEpochDay() }
        }
    }

    companion object {
        /** Kotva před prvním syncem — den před vznikem datasetu přírůstků (31. 10. 2024). */
        val DEFAULT_ANCHOR: LocalDate = LocalDate.of(2024, 10, 30)
        private val KEY_ANCHOR = longPreferencesKey("sync_anchor_epoch_day")
    }
}