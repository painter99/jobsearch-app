package io.github.painter99.jobsearch.data.pracecz

import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Klient výpisových stránek prace.cz (data-pracecz, M1.4).
 *
 * ⚠️ ToS riziko (ověřeno 6. 10. 2026): Alma Career „Podmínky používání
 * Elektronických systémů" §4.7(e) řadí automatické/hromadné čtení obsahu
 * mezi PODSTATNÁ PORUŠENÍ. Tato třída se proto NIKDY nevolá automaticky —
 * pouze na explicitní opt-in uživatele (default OFF, PRD v0.2 R1),
 * s in-app disclosure a rate limitem (UI vrstva, M1.6).
 *
 * Stahuje JEN výpisové stránky /nabidky/{kraj}/{obec}/ (server-rendered,
 * ~22 deep linků na stránku) a extrahuje deep linky na detaily inzerátů
 * (/nabidka/{uuid}, /firma/{slug}/nabidka/{uuid}). Žádný obsah inzerátů
 * se nestahuje ani neukládá (Model A: deep linky only).
 *
 * Ověřeno živě 7. 10. 2026: výpis Lutín = HTTP 200, 25 odkazů,
 * paginace ?page=N server-side; tracking parametr ?rps= se stripuje.
 */
class PraceCzClient(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    /**
     * Stáhne jednu stránku výpisu pro lokalitu (kraj + obec slug)
     * a vrátí deep linky. [page] je 1-based (paginace ?page=N).
     */
    suspend fun fetchListing(
        regionSlug: String,
        municipalitySlug: String,
        page: Int = 1,
    ): FetchResult<ListingPage> = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append(baseUrl.trimEnd('/'))
                append("/nabidky/")
                append(regionSlug)
                append('/')
                append(municipalitySlug)
                append('/')
                if (page > 1) append("?page=").append(page)
            }
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FetchResult.HttpError(response.code)
                val body = response.body?.string()
                    ?: return@withContext FetchResult.NetworkError("empty response body")
                try {
                    val parsed = PraceCzListingParser().parse(body)
                    FetchResult.Success(parsed)
                } catch (e: IOException) {
                    FetchResult.ParseError("listing parse failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.prace.cz"
    }
}

/**
 * Jedna stránka výpisu: deep linky detailů (absolutní URL, bez tracking
 * parametrů) + signál paginace.
 */
data class ListingPage(
    val deepLinks: List<String>,
    val hasNextPage: Boolean,
)