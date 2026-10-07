package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Agency
import io.github.painter99.jobsearch.core.model.AgencyPermit
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AgencyParser testy na reálné fixtuře (staženo živě 7. 10. 2026,
 * whitelist pole: ico, name, permits — GDPR pole se nemapují).
 */
class AgencyParserTest {

    private fun fixture(): JSONObject {
        val text = javaClass.getResourceAsStream("/agentury-prace-sample.json")!!
            .bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    @Test
    fun parse_returnsAllItemsFromFixture() {
        val parser = AgencyParser()
        val result = parser.parseItems(fixture())
        assertEquals(3, result.size)
    }

    @Test
    fun parse_jobsContactInList() {
        val parser = AgencyParser()
        val result = parser.parseItems(fixture())
        val jc = result.firstOrNull { it.ico == "17181879" }
        assertTrue("Jobs Contact musí být v evidenci", jc != null)
        assertEquals("Jobs Contact Personal, s.r.o.", jc!!.name)
        // povolení od 27. 8. 2022, bez omezení (validTo null)
        assertEquals("2022-08-27", jc.permits.first().validFrom)
        assertNull(jc.permits.first().validTo)
    }

    @Test
    fun parse_gdprPersonalDataNeverMapped() {
        // GDPR whitelist vynucený modelem: Agency nemá pole pro
        // odpovednyZastupce/kontaktniOsoby — test dokládá, že parser
        // nezachovává žádný string obsahující jméno zástupce.
        val raw = JSONObject(
            """
            {"polozky": [{
                "ico": "12345678",
                "nazev": "Test Agentura s.r.o.",
                "odpovednyZastupce": "Jan Novák",
                "kontaktniOsoby": [{"jmeno": "Jan", "prijmeni": "Novák"}],
                "povoleni": [{"druhyPraci": {"cs": "Bez omezení"},
                              "platnostOd": "2020-01-01", "platnostDo": null}]
            }]}
            """.trimIndent()
        )
        val result = AgencyParser().parseItems(raw)
        assertEquals(1, result.size)
        val a = result[0]
        assertEquals("12345678", a.ico)
        assertEquals("Test Agentura s.r.o.", a.name)
        // žádné pole modelu nesmí obsahovat osobní údaje
        assertEquals(listOf("Bez omezení"), a.permits.map { it.workTypes })
    }

    @Test
    fun parse_tolerant_nullPermitsAndName() {
        val raw = JSONObject(
            """
            {"polozky": [
                {"ico": "11111111", "nazev": null, "povoleni": null},
                {"ico": null, "nazev": "Bez IČO", "povoleni": []},
                {"nazev": "Bez IČO vůbec"}
            ]}
            """.trimIndent()
        )
        val result = AgencyParser().parseItems(raw)
        // záznamy bez IČO se zahodí (IČO je klíč detekce), null name → ""
        assertEquals(1, result.size)
        assertEquals("11111111", result[0].ico)
        assertEquals("", result[0].name)
        assertTrue(result[0].permits.isEmpty())
    }

    @Test
    fun hasValidPermitOn_boundaryAndEmptyDates() {
        val a = Agency(
            ico = "1",
            name = "X",
            permits = listOf(
                AgencyPermit("Bez omezení", "2022-08-27", null),
                AgencyPermit("Zprostředkování", "2020-01-01", "2021-12-31"),
            ),
        )
        assertTrue(a.hasValidPermitOn("2026-10-07"))
        assertTrue(a.hasValidPermitOn("2022-08-27")) // den vzniku = platné
        assertFalse(a.hasValidPermitOn("2022-08-26"))
    }
}