package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.Agency
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.ares.AresClient
import io.github.painter99.jobsearch.data.mpsv.MpsvAgencyClient

/**
 * Výsledek detekce agenturní nabídky (pilíř č. 1A, US1).
 *
 * Zdroj detekce je součástí statusu — UI ukazuje PROČ:
 * AGENCY_MPSV  = IČO je v oficiálním MPSV seznamu agentur (deterministické),
 * AGENCY_VR    = ARES VR má předmět „Zprostředkování zaměstnání" (doplněk),
 * NOT_AGENCY   = obě vrstvy prošly bez shody,
 * UNDETERMINED = data nedostupná/nevalidní — netvrdíme ani negativ (N7).
 */
enum class AgencyStatus {
    AGENCY_MPSV,
    AGENCY_VR,
    NOT_AGENCY,
    UNDETERMINED,
}

/**
 * Detekce personální agentury podle IČO.
 *
 * Primární zdroj = MPSV oficiální seznam agentur (deterministický, denně
 * aktualizovaný); doplněk = ARES VR předměty podnikání. Seznam se stahuje
 * jednou za běh procesu (in-memory; trvalý cache až M1.5 — schválený
 * předpoklad TASKS M1.2).
 *
 * N7: UNDETERMINED nikdy netvrdí „není agentura" — jen říká, že nemáme data.
 */
class AgencyDetector(
    private val mpsvClient: MpsvAgencyClient,
    private val aresClient: AresClient,
) {

    private var agencies: List<Agency>? = null

    /** Načte (a cachuje) oficiální seznam agentur; chyby se necachují. */
    suspend fun loadAgencies(): FetchResult<List<Agency>> {
        agencies?.let { return FetchResult.Success(it) }
        return when (val r = mpsvClient.fetch()) {
            is FetchResult.Success -> {
                agencies = r.data
                r
            }
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }
    }

    /**
     * Detekce agentury podle IČO. Nevalidní IČO → UNDETERMINED bez síťového
     * volání. MPSV seznam má prioritu; VR text se dotazuje jen když IČO
     * v seznamu není (nebo seznam není dostupný).
     */
    suspend fun isAgency(ico: String): AgencyStatus {
        val clean = ico.trim()
        if (clean.length != 8 || clean.any { it !in '0'..'9' }) {
            return AgencyStatus.UNDETERMINED
        }

        val agencyList = when (val r = loadAgencies()) {
            is FetchResult.Success -> r.data
            else -> null
        }
        if (agencyList?.any { it.ico == clean } == true) {
            return AgencyStatus.AGENCY_MPSV
        }

        return when (val vr = aresClient.vrBusinessActivities(clean)) {
            is FetchResult.Success ->
                if (vr.data.any {
                        it.contains("zprostředkování zaměstnání", ignoreCase = true)
                    }
                ) {
                    AgencyStatus.AGENCY_VR
                } else {
                    AgencyStatus.NOT_AGENCY
                }
            else -> AgencyStatus.UNDETERMINED
        }
    }

    /** Název agentury z MPSV seznamu pro UI (není osobní údaj); null = nevíme. */
    fun agencyName(ico: String): String? =
        agencies?.firstOrNull { it.ico == ico.trim() }?.name
}