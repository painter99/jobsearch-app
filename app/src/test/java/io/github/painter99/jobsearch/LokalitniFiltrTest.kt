package io.github.painter99.jobsearch

import io.github.painter99.jobsearch.core.filter.LokalitniFiltr
import io.github.painter99.jobsearch.core.model.MistoVykonu
import io.github.painter99.jobsearch.core.model.Nabidka
import io.github.painter99.jobsearch.core.model.Smennost
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3: Matching lokality — strategie z top 3 robustness rešerše:
 * pracoviste (RÚIAN) → obec.id → okresy → adresaText (poslední);
 * celaCR vyhovuje vždy; Okres/9999 = placeholder (nikdy nematchuje).
 */
class LokalitniFiltrTest {

    private val olomouc = setOf("Obec/554782")
    private val olomouckyOkres = setOf("Okres/3806")

    private fun nabidka(misto: MistoVykonu) = Nabidka(
        portalId = 1,
        referencniCislo = "X",
        profese = "Test",
        smennost = Smennost.JEDNOSMENNA,
        mzdaOd = 45_000,
        mzdaDo = null,
        pocetHodinTydne = null,
        zamestnavatel = null,
        misto = misto,
        vyhody = emptyList(),
        urlAdresa = null,
        agenturaSouhlas = null,
        uzivatelSouhlas = null,
    )

    @Test
    fun `typ obec - obecId v profilu vyhovuje`() {
        val misto = MistoVykonu(MistoVykonu.TypMistaVykonu.OBEC, "Obec/554782", emptyList(), null, emptyList())
        assertTrue(LokalitniFiltr.vyhovuje(nabidka(misto), olomouc, olomouckyOkres))
    }

    @Test
    fun `typ obec - jiná obec NEvyhovuje`() {
        val misto = MistoVykonu(MistoVykonu.TypMistaVykonu.OBEC, "Obec/537683", emptyList(), null, emptyList())
        assertFalse(LokalitniFiltr.vyhovuje(nabidka(misto), olomouc, olomouckyOkres))
    }

    @Test
    fun `typ adrprov - pracovisteObecIds matchují`() {
        val misto = MistoVykonu(
            MistoVykonu.TypMistaVykonu.ADRESA_PRACOVISTE, null, emptyList(), null,
            listOf("Obec/537683", "Obec/554782"),
        )
        assertTrue(LokalitniFiltr.vyhovuje(nabidka(misto), olomouc, olomouckyOkres))
    }

    @Test
    fun `typ okres - okres v profilu vyhovuje`() {
        val misto = MistoVykonu(MistoVykonu.TypMistaVykonu.OKRES, null, listOf("Okres/3806", "Okres/9999"), null, emptyList())
        assertTrue(LokalitniFiltr.vyhovuje(nabidka(misto), olomouc, olomouckyOkres))
    }

    @Test
    fun `typ okres - Okres 9999 placeholder nikdy nematchuje`() {
        val misto = MistoVykonu(MistoVykonu.TypMistaVykonu.OKRES, null, listOf("Okres/9999"), null, emptyList())
        assertFalse(LokalitniFiltr.vyhovuje(nabidka(misto), olomouc, olomouckyOkres))
    }

    @Test
    fun `typ celaCR vyhovuje vždy`() {
        val misto = MistoVykonu(MistoVykonu.TypMistaVykonu.CELA_CR, null, emptyList(), null, emptyList())
        assertTrue(LokalitniFiltr.vyhovuje(nabidka(misto), olomouc, olomouckyOkres))
        assertTrue(LokalitniFiltr.vyhovuje(nabidka(misto), emptySet(), emptySet()))
    }

    @Test
    fun `typ adrvolna - adresaText jako poslední fallback`() {
        val misto = MistoVykonu(MistoVykonu.TypMistaVykonu.ADRESA_VOLNA, "Obec/567027", emptyList(), "Most, Litvínov", emptyList())
        // obecId není v profilu, ale adresaText obsahuje hledaný text
        val profilText = setOf("most")
        assertTrue(LokalitniFiltr.vyhovuje(nabidka(misto), emptySet(), emptySet(), profilText))
    }

    @Test
    fun `typ neurceno NEvyhovuje (nemáme co matchovat)`() {
        val misto = MistoVykonu(MistoVykonu.TypMistaVykonu.NEURCENO, null, emptyList(), null, emptyList())
        assertFalse(LokalitniFiltr.vyhovuje(nabidka(misto), olomouc, olomouckyOkres))
    }
}