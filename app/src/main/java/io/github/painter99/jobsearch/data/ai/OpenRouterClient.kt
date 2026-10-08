package io.github.painter99.jobsearch.data.ai

import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Jedna zpráva chatu (role: "system" / "user" / "assistant"). */
data class ChatMessage(val role: String, val content: String)

/**
 * Klient OpenRouter Chat Completions API (M1.7 T1, BYOK).
 *
 * Ověřená fakta (OpenRouter docs, 8. 10. 2026):
 * - POST {base}/chat/completions
 * - hlavičky: `Authorization: Bearer <klíč>`; volitelné `HTTP-Referer` /
 *   `X-Title` (aplikace seIdentifikuje ve veřejných OpenRouter statistikách)
 * - tělo: {"model", "messages", "response_format": {"type": "json_object"}}
 * - odpověď: {"choices": [{"message": {"content": "…"}}], …}
 *
 * Klíč se předává per volání (klient je stateless) — perzistence v ApiKeyStore.
 * FetchResult vzor jako AresClient: HttpError / NetworkError / ParseError.
 * D6: volá VŽDY jen explicitní akce uživatele (tlačítko) — tokenová disciplína.
 */
class OpenRouterClient(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE,
) {

    /**
     * Jedno chat completion volání. [jsonOutput] = structured output
     * (response_format json_object; schéma výstupu popisuje prompt samotný).
     * Vrací raw content modelu; JSON parse dělá volající přes [TolerantJson].
     */
    suspend fun complete(
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
        jsonOutput: Boolean = false,
    ): FetchResult<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("model", model)
                put(
                    "messages",
                    JSONArray().apply {
                        messages.forEach {
                            put(JSONObject().put("role", it.role).put("content", it.content))
                        }
                    },
                )
                if (jsonOutput) {
                    put("response_format", JSONObject().put("type", "json_object"))
                }
            }
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", APP_REFERER)
                .header("X-Title", APP_TITLE)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext FetchResult.HttpError(response.code)
                }
                val text = response.body?.string()
                    ?: return@withContext FetchResult.NetworkError("empty response body")
                try {
                    val json = JSONObject(text)
                    val content = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optStringOrNull("content")
                    if (content.isNullOrBlank()) {
                        FetchResult.ParseError("choices[0].message.content chybí: ${text.take(200)}")
                    } else {
                        FetchResult.Success(content)
                    }
                } catch (e: Exception) {
                    FetchResult.ParseError("invalid JSON: ${e.message}")
                }
            }
        } catch (e: Exception) {
            FetchResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    companion object {
        const val DEFAULT_BASE = "https://openrouter.ai/api/v1"

        /** OpenRouter doporučené identifikační hlavičky (volitelné). */
        const val APP_REFERER = "https://github.com/painter99/jobsearch-app"
        const val APP_TITLE = "Jobsearch"
    }
}

/**
 * Tolerantní parse JSON odpovědi modelu (M1.7 T1 fallback).
 *
 * response_format json_object většinou vrátí čistý JSON, ale některé modely
 * odpověď obalí do ```json fence nebo přidají okolní text — proto fallbacky:
 * 1. přímý parse, 2. fence, 3. první `{` … poslední `}`.
 * Nevalidní vstup → null (volající mapuje na ParseError).
 */
object TolerantJson {

    fun parseObject(text: String): JSONObject? {
        val trimmed = text.trim()
        directParse(trimmed)?.let { return it }

        val fenced = FENCE.find(trimmed)?.groupValues?.get(1)
        if (fenced != null) {
            directParse(fenced.trim())?.let { return it }
        }

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            directParse(trimmed.substring(start, end + 1))?.let { return it }
        }
        return null
    }

    private fun directParse(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (_: Exception) {
        null
    }

    private val FENCE = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
}