package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Agency
import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Klient pro MPSV oficiální seznam „Agentury práce" (primární zdroj
 * detekce agentury, PRD v0.2). OkHttp bez Retrofit (WSW M1.4 vzor),
 * výsledek jako [FetchResult] — volající vidí příčinu selhání.
 *
 * Seznam má ~1 912 záznamů (~1 MB JSON), update 1x denně — stahuje se
 * celý soubor, žádné přírůstky.
 */
class MpsvAgencyClient(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_URL,
) {

    suspend fun fetch(): FetchResult<List<Agency>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(baseUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FetchResult.HttpError(response.code)
                val body = response.body?.string()
                    ?: return@withContext FetchResult.NetworkError("empty response body")
                parseBody(body)
            }
        } catch (e: Exception) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun parseBody(body: String): FetchResult<List<Agency>> {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            return FetchResult.ParseError("not JSON: ${e.message}")
        }
        val agencies = AgencyParser().parse(root)
        if (agencies.isEmpty()) {
            // živý seznam má ~1900 záznamů; prázdný = podezřelý payload
            return FetchResult.ParseError("empty polozky — suspicious payload")
        }
        return FetchResult.Success(agencies)
    }

    companion object {
        const val DEFAULT_URL = "https://data.mpsv.cz/od/soubory/agentury-prace/agentury-prace.json"
    }
}