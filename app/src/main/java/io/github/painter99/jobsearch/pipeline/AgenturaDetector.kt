package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.Agentura
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.ares.AresClient
import io.github.painter99.jobsearch.data.mpsv.MpsvAgenturyClient

/**
 * Výsledek detekce agenturní nabídky (pilíř č. 1A, US1).
 *
 * Zdroj detekce je součástí statusu — UI ukazuje PROČ:
 * AGENTURA_MPSV = IČO je v oficiálním MPSV seznamu agentur (deterministické),
 * AGENTURA_VR   = ARES VR má předmět „Zprostředkování zaměstnání" (doplněk),
 * NENI_AGENTURA = obě vrstvy prošly bez shody,
 * NEVYDECENO    = data nedostupná/nevalidní — netvrdíme ani negativ (N7).
 */
enum class AgenturaStatus {
    AGENTURA_MPSV,
    AGENTURA_VR,
    NENI_AGENTURA,
    NEVYDECENO,
}

/**
 * Detekce personální agentury podle IČO.
 *
 * Primární zdroj = MPSV oficiální seznam agentur (deterministický, denně
 * aktualizovaný); doplněk = ARES VR předměty podnikání. Seznam se stahuje
 * jednou za běh procesu (in-memory; trvalý cache až M1.5 — schválený
 * předpoklad TASKS M1.2).
 *
 * N7: NEVYDECENO nikdy netvrdí „není agentura" — jen říká, že nemáme data.
 */
class AgenturaDetector(
    private val mpsvClient: MpsvAgenturyClient,
    private val aresClient: AresClient,
) {

    private var seznamAgentur: List<Agentura>? = null

    /** Načte (a cachuje) oficiální seznam agentur; chyby se necachují. */
    suspend fun nactiSeznamAgentur(): FetchResult<List<Agentura>> {
        seznamAgentur?.let { return FetchResult.Success(it) }
        return when (val r = mpsvClient.fetch()) {
            is FetchResult.Success -> {
                seznamAgentur = r.data
                r
            }
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }
    }

    /**
     * Detekce agentury podle IČO. Nevalidní IČO → NEVYDECENO bez síťového
     * volání. MPSV seznam má prioritu; VR text se dotazuje jen když IČO
     * v seznamu není (nebo seznam není dostupný).
     */
    suspend fun jeAgentura(ico: String): AgenturaStatus {
        val clean = ico.trim()
        if (clean.length != 8 || clean.any { it !in '0'..'9' }) {
            return AgenturaStatus.NEVYDECENO
        }

        val seznam = when (val r = nactiSeznamAgentur()) {
            is FetchResult.Success -> r.data
            else -> null
        }
        if (seznam?.any { it.ico == clean } == true) {
            return AgenturaStatus.AGENTURA_MPSV
        }

        return when (val vr = aresClient.vrPredmetyPodnikani(clean)) {
            is FetchResult.Success ->
                if (vr.data.any {
                        it.contains("zprostředkování zaměstnání", ignoreCase = true)
                    }
                ) {
                    AgenturaStatus.AGENTURA_VR
                } else {
                    AgenturaStatus.NENI_AGENTURA
                }
            else -> AgenturaStatus.NEVYDECENO
        }
    }

    /** Název agentury z MPSV seznamu pro UI (není osobní údaj); null = nevíme. */
    fun nazevAgentury(ico: String): String? =
        seznamAgentur?.firstOrNull { it.ico == ico.trim() }?.nazev
}