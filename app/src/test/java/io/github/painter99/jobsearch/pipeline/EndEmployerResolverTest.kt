package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.Company
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EndEmployerResolver scoring testy — oracle = Python prototyp
 * (`agency_resolver.py::resolvuj_koncovou_firmu`, živý běh 7. 10. 2026:
 * NACE 28130 + Olomouc → ISH PUMPS 4.0 / Edwards 4.0 / HOSYN 3.0).
 * N7: výstup = kandidáti se skóre a zdůvodněním, nikdy fakt.
 */
class EndEmployerResolverTest {

    private fun company(
        ico: String,
        name: String,
        municipality: String? = null,
        districtName: String? = null,
        foundedOn: String? = null,
        czNace: List<String> = emptyList(),
        legalForm: String? = null,
    ) = Company(
        ico = ico,
        businessName = name,
        municipality = municipality,
        districtName = districtName,
        foundedOn = foundedOn,
        czNace = czNace,
        legalForm = legalForm,
    )

    private val hints = EmployerHints(
        naceCodes = listOf("28130"),
        municipalityName = "Olomouc",
        districtName = "Olomouc",
        familyBusiness = true,
    )

    @Test
    fun `oracle - ISH PUMPS score 4,0 (obec + NACE) nad živou fixturou 28130`() {
        // živá odpověď ARES vyhledat (czNace 28130) z 7. 10. 2026 — 44 firem
        val text = javaClass.getResourceAsStream("/ares-vyhledat-28130.json")!!
            .bufferedReader().use { it.readText() }
        val root = org.json.JSONObject(text)
        val companies = root.optJSONArray("ekonomickeSubjekty")!!.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { s ->
                    val sidlo = s.optJSONObject("sidlo")
                    Company(
                        ico = s.optString("ico"),
                        businessName = s.optString("obchodniJmeno"),
                        municipality = sidlo?.optString("nazevObce"),
                        districtName = sidlo?.optString("nazevOkresu"),
                        foundedOn = s.optStringOrNull("datumVzniku"),
                        czNace = s.optJSONArray("czNace")?.let { a ->
                            (0 until a.length()).map { a.optString(it) }
                        } ?: emptyList(),
                        legalForm = s.optString("pravniForma"),
                    )
                }
            }
        }
        assertEquals(44, companies.size)

        val candidates = EndEmployerResolver(deadAres).score(companies, hints)

        // ISH PUMPS = top kandidát (sídlo Olomouc + NACE), score 4.0 jako v oracle
        val ish = candidates.first { it.company.ico == "25272365" }
        assertEquals(4.0, ish.score, 0.001)
        assertTrue(ish.reasons.any { it.contains("sídlo v hledané obci") })
        assertTrue(ish.reasons.any { it.contains("NACE") })
        // Edwards, s.r.o. (Lutín, okres Olomouc) = okres + NACE + rodinná firma = 4.0
        val edwards = candidates.first { it.company.ico == "26461498" }
        assertEquals(4.0, edwards.score, 0.001)
        assertTrue(edwards.reasons.any { it.contains("okres") })
        assertTrue(edwards.reasons.any { it.contains("rodinnou firmu") })
        // řazení: sestupně podle skóre
        assertTrue(candidates.first().score >= candidates.last().score)
    }

    @Test
    fun `obec shoda +2, okres shoda +1 (bez obce)`() {
        val c = company("1", "A s.r.o.", municipality = "Olomouc", districtName = "Olomouc", czNace = listOf("28130"))
        val r = EndEmployerResolver(deadAres).score(listOf(c), hints)
        assertEquals(1, r.size)
        assertEquals(4.0, r[0].score, 0.001) // obec 2 + NACE 2 (okres se nepřičítá, když sedí obec)
        assertEquals(listOf("sídlo v hledané obci (Olomouc)", "hlavní NACE odpovídá oboru (28130)"), r[0].reasons)
    }

    @Test
    fun `okres-only shoda +1, obec mimo`() {
        val c = company("2", "B s.r.o.", municipality = "Hranice", districtName = "Přerov", czNace = listOf("28130"))
        val r = EndEmployerResolver(deadAres).score(listOf(c), hints)
        assertEquals(1, r.size)
        assertEquals(3.0, r[0].score, 0.001) // okres 1 + NACE 2
    }

    @Test
    fun `rodinná firma +1 jen pro známé právní formy`() {
        val sro = company("3", "C s.r.o.", municipality = "Olomouc", czNace = listOf("28130"), legalForm = "112")
        val as_ = company("4", "D a.s.", municipality = "Olomouc", czNace = listOf("28130"), legalForm = "121")
        val osvc = company("5", "E", municipality = "Olomouc", czNace = listOf("28130"), legalForm = "70653")
        val akciovaNeznama = company("6", "F a.s.", municipality = "Olomouc", czNace = listOf("28130"), legalForm = "999")
        val r = EndEmployerResolver(deadAres).score(listOf(sro, as_, osvc, akciovaNeznama), hints)
        assertEquals(4.0, r.first { it.company.ico == "3" }.score, 0.001)
        assertEquals(4.0, r.first { it.company.ico == "4" }.score, 0.001)
        assertEquals(4.0, r.first { it.company.ico == "5" }.score, 0.001)
        assertEquals(4.0, r.first { it.company.ico == "6" }.score, 0.001) // neznámá forma → bonus nepřidá, ale obec+NACE = 4.0? NE — obec 2 + NACE 2 = 4.0
        // kontrola důvodů: neznámá forma NEMÁ rodinný důvod
        assertTrue(r.first { it.company.ico == "6" }.reasons.none { it.contains("rodinnou firmu") })
        assertTrue(r.first { it.company.ico == "3" }.reasons.any { it.contains("rodinnou firmu") })
    }

    @Test
    fun `dlouhá historie +0,5 při foundedUntilYear`() {
        val old = company("7", "G s.r.o.", municipality = "Olomouc", czNace = listOf("28130"), foundedOn = "2005-01-01")
        val new = company("8", "H s.r.o.", municipality = "Olomouc", czNace = listOf("28130"), foundedOn = "2024-06-01")
        val h = hints.copy(foundedUntilYear = 2010)
        val r = EndEmployerResolver(deadAres).score(listOf(old, new), h)
        assertEquals(4.5, r.first { it.company.ico == "7" }.score, 0.001)
        assertEquals(4.0, r.first { it.company.ico == "8" }.score, 0.001)
    }

    @Test
    fun `score 0 se zahazuje - NACE mimo zájem`() {
        val c = company("9", "I s.r.o.", municipality = "Brno", districtName = "Brno-město", czNace = listOf("62010"))
        val r = EndEmployerResolver(deadAres).score(listOf(c), hints)
        assertTrue(r.isEmpty())
    }

    @Test
    fun `maxCandidates omezuje výstup a řazení je deterministické`() {
        val cs = (1..10).map { i ->
            company("$i", "Firma $i", municipality = "Olomouc", czNace = listOf("28130"), legalForm = "112")
        }
        val r = EndEmployerResolver(deadAres, maxCandidates = 3).score(cs, hints)
        assertEquals(3, r.size)
        // všechny mají stejné skóre 5.0 → sekundární řazení dle businessName (abecedně)
        assertEquals(listOf("Firma 1", "Firma 10", "Firma 2"), r.map { it.company.businessName })
    }

    @Test
    fun `N7 - kandidát vždy nese reasons a score (nikdy fakt)`() {
        val c = company("10", "J s.r.o.", municipality = "Olomouc", czNace = listOf("28130"))
        val r = EndEmployerResolver(deadAres).score(listOf(c), hints)
        assertEquals(1, r.size)
        assertTrue(r[0].reasons.isNotEmpty())
        assertTrue(r[0].score > 0.0)
    }

    /**
     * Nepoužitá instance AresClientu — scoring testy volají jen čistou
     * funkci score() a síť nikdy nevolají (fixture/oracle first). Instanci
     * nelze nahradit stubem (třída je final), síťová adresa je mrtvá
     * (127.0.0.1:1) — kdyby test omylem volal síť, test selže.
     */
    private val deadAres = io.github.painter99.jobsearch.data.ares.AresClient(
        client = okhttp3.OkHttpClient(),
        baseUrl = "http://127.0.0.1:1/stub",
    )
}

/** Tolerantní helper pro test fixture (null vs. JSONObject.NULL). */
private fun org.json.JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }