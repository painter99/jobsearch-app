package io.github.painter99.jobsearch

import io.github.painter99.jobsearch.core.model.MistoVykonu
import io.github.painter99.jobsearch.core.model.Nabidka
import io.github.painter99.jobsearch.core.model.Smennost
import io.github.painter99.jobsearch.core.model.Vyhoda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T4: Výhody → checklist auto-fill (Pavlova latka strojově).
 * dovol (Dovolená navíc) + premie (Zvláštní prémie) = signály pro auto-fill.
 */
class VyhodyChecklistTest {

    private fun nabidka(vyhody: List<Vyhoda>) = Nabidka(
        portalId = 1,
        referencniCislo = "X",
        profese = "Test",
        smennost = Smennost.JEDNOSMENNA,
        mzdaOd = 45_000,
        mzdaDo = null,
        pocetHodinTydne = null,
        zamestnavatel = null,
        misto = MistoVykonu.NIC,
        vyhody = vyhody,
        urlAdresa = null,
        agenturaSouhlas = null,
        uzivatelSouhlas = null,
    )

    @Test
    fun `dovol + premie = auto-fill signály latky`() {
        val n = nabidka(listOf(Vyhoda.DOVOL, Vyhoda.PREMIE))
        assertTrue("dovol → dovolená navíc", n.vyhody.contains(Vyhoda.DOVOL))
        assertTrue("premie → zvláštní prémie", n.vyhody.contains(Vyhoda.PREMIE))
        assertTrue(n.maDovolenouNavic())
        assertTrue(n.maZvlastniPremie())
    }

    @Test
    fun `bez dovol a premie - žádné signály`() {
        val n = nabidka(listOf(Vyhoda.STRAV, Vyhoda.JIZDNE))
        assertFalse(n.maDovolenouNavic())
        assertFalse(n.maZvlastniPremie())
    }

    @Test
    fun `prázdné výhody - žádné signály`() {
        val n = nabidka(emptyList())
        assertFalse(n.maDovolenouNavic())
        assertFalse(n.maZvlastniPremie())
    }

    @Test
    fun `všechny kódy výhod z číselníku mapují na české názvy`() {
        val ocekavane = mapOf(
            "ubyt" to "Ubytování",
            "predsk" to "Předškolní zařízení",
            "natur" to "Naturální výhody",
            "jizdne" to "Jízdní výhody",
            "jine" to "Jiné výhody",
            "strav" to "Podnikové stravování",
            "dovol" to "Dovolená navíc",
            "premie" to "Zvláštní prémie",
            "mimo" to "Mimo okres bydliště",
            "zahr" to "V zahraničí",
        )
        assertEquals(ocekavane.size, Vyhoda.entries.size)
        Vyhoda.entries.forEach { v ->
            assertEquals("kód ${v.mpsvKod}", ocekavane[v.mpsvKod], v.ceskyNazev)
        }
    }
}