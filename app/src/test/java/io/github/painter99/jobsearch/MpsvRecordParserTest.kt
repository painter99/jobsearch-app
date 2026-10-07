package io.github.painter99.jobsearch

import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.core.model.Benefit
import io.github.painter99.jobsearch.data.mpsv.MpsvRecordParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T1: Domain modely + GDPR whitelist parsing (RED fáze).
 *
 * Fixture = reálný záznam z MPSV otevřených dat (6. 10. 2026, anonymizovaný
 * jen v tom, že obsahuje pole s osobními údaji, která parser musí ignorovat).
 */
class MpsvRecordParserTest {

    // Reálná struktura z full dumpu (DHL Information Services, jednoSm, 98 300 Kč)
    private fun dhlFixture(): JSONObject = JSONObject(
        """
        {
          "portalId": 67299543,
          "referencniCislo": "35101880724",
          "pozadovanaProfese": {"cs": "Vývojáři softwaru"},
          "smennost": {"id": "Smennost/jednoSm"},
          "mesicniMzdaOd": 98300,
          "mesicniMzdaDo": null,
          "pocetHodinTydne": 40,
          "zamestnavatel": {"ico": "27080439", "nazev": "DHL Information Services (Europe) s.r.o."},
          "mistoVykonuPrace": {
            "typMistaVykonuPrace": {"id": "TypMistaVykonuPrace/obec"},
            "obec": {"id": "Obec/554782"},
            "okresy": null,
            "adresaText": null,
            "pracoviste": null
          },
          "vyhodyVolnehoMista": [
            {"vyhoda": {"id": "VyhodyVolnehoMista/dovol"}, "popis": null},
            {"vyhoda": {"id": "VyhodyVolnehoMista/strav"}, "popis": null}
          ],
          "urlAdresa": null,
          "souhlasAgenturyAgentura": false,
          "souhlasAgenturyUzivatel": false,
          "prvniKontaktSeZamestnavatelem": {
            "komuSeHlasit": {"prijmeni": "Novák", "jmeno": "Jan", "email": "jan.novak@example.com", "telefon": "+420123456789"}
          },
          "kdeSeHlasit": {"telefon": "800 123 456", "email": "kontakt@example.com"},
          "upresnujiciInformace": {"cs": "volný text s osobními údaji"}
        }
        """.trimIndent()
    )

    @Test
    fun `parses whitelist fields from real record`() {
        val offer = MpsvRecordParser().parse(dhlFixture())!!

        assertEquals(67299543L, offer.portalId)
        assertEquals("35101880724", offer.referenceNumber)
        assertEquals("Vývojáři softwaru", offer.profession)
        assertEquals(ShiftPattern.SINGLE_SHIFT, offer.shiftPattern)
        assertEquals(98300, offer.salaryFrom)
        assertNull(offer.salaryTo)
        assertEquals(40, offer.hoursPerWeek)
        assertEquals("27080439", offer.employer?.ico)
        assertEquals("DHL Information Services (Europe) s.r.o.", offer.employer?.name)
        assertEquals(WorkLocation.LocationType.MUNICIPALITY, offer.location.type)
        assertEquals("Obec/554782", offer.location.municipalityId)
        assertEquals(listOf(Benefit.EXTRA_VACATION, Benefit.CANTEEN), offer.benefits)
        assertNull(offer.url)
        assertEquals(false, offer.agencyConsent)
        assertEquals(false, offer.userConsent)
    }

    @Test
    fun `GDPR - personal data never reach the model`() {
        val offer = MpsvRecordParser().parse(dhlFixture())!!
        // Model nemá žádné pole, kam by se osobní údaje mohly dostat:
        // kontrola reflectionem — žádné pole nesmí obsahovat hodnoty z GDPR polí
        val json = JSONObject(offer) // pokus o serializaci nesmí vyhodit GDPR pole
        assertFalse(json.toString().contains("Novák"))
        assertFalse(json.toString().contains("jan.novak"))
        assertFalse(json.toString().contains("123456789"))
        assertFalse(json.toString().contains("800 123 456"))
        assertFalse(json.toString().contains("kontakt@"))
        assertFalse(json.toString().contains("volný text"))
    }

    @Test
    fun `tolerant parsing - null vs value vs missing field`() {
        val minimal = JSONObject(
            """
            {
              "portalId": 1,
              "referencniCislo": "X1",
              "pozadovanaProfese": {"cs": "Kontrolor kvality"},
              "smennost": null,
              "mistoVykonuPrace": null
            }
            """.trimIndent()
        )
        val offer = MpsvRecordParser().parse(minimal)!!
        assertNull(offer.shiftPattern)
        assertNull(offer.salaryFrom)
        assertEquals(WorkLocation.NONE, offer.location)
        assertTrue(offer.benefits.isEmpty())
    }

    @Test
    fun `all shift pattern codes from codelist map`() {
        val expectedMapping = mapOf(
            "Smennost/jednoSm" to ShiftPattern.SINGLE_SHIFT,
            "Smennost/dvouSm" to ShiftPattern.TWO_SHIFT,
            "Smennost/triSm" to ShiftPattern.THREE_SHIFT,
            "Smennost/ctyrSm" to ShiftPattern.FOUR_SHIFT,
            "Smennost/deleneSm" to ShiftPattern.SPLIT_SHIFTS,
            "Smennost/nepretrzity" to ShiftPattern.CONTINUOUS,
            "Smennost/nocni" to ShiftPattern.NIGHT_SHIFT,
            "Smennost/pruznaPd" to ShiftPattern.FLEXIBLE,
            "Smennost/turnus" to ShiftPattern.ROTATING,
            "Smennost/neurceno" to ShiftPattern.UNSPECIFIED,
        )
        expectedMapping.forEach { (id, expected) ->
            assertEquals(expected, ShiftPattern.fromMpsvId(id))
        }
        assertNull(ShiftPattern.fromMpsvId(null))
        assertNull(ShiftPattern.fromMpsvId("Smennost/neznamyKod"))
    }

    @Test
    fun `worksite (adrprov) - RUIAN municipality codes extracted`() {
        val adrprov = JSONObject(
            """
            {
              "portalId": 2,
              "referencniCislo": "X2",
              "pozadovanaProfese": {"cs": "Masér"},
              "smennost": {"id": "Smennost/jednoSm"},
              "mistoVykonuPrace": {
                "typMistaVykonuPrace": {"id": "TypMistaVykonuPrace/adrprov"},
                "obec": null,
                "okresy": null,
                "adresaText": null,
                "pracoviste": [{
                  "email": null,
                  "nazev": "Kinnaree SPA s.r.o. - Poděbrady",
                  "telefon": null,
                  "adresa": {
                    "cisloDomovni": 57,
                    "kodAdresnihoMista": 18329012,
                    "psc": "29001",
                    "kraj": {"id": "Kraj/27"},
                    "okres": {"id": "Okres/3208"},
                    "obec": {"id": "Obec/537683"},
                    "ulice": {"nazev": "Na Valech"}
                  }
                }]
              }
            }
            """.trimIndent()
        )
        val offer = MpsvRecordParser().parse(adrprov)!!
        assertEquals(WorkLocation.LocationType.WORKSITE_ADDRESS, offer.location.type)
        assertEquals(listOf("Obec/537683"), offer.location.worksiteMunicipalityIds)
    }

    @Test
    fun `districts i celaCR se mapují, Okres 9999 je placeholder`() {
        val districtFixture = JSONObject(
            """
            {
              "portalId": 3,
              "referencniCislo": "X3",
              "pozadovanaProfese": {"cs": "Řidič"},
              "smennost": {"id": "Smennost/jednoSm"},
              "mistoVykonuPrace": {
                "typMistaVykonuPrace": {"id": "TypMistaVykonuPrace/okres"},
                "obec": null,
                "okresy": [{"id": "Okres/3210"}, {"id": "Okres/9999"}],
                "adresaText": null,
                "pracoviste": null
              }
            }
            """.trimIndent()
        )
        val offer = MpsvRecordParser().parse(districtFixture)!!
        assertEquals(listOf("Okres/3210", "Okres/9999"), offer.location.districts)
    }

    @Test
    fun `unknown benefit code does not break parsing`() {
        val withUnknownCode = JSONObject(
            """
            {
              "portalId": 4,
              "referencniCislo": "X4",
              "pozadovanaProfese": {"cs": "Skladník"},
              "smennost": {"id": "Smennost/jednoSm"},
              "vyhodyVolnehoMista": [
                {"vyhoda": {"id": "VyhodyVolnehoMista/premie"}, "popis": null},
                {"vyhoda": {"id": "VyhodyVolnehoMista/budouciKod"}, "popis": null}
              ]
            }
            """.trimIndent()
        )
        val offer = MpsvRecordParser().parse(withUnknownCode)!!
        assertEquals(listOf(Benefit.SPECIAL_BONUS), offer.benefits)
    }
}