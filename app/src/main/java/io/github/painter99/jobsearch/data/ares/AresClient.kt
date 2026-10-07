package io.github.painter99.jobsearch.data.ares

import io.github.painter99.jobsearch.core.model.Company
import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Klient ARES REST API (rejstříková vrstva dossieru).
 *
 * Ověřené endpointy (6.–7. 10. 2026, bez tokenu):
 * - detail: GET {base}/ekonomicke-subjekty/{ico}?detail=2
 * - VR:     GET {base}/ekonomicke-subjekty-vr/{ico}?detail=2
 *   (předměty podnikání na cestě zaznamy[].cinnosti.predmetPodnikani[].hodnota)
 *
 * Vyhledat POST (dávky 100 IČO) až M1.3 (resolver) — scope tight.
 * Gotcha: sidlo nemá klíč `obec` s názvem — název obce nese `nazevObce`
 * (ověřeno na živé odpovědi 7. 10. 2026).
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
    }
}