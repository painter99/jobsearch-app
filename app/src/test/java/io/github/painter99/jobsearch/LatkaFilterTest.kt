package io.github.painter99.jobsearch

import io.github.painter99.jobsearch.core.filter.Latka
import io.github.painter99.jobsearch.core.filter.vyhovujeLatce
import io.github.painter99.jobsearch.core.model.MistoVykonu
import io.github.painter99.jobsearch.core.model.Nabidka
import io.github.painter99.jobsearch.core.model.Smennost
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2: Pavlova latka — tvrdé filtry (směna + mzda).
 * Oracle: Python prototyp filters.py (mzda_od >= minimum, neuvedeno → NEvyhovuje).
 */
class LatkaFilterTest {

    private val latka = Latka(minMzdaKc = 40_000, jenJednosmenna = true)

    private fun nabidka(
        smennost: Smennost?,
        mzdaOd: Int?,
    ) = Nabidka(
        portalId = 1,
        referencniCislo = "X",
        profese = "Test",
        smennost = smennost,
        mzdaOd = mzdaOd,
        mzdaDo = null,
        pocetHodinTydne = null,
        zamestnavatel = null,
        misto = MistoVykonu.NIC,
        vyhody = emptyList(),
        urlAdresa = null,
        agenturaSouhlas = null,
        uzivatelSouhlas = null,
    )

    @Test
    fun `boundary mzdy - 39999 NEvyhovuje, 40000 vyhovuje`() {
        assertFalse(nabidka(Smennost.JEDNOSMENNA, 39_999).vyhovujeLatce(latka))
        assertTrue(nabidka(Smennost.JEDNOSMENNA, 40_000).vyhovujeLatce(latka))
    }

    @Test
    fun `neuvedená mzda NEvyhovuje (jako v prototypu)`() {
        assertFalse(nabidka(Smennost.JEDNOSMENNA, null).vyhovujeLatce(latka))
    }

    @Test
    fun `jiná směnnost NEvyhovuje při jenJednosmenna`() {
        Smennost.entries.filter { it != Smennost.JEDNOSMENNA }.forEach { s ->
            assertFalse("směnnost $s má vyřadit", nabidka(s, 50_000).vyhovujeLatce(latka))
        }
    }

    @Test
    fun `neuvedená směnnost NEvyhovuje`() {
        assertFalse(nabidka(null, 50_000).vyhovujeLatce(latka))
    }

    @Test
    fun `vypnutý směnnostní filtr projde i třísměnnou`() {
        val bezSmen = Latka(minMzdaKc = 40_000, jenJednosmenna = false)
        assertTrue(nabidka(Smennost.TRISMENNA, 50_000).vyhovujeLatce(bezSmen))
        assertTrue(nabidka(null, 50_000).vyhovujeLatce(bezSmen))
    }
}