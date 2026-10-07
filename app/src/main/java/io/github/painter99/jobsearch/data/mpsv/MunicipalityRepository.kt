package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Municipality
import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repozitář obcí z číselníku MPSV (obce.json, ~900 KB, 6 258 obcí) —
 * fetch-once in-memory cache (vzor AgenturaDetector M1.2). Zdroj pro
 * autocomplete lokalit (M1.6 vlna A) a join RÚIAN kódů → názvy obcí.
 *
 * Neúspěšný fetch se NEcachuje (další volání zkusí síť znovu).
 */
class MunicipalityRepository(private val client: MpsvCiselnikyClient) {

    private val mutex = Mutex()

    @Volatile
    private var cache: List<Municipality>? = null

    suspend fun getMunicipalities(): FetchResult<List<Municipality>> {
        cache?.let { return FetchResult.Success(it) }
        return mutex.withLock {
            cache?.let { return FetchResult.Success(it) }
            val result = client.fetchMunicipalities()
            if (result is FetchResult.Success) cache = result.data
            result
        }
    }

    /** Mapa "Obec/&lt;ruian&gt;" → název (pro zobrazení lokalit v řádcích nabídek). */
    suspend fun nameMap(): Map<String, String> =
        when (val result = getMunicipalities()) {
            is FetchResult.Success -> result.data.associate { it.id to it.name }
            else -> emptyMap()
        }
}