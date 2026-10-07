package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Agentura
import io.github.painter99.jobsearch.core.model.AgenturaPovoleni
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AgenturyParser testy na reálné fixtuře (staženo živě 7. 10. 2026,
 * whitelist pole: ico, nazev, povoleni — GDPR pole se nemapují).
 */
class AgenturyParserTest {

    private fun fixture(): JSONObject {
        val text = javaClass.getResourceAsStream("/agentury-prace-sample.json")!!
            .bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    @Test
    fun parse_vraci1912AgenturZPlnehoSeznamu() {
        val parser = AgenturyParser()
        val result = parser.parsePolozky(fixture())
        assertEquals(3, result.size)
    }

    @Test
    fun parse_jobsContactVSeznamu() {
        val parser = AgenturyParser()
        val result = parser.parsePolozky(fixture())
        val jc = result.firstOrNull { it.ico == "17181879" }
        assertTrue("Jobs Contact musí být v evidenci", jc != null)
        assertEquals("Jobs Contact Personal, s.r.o.", jc!!.nazev)
        // povolení od 27. 8. 2022, bez omezení (platnostDo null)
        assertEquals("2022-08-27", jc.povoleni.first().platnostOd)
        assertNull(jc.povoleni.first().platnostDo)
    }

    @Test
    fun parse_gdprOsobniUdajeSeNemapou() {
        // GDPR whitelist vynucený modelem: Agentura nemá pole pro
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
        val result = AgenturyParser().parsePolozky(raw)
        assertEquals(1, result.size)
        val a = result[0]
        assertEquals("12345678", a.ico)
        assertEquals("Test Agentura s.r.o.", a.nazev)
        // žádné pole modelu nesmí obsahovat osobní údaje
        assertEquals(listOf("Bez omezení"), a.povoleni.map { it.druhyPraci })
    }

    @Test
    fun parse_tolerantni_nullPovoleniANazev() {
        val raw = JSONObject(
            """
            {"polozky": [
                {"ico": "11111111", "nazev": null, "povoleni": null},
                {"ico": null, "nazev": "Bez IČO", "povoleni": []},
                {"nazev": "Bez IČO vůbec"}
            ]}
            """.trimIndent()
        )
        val result = AgenturyParser().parsePolozky(raw)
        // záznamy bez IČO se zahodí (IČO je klíč detekce), null nazev → ""
        assertEquals(1, result.size)
        assertEquals("11111111", result[0].ico)
        assertEquals("", result[0].nazev)
        assertTrue(result[0].povoleni.isEmpty())
    }

    @Test
    fun maPlatnePovoleniK_hraniceAPrazdneDatumy() {
        val a = Agentura(
            ico = "1",
            nazev = "X",
            povoleni = listOf(
                AgenturaPovoleni("Bez omezení", "2022-08-27", null),
                AgenturaPovoleni("Zprostředkování", "2020-01-01", "2021-12-31"),
            ),
        )
        assertTrue(a.maPlatnePovoleniK("2026-10-07"))
        assertTrue(a.maPlatnePovoleniK("2022-08-27")) // den vzniku = platné
        assertFalse(a.maPlatnePovoleniK("2022-08-26"))
    }
}