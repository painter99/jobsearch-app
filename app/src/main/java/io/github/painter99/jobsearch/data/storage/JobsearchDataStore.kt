package io.github.painter99.jobsearch.data.storage

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Jediná instance DataStore pro appku (soubor `jobsearch.preferences_pb`).
 *
 * DataStore vyžaduje max. JEDNU instanci na soubor v rámci procesu — proto
 * sdílený delegate, kterého používají [CriteriaStore], [ApiKeyStore] i
 * [DataStoreSyncAnchorStore]. Více instancí na stejném souboru = crash.
 */
internal val Context.jobsearchDataStore by preferencesDataStore(name = "jobsearch")