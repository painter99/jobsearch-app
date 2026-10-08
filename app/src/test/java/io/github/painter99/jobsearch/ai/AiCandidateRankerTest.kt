package io.github.painter99.jobsearch.ai

import io.github.painter99.jobsearch.core.model.Company
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.pipeline.ResolverCandidate
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
 * T3: AiCandidateRanker — parse AI odpovědi (tolerantní, obrana proti
 * vyfabulovaným IČO), serializace kandidátů do promptu, propagace chyb.
 *
 * PromptLoader = fake (interface) s reálným prompt souborem? Ne — fake
 * vrací šablonu s placeholdery substituovanou přes PromptSubstitution,
 * aby test ověřil i kontrakt promptu (žádný nezsubstituovaný placeholder).
 */
class AiCandidateRankerTest {

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

    private fun candidate(ico: String, name: String) = ResolverCandidate(
        company = Company(
            ico = ico,
            businessName = name,
            municipality = "Olomouc",
            districtName = "Olomouc",
            foundedOn = "1991-01-01",
            czNace = listOf("28130"),
            legalForm = "112",
        ),
        score = 2.0,
        reasons = listOf("sídlo v hledané obci (Olomouc)"),
    )

    private fun ranker() = AiCandidateRanker(
        client = io.github.painter99.jobsearch.data.ai.OpenRouterClient(
            OkHttpClient(),
            server.url("/api/v1").toString(),
        ),
        promptLoader = object : PromptLoader {
            override fun load(name: String, params: Map<String, String>): String =
                PromptSubstitution.substitute(
                    "Kandidáti: {{candidates}}; profese {{profession}}; obec {{municipality}}; agentura {{agency}}",
                    params,
                )
        },
    )

    private fun aiBody(ranked: String): String =
        """{"choices":[{"message":{"content":${org.json.JSONObject.quote(ranked)}}}]}"""

    @Test
    fun `rank - success přeřadí kandidáty dle AI pořadí`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                aiBody("""{"ranked":[{"ico":"222","reason":"obor i lokalita sedí"},{"ico":"111","reason":"obor sedí"}]}"""),
            ),
        )
        val result = ranker().rank(
            apiKey = "sk",
            model = "m",
            profession = "lakýrník",
            municipality = "Olomouc",
            agencyName = "Agentura",
            candidates = listOf(candidate("111", "Alfa"), candidate("222", "Beta")),
        )
        assertTrue(result is FetchResult.Success)
        val ranked = (result as FetchResult.Success).data
        assertEquals(listOf("222", "111"), ranked.map { it.candidate.company.ico })
        assertEquals("obor i lokalita sedí", ranked[0].aiReason)
        // AI dostal kandidáty jako JSON v promptu
        val sent = org.json.JSONObject(server.takeRequest().body.readUtf8())
        val prompt = sent.getJSONArray("messages").getJSONObject(1).getString("content")
        assertTrue(prompt.contains("\"ico\""))
        assertTrue(prompt.contains("lakýrník"))
    }

    @Test
    fun `rank - AI vyfabulované IČO se zahodí (N7 obrana)`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                aiBody("""{"ranked":[{"ico":"999","reason":"vymyšlená"},{"ico":"111","reason":"ok"}]}"""),
            ),
        )
        val result = ranker().rank("sk", "m", "p", "o", "a", listOf(candidate("111", "Alfa")))
        val ranked = (result as FetchResult.Success).data
        assertEquals(listOf("111"), ranked.map { it.candidate.company.ico })
    }

    @Test
    fun `rank - chybějící reason → fallback na důvody resolveru`() = runTest {
        server.enqueue(MockResponse().setBody(aiBody("""{"ranked":[{"ico":"111"}]}""")))
        val result = ranker().rank("sk", "m", "p", "o", "a", listOf(candidate("111", "Alfa")))
        val ranked = (result as FetchResult.Success).data
        assertEquals("sídlo v hledané obci (Olomouc)", ranked[0].aiReason)
    }

    @Test
    fun `rank - AI odpověď ve fence se toleruje`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                aiBody("```json\n{\"ranked\":[{\"ico\":\"111\",\"reason\":\"fenced\"}]}\n```"),
            ),
        )
        val result = ranker().rank("sk", "m", "p", "o", "a", listOf(candidate("111", "Alfa")))
        assertEquals("fenced", (result as FetchResult.Success).data[0].aiReason)
    }

    @Test
    fun `rank - HTTP chyba propaguje, kandidáti se nezmění`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        val result = ranker().rank("sk", "m", "p", "o", "a", listOf(candidate("111", "Alfa")))
        assertEquals(FetchResult.HttpError(429), result)
    }

    @Test
    fun `rank - prázdný seznam kandidátů → ParseError bez volání sítě`() = runTest {
        val result = ranker().rank("sk", "m", "p", "o", "a", emptyList())
        assertTrue(result is FetchResult.ParseError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `rank - AI odpověď bez známého kandidáta → ParseError`() = runTest {
        server.enqueue(MockResponse().setBody(aiBody("""{"ranked":[{"ico":"999"}]}""")))
        val result = ranker().rank("sk", "m", "p", "o", "a", listOf(candidate("111", "Alfa")))
        assertTrue(result is FetchResult.ParseError)
    }
}