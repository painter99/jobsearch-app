package io.github.painter99.jobsearch.data.ares

import io.github.painter99.jobsearch.core.model.ObchodniSubjekt
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
 * Gotchy: sidlo nemá `obec` s názvem — název obce nese `nazevObce`
 * (ověřeno na živé odpovědi 7. 10. 2026).
 */
class AresClient(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE,
) {

    suspend fun detail(ico: String): FetchResult<ObchodniSubjekt> =
        when (val r = getJson("ekonomicke-subjekty/$ico?detail=2")) {
            is FetchResult.Success -> parseDetail(r.data)
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }

    /** Předměty podnikání z VR (pro detekci „Zprostředkování zaměstnání"). */
    suspend fun vrPredmetyPodnikani(ico: String): FetchResult<List<String>> =
        when (val r = getJson("ekonomicke-subjekty-vr/$ico?detail=2")) {
            is FetchResult.Success -> FetchResult.Success(parseVrPredmety(r.data))
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

    private fun parseDetail(json: JSONObject): FetchResult<ObchodniSubjekt> {
        val sidlo = json.optJSONObject("sidlo")
        val subjekt = ObchodniSubjekt(
            ico = json.optStringOrNull("ico") ?: "",
            obchodniJmeno = json.optStringOrNull("obchodniJmeno") ?: "",
            obec = sidlo?.optStringOrNull("nazevObce"),
            nazevOkresu = sidlo?.optStringOrNull("nazevOkresu"),
            datumVzniku = json.optStringOrNull("datumVzniku"),
            czNace = json.optJSONArray("czNace").toStringList(),
            pravniForma = json.optStringOrNull("pravniForma"),
        )
        if (subjekt.ico.isBlank() && subjekt.obchodniJmeno.isBlank()) {
            return FetchResult.ParseError("detail bez ico i obchodniJmeno")
        }
        return FetchResult.Success(subjekt)
    }

    private fun parseVrPredmety(json: JSONObject): List<String> {
        val result = mutableListOf<String>()
        json.optJSONArray("zaznamy")?.forEachObject { zaznam ->
            zaznam.optJSONObject("cinnosti")
                ?.optJSONArray("predmetPodnikani")
                ?.forEachObject { predmet ->
                    predmet.optStringOrNull("hodnota")?.let { result.add(it.trim()) }
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