package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.ShiftPattern
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2: IncrementParser — parse denního přírůstku (novy/zmeneny/zruseny).
 * Fixture = živý přírůstek 2026-10-05 (8 validních záznamů + 1 neznámý typ).
 * Očekávání dle simulace na živých datech (M1.3 lekce).
 */
class IncrementParserTest {

    private fun fixtureText(): String {
        val bytes = javaClass.getResourceAsStream("/prirustek-sample.json.gz")!!.readBytes()
        return java.util.zip.GZIPInputStream(bytes.inputStream()).readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun parse_liveFixture_countsMatchSimulation() {
        val records = IncrementParser().parseText(fixtureText())

        // simulace na živých datech: 9 záznamů ve fixture, 1 neznámý typ → 8 validních
        assertEquals(8, records.size)
        assertEquals(3, records.count { it.changeType == ChangeType.NEW })
        assertEquals(3, records.count { it.changeType == ChangeType.CHANGED })
        assertEquals(2, records.count { it.changeType == ChangeType.REMOVED })
    }

    @Test
    fun parse_removedRecordsCarryPortalId() {
        val records = IncrementParser().parseText(fixtureText())
        val removed = records.filter { it.changeType == ChangeType.REMOVED }

        // zrušené nabídky nejsou tombstony — mají plný záznam (offer != null)
        assertTrue(removed.isNotEmpty())
        removed.forEach { record ->
            assertTrue(record.portalId > 0)
            assertNotNull(record.offer)
        }
    }

    @Test
    fun parse_newRecordsHaveOffers() {
        val records = IncrementParser().parseText(fixtureText())
        val new = records.filter { it.changeType == ChangeType.NEW }

        new.forEach { record ->
            assertNotNull(record.offer)
        }
        // živá data 7. 10.: urlAdresa je většinou null (1 z 8 záznamů ji nese,
        // zmeneny) — klíč ve schématu ≠ vyplněná hodnota
        assertTrue(records.any { it.offer?.url != null })
    }

    @Test
    fun parse_unknownChangeTypeSkipped() {
        val root = JSONObject(
            """
            {"polozky": [
              {"portalId": 1, "typZmenyOpenData": {"id": "TypyZmenOpenData/neznamyTyp"},
               "pozadovanaProfese": {"cs": "Cokoliv"}},
              {"portalId": 2, "typZmenyOpenData": {"id": "TypyZmenOpenData/novy"},
               "pozadovanaProfese": {"cs": "Skladník"}}
            ]}
            """.trimIndent()
        )

        val records = IncrementParser().parse(root)

        assertEquals(1, records.size)
        assertEquals(2L, records[0].portalId)
        assertEquals(ChangeType.NEW, records[0].changeType)
    }

    @Test
    fun parse_recordWithoutProfessionYieldsNullOfferButKeptForRemoval() {
        val root = JSONObject(
            """
            {"polozky": [
              {"portalId": 77, "typZmenyOpenData": {"id": "TypyZmenOpenData/zruseny"},
               "pozadovanaProfese": null}
            ]}
            """.trimIndent()
        )

        val records = IncrementParser().parse(root)

        assertEquals(1, records.size)
        assertEquals(77L, records[0].portalId)
        assertNull(records[0].offer)
    }

    @Test
    fun changeType_mapsAllMpsvCodes() {
        assertEquals(ChangeType.NEW, ChangeType.fromMpsvId("TypyZmenOpenData/novy"))
        assertEquals(ChangeType.CHANGED, ChangeType.fromMpsvId("TypyZmenOpenData/zmeneny"))
        assertEquals(ChangeType.REMOVED, ChangeType.fromMpsvId("TypyZmenOpenData/zruseny"))
        assertNull(ChangeType.fromMpsvId(null))
        assertNull(ChangeType.fromMpsvId("TypyZmenOpenData/novyNeco"))
    }

    @Test
    fun parse_tolerant_nullPolozkyAndMissingType() = runTest {
        assertEquals(emptyList<IncrementRecord>(), IncrementParser().parse(JSONObject("""{}""")))
        val root = JSONObject(
            """{"polozky": [{"portalId": 5, "typZmenyOpenData": null}]}"""
        )
        assertEquals(emptyList<IncrementRecord>(), IncrementParser().parse(root))
    }
}