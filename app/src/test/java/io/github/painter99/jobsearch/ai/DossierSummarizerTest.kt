package io.github.painter99.jobsearch.ai

import io.github.painter99.jobsearch.core.model.Benefit
import io.github.painter99.jobsearch.core.model.DossierVerdict
import io.github.painter99.jobsearch.core.model.Employer
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.ui.dossier.ChecklistRow
import io.github.painter99.jobsearch.ui.dossier.ChecklistTemplate
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
 * T4: DossierSummarizer — serializace dossieru do promptu (bez osobních
 * údajů), parse {"summary"}, propagace chyb.
 */
class DossierSummarizerTest {

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

    private fun summarizer() = DossierSummarizer(
        client = io.github.painter99.jobsearch.data.ai.OpenRouterClient(
            OkHttpClient(),
            server.url("/api/v1").toString(),
        ),
        promptLoader = object : PromptLoader {
            override fun load(name: String, params: Map<String, String>): String =
                PromptSubstitution.substitute("Dossier: {{dossier}}", params)
        },
    )

    private fun offer() = JobOffer(
        portalId = 1L,
        referenceNumber = "ref-1",
        profession = "Lakýrník",
        shiftPattern = ShiftPattern.SINGLE_SHIFT,
        salaryFrom = 45_000,
        salaryTo = null,
        hoursPerWeek = 40,
        employer = Employer(ico = "12345678", name = "Firma"),
        location = WorkLocation.NONE,
        benefits = listOf(Benefit.EXTRA_VACATION),
        url = null,
        agencyConsent = null,
        userConsent = null,
    )

    private fun checklist(): List<ChecklistRow> =
        ChecklistTemplate.items.map { ChecklistRow(item = it, checked = true, autoFilled = true) }

    private fun aiBody(summary: String): String =
        """{"choices":[{"message":{"content":${org.json.JSONObject.quote(summary)}}}]}"""

    @Test
    fun `summarize - success vrací summary`() = runTest {
        server.enqueue(MockResponse().setBody(aiBody("• Mzda sedí\n• Ověřit recenze")))
        val result = summarizer().summarize(
            apiKey = "sk",
            model = "m",
            offer = offer(),
            checklist = checklist(),
            verdict = DossierVerdict.OPEN,
            notes = "Zavolat pondělí",
            agencyName = "Agentura X",
            candidates = emptyList(),
        )
        assertEquals("• Mzda sedí\n• Ověřit recenze", (result as FetchResult.Success).data)
        // prompt obsahuje klíčová data dossieru
        val sent = org.json.JSONObject(server.takeRequest().body.readUtf8())
        val prompt = sent.getJSONArray("messages").getJSONObject(1).getString("content")
        assertTrue(prompt.contains("Lakýrník"))
        assertTrue(prompt.contains("Zavolat pondělí"))
        assertTrue(prompt.contains("Agentura X"))
    }

    @Test
    fun `summarize - summary ve fence se toleruje`() = runTest {
        server.enqueue(
            MockResponse().setBody(aiBody("```json\n{\"summary\":\"• bod\"}\n```")),
        )
        val result = summarizer().summarize("sk", "m", offer(), checklist(), DossierVerdict.GO, "", null, emptyList())
        assertEquals("• bod", (result as FetchResult.Success).data)
    }

    @Test
    fun `summarize - chybí summary pole → ParseError`() = runTest {
        server.enqueue(MockResponse().setBody(aiBody("""{"jine":"x"}""")))
        val result = summarizer().summarize("sk", "m", offer(), checklist(), DossierVerdict.GO, "", null, emptyList())
        assertTrue(result is FetchResult.ParseError)
    }

    @Test
    fun `summarize - HTTP chyba propaguje`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = summarizer().summarize("sk", "m", offer(), checklist(), DossierVerdict.GO, "", null, emptyList())
        assertEquals(FetchResult.HttpError(401), result)
    }

    @Test
    fun `dossierJson - neobsahuje URL ani souhlasy (jen data k rozhodnutí)`() {
        val json = summarizer().dossierJson(
            offer = offer(),
            checklist = checklist(),
            verdict = DossierVerdict.OPEN,
            notes = "n",
            agencyName = null,
            candidates = emptyList(),
        )
        assertTrue(json.contains("Lakýrník"))
        assertTrue(json.contains("jednosměnná"))
        assertTrue(json.contains("Mzda odpovídá látce"))
    }
}