package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Municipality
import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Klient MPSV číselníků (data.mpsv.cz/od/soubory/ciselniky/).
 * M1.4: obce.json (~900 KB, 6 258 obcí) — autocomplete lokalit + join
 * RÚIAN kódů pro LocationFilter. OkHttp bez Retrofit (WSW vzor),
 * výsledek jako [FetchResult].
 */
class MpsvCiselnikyClient(
    private val client: OkHttpClient,
    private val obceUrl: String = DEFAULT_OBCE_URL,
) {

    suspend fun fetchMunicipalities(): FetchResult<List<Municipality>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(obceUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FetchResult.HttpError(response.code)
                val body = response.body?.string()
                    ?: return@withContext FetchResult.NetworkError("empty response body")
                parseMunicipalities(body)
            }
        } catch (e: Exception) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun parseMunicipalities(body: String): FetchResult<List<Municipality>> {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            return FetchResult.ParseError("not JSON: ${e.message}")
        }
        val municipalities = MunicipalityParser().parse(root)
        if (municipalities.isEmpty()) {
            // živý číselník má ~6 258 obcí; prázdný = podezřelý payload
            return FetchResult.ParseError("empty polozky — suspicious payload")
        }
        return FetchResult.Success(municipalities)
    }

    companion object {
        const val DEFAULT_OBCE_URL = "https://data.mpsv.cz/od/soubory/ciselniky/obce.json"
    }
}