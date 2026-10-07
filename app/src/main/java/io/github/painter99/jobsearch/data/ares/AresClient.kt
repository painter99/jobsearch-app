package io.github.painter99.jobsearch.data.ares

import io.github.painter99.jobsearch.core.model.Company
import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Klient ARES REST API (rejstříková vrstva dossieru).
 *
 * Ověřené endpointy (6.–7. 10. 2026, bez tokenu):
 * - detail: GET {base}/ekonomicke-subjekty/{ico}?detail=2
 * - VR:     GET {base}/ekonomicke-subjekty-vr/{ico}?detail=2
 *   (předměty podnikání na cestě zaznamy[].cinnosti.predmetPodnikani[].hodnota)
 * - vyhledat: POST {base}/ekonomicke-subjekty/vyhledat
 *   (tělo {"czNace": [...], "pocet": N}; odpověď = {pocetCelkem, ekonomickeSubjekty[]})
 *
 * Vyhledat POST (dávky po NACE) — M1.3 (resolver). Gotchy: sidlo nemá klíč
 * `obec` s názvem — název obce nese `nazevObce` (ověřeno na živé odpovědi
 * 7. 10. 2026); czNace kód = komprimovaný formát (28.13 → "28130").
 */
class AresClient(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE,
) {

    suspend fun detail(ico: String): FetchResult<Company> =
        when (val r = getJson("ekonomicke-subjekty/$ico?detail=2")) {
            is FetchResult.Success -> parseDetail(r.data)
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }

    /** Předměty podnikání z VR (pro detekci „Zprostředkování zaměstnání"). */
    suspend fun vrBusinessActivities(ico: String): FetchResult<List<String>> =
        when (val r = getJson("ekonomicke-subjekty-vr/$ico?detail=2")) {
            is FetchResult.Success -> FetchResult.Success(parseVrActivities(r.data))
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }

    /**
     * POST vyhledat podle CZ-NACE (komprimované kódy). Obec se filtruje
     * client-side (sidlo filtr umí jen číselné RÚIAN kódy). Vrací seznam
     * firem z odpovědi (bez [pocetCelkem] — ten řeší volající přes raw
     * odpověď, pokud ho potřebuje).
     */
    suspend fun search(czNace: List<String>): FetchResult<List<Company>> {
        if (czNace.isEmpty()) {
            return FetchResult.ParseError("czNace list is empty — ARES would return unfiltered")
        }
        val body = JSONObject().apply {
            put("czNace", JSONArray(czNace))
            put("pocet", SEARCH_PAGE_SIZE)
        }
        return when (val r = postJson("ekonomicke-subjekty/vyhledat", body)) {
            is FetchResult.Success -> FetchResult.Success(parseSearchItems(r.data))
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }
    }

    private suspend fun getJson(path: String): FetchResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("$baseUrl/$path").build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext FetchResult.HttpError(response.code)
                    val body = response.body?.string()
                        ?: return@withContext FetchResult.NetworkError("empty response body")
                    try {
                        FetchResult.Success(JSONObject(body))
                    } catch (e: Exception) {
                        FetchResult.ParseError("invalid JSON: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
            }
        }

    private suspend fun postJson(path: String, body: JSONObject): FetchResult<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/$path")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext FetchResult.HttpError(response.code)
                    val responseBody = response.body?.string()
                        ?: return@withContext FetchResult.NetworkError("empty response body")
                    try {
                        FetchResult.Success(JSONObject(responseBody))
                    } catch (e: Exception) {
                        FetchResult.ParseError("invalid JSON: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
            }
        }

    private fun parseDetail(json: JSONObject): FetchResult<Company> {
        val sidlo = json.optJSONObject("sidlo")
        val company = Company(
            ico = json.optStringOrNull("ico") ?: "",
            businessName = json.optStringOrNull("obchodniJmeno") ?: "",
            municipality = sidlo?.optStringOrNull("nazevObce"),
            districtName = sidlo?.optStringOrNull("nazevOkresu"),
            foundedOn = json.optStringOrNull("datumVzniku"),
            czNace = json.optJSONArray("czNace").toStringList(),
            legalForm = json.optStringOrNull("pravniForma"),
        )
        if (company.ico.isBlank() && company.businessName.isBlank()) {
            return FetchResult.ParseError("detail bez ico i obchodniJmeno")
        }
        return FetchResult.Success(company)
    }

    private fun parseSearchItems(json: JSONObject): List<Company> {
        val items = json.optJSONArray("ekonomickeSubjekty") ?: return emptyList()
        val result = mutableListOf<Company>()
        for (i in 0 until items.length()) {
            val s = items.optJSONObject(i) ?: continue
            val sidlo = s.optJSONObject("sidlo")
            result.add(
                Company(
                    ico = s.optStringOrNull("ico") ?: "",
                    businessName = s.optStringOrNull("obchodniJmeno") ?: "",
                    municipality = sidlo?.optStringOrNull("nazevObce"),
                    districtName = sidlo?.optStringOrNull("nazevOkresu"),
                    foundedOn = s.optStringOrNull("datumVzniku"),
                    czNace = s.optJSONArray("czNace").toStringList(),
                    legalForm = s.optStringOrNull("pravniForma"),
                )
            )
        }
        return result
    }

    private fun parseVrActivities(json: JSONObject): List<String> {
        val result = mutableListOf<String>()
        json.optJSONArray("zaznamy")?.forEachObject { record ->
            record.optJSONObject("cinnosti")
                ?.optJSONArray("predmetPodnikani")
                ?.forEachObject { activity ->
                    activity.optStringOrNull("hodnota")?.let { result.add(it.trim()) }
                }
        }
        return result
    }

    // --- Tolerantní org.json extensiony (null vs. JSONObject.NULL) ---

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until length()) {
            val s = optString(i, "")
            if (s.isNotBlank()) result.add(s)
        }
        return result
    }

    private fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            (opt(i) as? JSONObject)?.let(action)
        }
    }

    companion object {
        const val DEFAULT_BASE = "https://ares.gov.cz/ekonomicke-subjekty-v-be/rest"

        /** ARES vyhledat: max počet záznamů na stránku (dost pro resolver). */
        const val SEARCH_PAGE_SIZE = 500
    }
}