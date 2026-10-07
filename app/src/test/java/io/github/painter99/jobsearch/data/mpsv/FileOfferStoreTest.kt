package io.github.painter99.jobsearch.data.mpsv

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * T3: FileOfferStore — bootstrap import + applyIncrement + perzistence.
 * GDPR test: fixture bootstrap-sample.json obsahuje osobní údaje
 * (syntetické) — po importu se NESMÍ objevit v offers souboru.
 */
class FileOfferStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fixture(name: String): File {
        val bytes = javaClass.getResourceAsStream("/$name")!!.readBytes()
        return tmp.newFile(name).apply { writeBytes(bytes) }
    }

    private fun store(): FileOfferStore = FileOfferStore(File(tmp.root, "offers.json"))

    @Test
    fun importBootstrap_parsesAllRecords() = runTest {
        val store = store()

        val imported = store.importBootstrap(fixture("bootstrap-sample.json"))

        assertEquals(3, imported)
        assertEquals(3, store.count())
    }

    @Test
    fun importBootstrap_offerFieldsRoundTrip() = runTest {
        val store = store()
        store.importBootstrap(fixture("bootstrap-sample.json"))

        val dhl = store.findByPortalId(67299543L)
        assertNotNull(dhl)
        assertEquals("Vývojáři softwaru", dhl!!.profession)
        assertEquals(ShiftPattern.SINGLE_SHIFT, dhl.shiftPattern)
        assertEquals(98300, dhl.salaryFrom)
        assertEquals("27080439", dhl.employer?.ico)
        assertEquals("Obec/554782", dhl.location.municipalityId)
        assertTrue(dhl.hasExtraVacation())

        val sigma = store.findByPortalId(67299001L)
        assertNotNull(sigma)
        // pracoviste (adrprov) → RÚIAN kódy obcí
        assertEquals(listOf("Obec/503657", "Obec/502511"), sigma!!.location.worksiteMunicipalityIds)
        assertEquals("https://portal.mpsv.cz/67299001", sigma.url)

        val minimal = store.findByPortalId(67299002L)
        assertNotNull(minimal) // záznam bez smennost/mzda/misto — modelovatelný
        assertNull(minimal!!.shiftPattern)
    }

    @Test
    fun importBootstrap_gdprPersonalDataNeverWrittenToFile() = runTest {
        val store = store()
        store.importBootstrap(fixture("bootstrap-sample.json"))

        val written = File(tmp.root, "offers.json").readText()
        // fixture obsahuje syntetické osobní údaje — do souboru se dostat nesmí
        assertTrue("offers.json obsahuje prijmeni!", !written.contains("Novák"))
        assertTrue("offers.json obsahuje email!", !written.contains("jan.novak@example.com"))
        assertTrue("offers.json obsahuje telefon!", !written.contains("+420123456789"))
        assertTrue("offers.json obsahuje kdeSeHlasit!", !written.contains("800 123 456"))
        assertTrue("offers.json obsahuje upresnujici text!", !written.contains("volný text"))
        assertTrue("offers.json obsahuje GDPR klíče!", !written.contains("prvniKontakt"))
        assertTrue("offers.json obsahuje GDPR klíče!", !written.contains("kdeSeHlasit"))
        assertTrue("offers.json obsahuje GDPR klíče!", !written.contains("upresnujiciInformace"))
    }

    @Test
    fun applyIncrement_newOfferInserted() = runTest {
        val store = store()
        store.importBootstrap(fixture("bootstrap-sample.json"))
        val newOffer = IncrementParser().parseText(
            """
            {"polozky": [
              {"portalId": 67299010, "typZmenyOpenData": {"id": "TypyZmenOpenData/novy"},
               "pozadovanaProfese": {"cs": "Lakýrník"}, "smennost": {"id": "Smennost/jednoSm"},
               "mesicniMzdaOd": 42000, "urlAdresa": "https://portal.mpsv.cz/67299010"}
            ]}
            """.trimIndent()
        )

        val changed = store.applyIncrement(newOffer)

        assertEquals(1, changed)
        assertEquals(4, store.count())
        val inserted = store.findByPortalId(67299010L)
        assertNotNull(inserted)
        assertEquals("Lakýrník", inserted!!.profession)
        assertEquals(42000, inserted.salaryFrom)
    }

    @Test
    fun applyIncrement_changedOfferUpdated() = runTest {
        val store = store()
        store.importBootstrap(fixture("bootstrap-sample.json"))
        val changed = IncrementParser().parseText(
            """
            {"polozky": [
              {"portalId": 67299001, "typZmenyOpenData": {"id": "TypyZmenOpenData/zmeneny"},
               "pozadovanaProfese": {"cs": "Operátor výroby"}, "smennost": {"id": "Smennost/jednoSm"},
               "mesicniMzdaOd": 47000, "zamestnavatel": {"ico": "64608212", "nazev": "SIGMA 1868 spol. s r.o."},
               "mistoVykonuPrace": {"typMistaVykonuPrace": {"id": "TypMistaVykonuPrace/obec"},
                 "obec": {"id": "Obec/503657"}, "okresy": null, "adresaText": null, "pracoviste": null}}
            ]}
            """.trimIndent()
        )

        val applied = store.applyIncrement(changed)

        assertEquals(1, applied)
        assertEquals(3, store.count())
        val updated = store.findByPortalId(67299001L)
        assertNotNull(updated)
        assertEquals(47000, updated!!.salaryFrom)
        assertEquals(ShiftPattern.SINGLE_SHIFT, updated.shiftPattern)
        assertEquals("Obec/503657", updated.location.municipalityId)
    }

    @Test
    fun applyIncrement_removedOfferDeleted() = runTest {
        val store = store()
        store.importBootstrap(fixture("bootstrap-sample.json"))
        val removal = IncrementParser().parseText(
            """
            {"polozky": [
              {"portalId": 67299001, "typZmenyOpenData": {"id": "TypyZmenOpenData/zruseny"},
               "pozadovanaProfese": {"cs": "Operátor výroby"}}
            ]}
            """.trimIndent()
        )

        val applied = store.applyIncrement(removal)

        assertEquals(1, applied)
        assertEquals(2, store.count())
        assertNull(store.findByPortalId(67299001L))
    }

    @Test
    fun applyIncrement_liveFixture_appliesAllValidRecords() = runTest {
        val store = store()
        store.importBootstrap(fixture("bootstrap-sample.json"))
        val gz = javaClass.getResourceAsStream("/prirustek-sample.json.gz")!!.readBytes()
        val text = java.util.GZIPInputStream(gz.inputStream()).readBytes().toString(Charsets.UTF_8)
        val records = IncrementParser().parseText(text)

        // živý přírůstek: 8 validních záznamů (3 novy + 3 zmeneny + 2 zruseny)
        val applied = store.applyIncrement(records)

        // 3 NEW insert + 3 CHANGED update (portalId neexistují v bootstrapu → insert) + 2 REMOVED (neexistují → 0)
        assertEquals(6, applied)
        assertEquals(9, store.count())
    }

    @Test
    fun persistence_survivesRestart() = runTest {
        val first = store()
        first.importBootstrap(fixture("bootstrap-sample.json"))

        // nová instance = "restart procesu" — data musí přežít na disku
        val second = FileOfferStore(File(tmp.root, "offers.json"))
        val loaded = second.load()

        assertEquals(3, loaded)
        assertEquals(3, second.count())
        assertNotNull(second.findByPortalId(67299543L))
    }

    @Test
    fun all_returnsEveryOffer() = runTest {
        val store = store()
        store.importBootstrap(fixture("bootstrap-sample.json"))

        val offers = store.all()

        assertEquals(3, offers.size)
        assertTrue(offers.any { it.profession == "Kontrolor kvality" })
    }

    @Test
    fun applyIncrement_emptyBatchChangesNothing() = runTest {
        val store = store()
        store.importBootstrap(fixture("bootstrap-sample.json"))

        val applied = store.applyIncrement(emptyList())

        assertEquals(0, applied)
        assertEquals(3, store.count())
    }
}