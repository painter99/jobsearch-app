package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.LocalDate
import java.util.zip.GZIPInputStream

/**
 * Klient MPSV „Volná místa" (data.mpsv.cz/od/soubory/):
 *
 * - [downloadBootstrap] — full dump volna-mista.json (~187 MB, ~40 000 záznamů).
 *   Streamuje přímo do souboru (nikdy celé v paměti); stahuje se jednorázově
 *   (PRD R5: 187 MB jen na Wi-Fi s potvrzením — rozhodnutí UI/WorkManager,
 *   tato vrstva jen stahuje).
 * - [fetchIncrement] — denní přírůstek volna-mista-prirustek-YYYY-MM-DD.json.gz
 *   (~1 MB gz; novy/zmeneny/zruseny dle typZmenyOpenData). Chybějící den
 *   (HTTP 404) je běžný stav MPSV publikace → [FetchResult.HttpError] 404,
 *   volající (OfferSyncEngine) den přeskočí.
 *
 * Ověřeno živě 7. 10. 2026: přírůstek 2026-10-05 = 1 650 záznamů
 * (572 novy / 679 zmeneny / 399 zruseny), schéma shodné pro všechny typy
 * (39 klíčů, zrušené nabídky nejsou tombstony — parsují se stejně).
 */
class MpsvOffersClient(
    private val client: OkHttpClient,
    private val bootstrapUrl: String = DEFAULT_BOOTSTRAP_URL,
    private val incrementUrlBase: String = DEFAULT_INCREMENT_URL_BASE,
) {

    /**
     * Stáhne full dump do cílového souboru. Vrací cestu k souboru.
     * Stream přes OkHttp response.body.source() — 187 MB nikdy v paměti.
     */
    suspend fun downloadBootstrap(targetFile: File): FetchResult<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(bootstrapUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FetchResult.HttpError(response.code)
                val body = response.body ?: return@withContext FetchResult.NetworkError("empty response body")
                val tmp = File(targetFile.parentFile, targetFile.name + ".tmp")
                tmp.parentFile?.mkdirs()
                try {
                    body.byteStream().use { input ->
                        tmp.outputStream().use { out ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (e: Exception) {
                    tmp.delete()
                    return@withContext FetchResult.NetworkError("bootstrap write failed: ${e.message}")
                }
                if (tmp.length() == 0L) {
                    tmp.delete()
                    return@withContext FetchResult.ParseError("empty bootstrap payload")
                }
                if (!tmp.renameTo(targetFile)) {
                    tmp.delete()
                    return@withContext FetchResult.NetworkError("rename to ${targetFile.name} failed")
                }
                FetchResult.Success(targetFile)
            }
        } catch (e: Exception) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Stáhne a rozbalí denní přírůstek pro [date] → JSON text v paměti
     * (~8 MB raw; 1 650 záznamů/den — pohodlné v paměti).
     */
    suspend fun fetchIncrement(date: LocalDate): FetchResult<String> = withContext(Dispatchers.IO) {
        try {
            val url = incrementUrl(date)
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext FetchResult.HttpError(response.code)
                val body = response.body ?: return@withContext FetchResult.NetworkError("empty response body")
                try {
                    val text = GZIPInputStream(body.byteStream()).use { gz ->
                        gz.readBytes().toString(Charsets.UTF_8)
                    }
                    if (text.isBlank()) {
                        return@withContext FetchResult.ParseError("empty increment payload")
                    }
                    FetchResult.Success(text)
                } catch (e: Exception) {
                    FetchResult.ParseError("not gzipped JSON: ${e.message}")
                }
            }
        } catch (e: Exception) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun incrementUrl(date: LocalDate): String {
        val fileName = "volna-mista-prirustek-${date}.json.gz"
        return incrementUrlBase.trimEnd('/') + "/" + fileName
    }

    companion object {
        const val DEFAULT_BOOTSTRAP_URL = "https://data.mpsv.cz/od/soubory/volna-mista/volna-mista.json"
        const val DEFAULT_INCREMENT_URL_BASE = "https://data.mpsv.cz/od/soubory/volna-mista-prirustek"
    }
}