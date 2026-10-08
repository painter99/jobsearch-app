package io.github.painter99.jobsearch.data.ai

import io.github.painter99.jobsearch.data.FetchResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T1: OpenRouterClient přes MockWebServer — žádná reálná síť.
 *
 * Ověřuje: request (URL, Authorization bearer, model, response_format),
 * parse úspěchu, HTTP chyby (401/429), rozbitý JSON, prázdné choices.
 */
class OpenRouterClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun client() = OpenRouterClient(
        client = OkHttpClient(),
        baseUrl = server.url("/api/v1").toString(),
    )

    private fun successBody(content: String): String =
        """{"id":"gen-1","model":"test/model","choices":[{"index":0,"message":""" +
            """{"role":"assistant","content":${org.json.JSONObject.quote(content)}}}]}"""

    @Test
    fun `complete - success vrací content a posílá bearer hlavičku`() = runTest {
        server.enqueue(
            MockResponse().setBody(successBody("ahoj"))
                .setHeader("Content-Type", "application/json"),
        )

        val result = client().complete(
            apiKey = "sk-test",
            model = "test/model",
            messages = listOf(ChatMessage("user", "pozdrav")),
            jsonOutput = true,
        )

        assertTrue(result is FetchResult.Success)
        assertEquals("ahoj", (result as FetchResult.Success).data)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test", recorded.getHeader("Authorization"))
        val body = org.json.JSONObject(recorded.body.readUtf8())
        assertEquals("test/model", body.getString("model"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertEquals("user", body.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test
    fun `complete - 401 bez klíče → HttpError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val result = client().complete("sk-bad", "m", listOf(ChatMessage("user", "x")))
        assertEquals(FetchResult.HttpError(401), result)
    }

    @Test
    fun `complete - 429 rate limit → HttpError`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate"}}"""))
        val result = client().complete("sk-x", "m", listOf(ChatMessage("user", "x")))
        assertEquals(FetchResult.HttpError(429), result)
    }

    @Test
    fun `complete - rozbitý JSON → ParseError`() = runTest {
        server.enqueue(MockResponse().setBody("not json at all").setResponseCode(200))
        val result = client().complete("sk-x", "m", listOf(ChatMessage("user", "x")))
        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun `complete - prázdné choices → ParseError`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        val result = client().complete("sk-x", "m", listOf(ChatMessage("user", "x")))
        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun `complete - síťová chyba (server shozen) → NetworkError`() = runTest {
        val url = server.url("/api/v1").toString()
        server.shutdown()
        val result = OpenRouterClient(OkHttpClient(), url).complete("sk-x", "m", listOf(ChatMessage("user", "x")))
        assertTrue(result is FetchResult.NetworkError)
    }
}

/**
 * Tolerantní parse AI odpovědi (fallbacky: fence, obalený text).
 */
class TolerantJsonTest {

    @Test
    fun `parseObject - čistý JSON`() {
        val json = TolerantJson.parseObject("""{"a":1}""")
        assertEquals(1, json?.getInt("a"))
    }

    @Test
    fun `parseObject - json fence`() {
        val json = TolerantJson.parseObject("```json\n{\"a\":2}\n```")
        assertEquals(2, json?.getInt("a"))
    }

    @Test
    fun `parseObject - fence bez označení`() {
        val json = TolerantJson.parseObject("```\n{\"a\":3}\n```")
        assertEquals(3, json?.getInt("a"))
    }

    @Test
    fun `parseObject - JSON obalený textem`() {
        val json = TolerantJson.parseObject("Tady je výsledek: {\"a\":4} — doufám, že pomáhá.")
        assertEquals(4, json?.getInt("a"))
    }

    @Test
    fun `parseObject - nevalidní → null`() {
        assertEquals(null, TolerantJson.parseObject("žádný json tady"))
        assertEquals(null, TolerantJson.parseObject(""))
    }
}