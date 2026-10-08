package io.github.painter99.jobsearch.ai

import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.ai.ChatMessage
import io.github.painter99.jobsearch.data.ai.OpenRouterClient
import io.github.painter99.jobsearch.data.ai.TolerantJson
import io.github.painter99.jobsearch.pipeline.ResolverCandidate
import org.json.JSONObject

/**
 * AI přeřazení kandidátů resolveru (M1.7 T3, US2).
 *
 * Vstup = deterministické kandidáty resolveru (hotový seznam), výstup =
 * stejní kandidáti přeřazení modelem + krátké AI zdůvodnění. Model dostane
 * kandidáty jako JSON (IČO, název, NACE, sídlo, skóre, důvody resolveru).
 *
 * N7: výstup je stále jen NÁVRH — potvrzuje uživatel. Bez klíče se funkce
 * nezobrazí (AC4); volá se jen na explicitní tlačítko (D6).
 */
class AiCandidateRanker(
    private val client: OpenRouterClient,
    private val promptLoader: PromptLoader,
) {

    /**
     * Přeřadí kandidáty. Chování při selhání AI = graceful degradation:
     * HttpError/NetworkError/ParseError propagujeme volajícímu (UI ukáže
     * hlášku), ale AI výstup NIKDY nenahrazuje deterministický seznam.
     */
    suspend fun rank(
        apiKey: String,
        model: String,
        profession: String,
        municipality: String,
        agencyName: String,
        candidates: List<ResolverCandidate>,
    ): FetchResult<List<RankedCandidate>> {
        if (candidates.isEmpty()) return FetchResult.ParseError("no candidates to rank")
        val prompt = promptLoader.load(
            "candidate-ranking.md",
            mapOf(
                "profession" to profession,
                "municipality" to municipality,
                "agency" to agencyName,
                "candidates" to candidatesJson(candidates),
            ),
        )
        return when (
            val r = client.complete(
                apiKey = apiKey,
                model = model,
                messages = listOf(
                    ChatMessage(role = "system", content = SYSTEM_RULES),
                    ChatMessage(role = "user", content = prompt),
                ),
                jsonOutput = true,
            )
        ) {
            is FetchResult.Success -> parseRanked(r.data, candidates)
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }
    }

    /**
     * Parse odpovědi modelu: {"ranked": [{"ico", "reason"}]}.
     * Obrana: model vrátí IČO mimo seznam → kandidát se přeskočí (nikdy
     * nevyfabulovat firmu, N7). Kandidát bez AI zdůvodnění zůstává s
     * původními důvody resolveru. Řazení = pořadí od modelu.
     */
    internal fun parseRanked(
        text: String,
        candidates: List<ResolverCandidate>,
    ): FetchResult<List<RankedCandidate>> {
        val json = TolerantJson.parseObject(text)
            ?: return FetchResult.ParseError("AI odpověď není JSON objekt: ${text.take(200)}")
        val ranked = json.optJSONArray("ranked")
            ?: return FetchResult.ParseError("AI odpověď nemá pole 'ranked'")
        val byIco = candidates.associateBy { it.company.ico }
        val result = mutableListOf<RankedCandidate>()
        for (i in 0 until ranked.length()) {
            val item = ranked.optJSONObject(i) ?: continue
            val ico = item.optStringOrNull("ico") ?: continue
            val candidate = byIco[ico] ?: continue // neznámé IČO = zahodit (N7)
            val aiReason = item.optStringOrNull("reason")
            result.add(
                RankedCandidate(
                    candidate = candidate,
                    aiReason = aiReason ?: candidate.reasons.joinToString("; "),
                ),
            )
        }
        if (result.isEmpty()) {
            return FetchResult.ParseError("AI odpověď neobsahuje žádného známého kandidáta")
        }
        return FetchResult.Success(result)
    }

    /** Kandidáti serializovaní pro prompt (data, ne kód). */
    internal fun candidatesJson(candidates: List<ResolverCandidate>): String {
        val array = org.json.JSONArray()
        candidates.forEach { c ->
            val obj = JSONObject()
                .put("ico", c.company.ico)
                .put("name", c.company.businessName)
                .put("municipality", c.company.municipality ?: "")
                .put("district", c.company.districtName ?: "")
                .put("nace", org.json.JSONArray(c.company.czNace))
                .put("foundedOn", c.company.foundedOn ?: "")
                .put("score", c.score)
                .put("reasons", org.json.JSONArray(c.reasons))
            array.put(obj)
        }
        return array.toString(2)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    companion object {
        /** Systémová role: role modelu, ne prompt (prompt = data v assets). */
        private const val SYSTEM_RULES =
            "Jsi asistent pro prověřování nabídek práce v ČR. Odpovídáš vždy čistým JSON objektem."
    }
}

/**
 * Kandidát po AI přeřazení: původní deterministická data + AI zdůvodnění.
 * N7: stále návrh, potvrzuje uživatel.
 */
data class RankedCandidate(
    val candidate: ResolverCandidate,
    /** Krátké AI zdůvodnění pozice (CZ); fallback = důvody resolveru. */
    val aiReason: String,
)