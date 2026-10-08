package io.github.painter99.jobsearch.ai

import io.github.painter99.jobsearch.core.model.DossierVerdict
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.ai.ChatMessage
import io.github.painter99.jobsearch.data.ai.OpenRouterClient
import io.github.painter99.jobsearch.data.ai.TolerantJson
import io.github.painter99.jobsearch.ui.dossier.ChecklistRow
import io.github.painter99.jobsearch.ui.theme.shiftLabel
import org.json.JSONObject

/**
 * Shrnutí dossieru (M1.7 T4, US5) s disclaimerem „AI doporučuje,
 * nerozhoduje" (OpenRouter ToS §16).
 *
 * Vstup = strukturovaná data dossieru (nabídka, checklist, verdikt, poznámky,
 * kandidáti resolveru) serializovaná do JSON pro prompt. Bez klíče se
 * funkce nezobrazí (AC4); volá se jen na explicitní tlačítko (D6).
 */
class DossierSummarizer(
    private val client: OpenRouterClient,
    private val promptLoader: PromptLoader,
) {

    suspend fun summarize(
        apiKey: String,
        model: String,
        offer: JobOffer,
        checklist: List<ChecklistRow>,
        verdict: DossierVerdict,
        notes: String,
        agencyName: String?,
        candidates: List<io.github.painter99.jobsearch.pipeline.ResolverCandidate>,
    ): FetchResult<String> {
        val prompt = promptLoader.load(
            "dossier-summary.md",
            mapOf("dossier" to dossierJson(offer, checklist, verdict, notes, agencyName, candidates)),
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
            is FetchResult.Success -> parseSummary(r.data)
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }
    }

    /** {"summary": "• …"} → odrážky (UI přidá disclaimer, ToS §16). */
    internal fun parseSummary(text: String): FetchResult<String> {
        val json = TolerantJson.parseObject(text)
            ?: return FetchResult.ParseError("AI odpověď není JSON objekt: ${text.take(200)}")
        val summary = json.optStringOrNull("summary")
            ?: return FetchResult.ParseError("AI odpověď nemá pole 'summary'")
        return FetchResult.Success(summary)
    }

    /** Dossier jako JSON pro prompt (data, ne kód; žádné osobní údaje). */
    internal fun dossierJson(
        offer: JobOffer,
        checklist: List<ChecklistRow>,
        verdict: DossierVerdict,
        notes: String,
        agencyName: String?,
        candidates: List<io.github.painter99.jobsearch.pipeline.ResolverCandidate>,
    ): String {
        val json = JSONObject()
            .put("profession", offer.profession)
            .put("referenceNumber", offer.referenceNumber)
            .put("salaryFrom", offer.salaryFrom ?: JSONObject.NULL)
            .put("salaryTo", offer.salaryTo ?: JSONObject.NULL)
            .put(
                "shift",
                offer.shiftPattern?.let { shiftLabel(it) } ?: JSONObject.NULL,
            )
            .put("hoursPerWeek", offer.hoursPerWeek ?: JSONObject.NULL)
            .put("employer", offer.employer?.name ?: JSONObject.NULL)
            .put("agency", agencyName ?: JSONObject.NULL)
            .put("verdict", verdict.name)
            .put("notes", notes)
            .put(
                "checklist",
                org.json.JSONArray(
                    checklist.map { row ->
                        JSONObject()
                            .put("item", row.item.label)
                            .put("checked", row.checked)
                    },
                ),
            )
            .put(
                "candidates",
                org.json.JSONArray(
                    candidates.map { c ->
                        JSONObject()
                            .put("ico", c.company.ico)
                            .put("name", c.company.businessName)
                            .put("score", c.score)
                    },
                ),
            )
        return json.toString(2)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    companion object {
        private const val SYSTEM_RULES =
            "Jsi asistent pro prověřování nabídk práce v ČR. Odpovídáš vždy čistým JSON objektem."
    }
}