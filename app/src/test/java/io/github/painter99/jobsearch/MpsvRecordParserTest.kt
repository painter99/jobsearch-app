package io.github.painter99.jobsearch

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
    fun `parsuje whitelist pole z reálného záznamu`() {
        val nabidka = MpsvRecordParser().parse(dhlFixture())!!

        assertEquals(67299543L, nabidka.portalId)
        assertEquals("35101880724", nabidka.referencniCislo)
        assertEquals("Vývojáři softwaru", nabidka.profese)
        assertEquals(Smennost.JEDNOSMENNA, nabidka.smennost)
        assertEquals(98300, nabidka.mzdaOd)
        assertNull(nabidka.mzdaDo)
        assertEquals(40, nabidka.pocetHodinTydne)
        assertEquals("27080439", nabidka.zamestnavatel?.ico)
        assertEquals("DHL Information Services (Europe) s.r.o.", nabidka.zamestnavatel?.nazev)
        assertEquals(MistoVykonu.TypMistaVykonu.OBEC, nabidka.misto.typ)
        assertEquals("Obec/554782", nabidka.misto.obecId)
        assertEquals(listOf(Vyhoda.DOVOL, Vyhoda.STRAV), nabidka.vyhody)
        assertNull(nabidka.urlAdresa)
        assertEquals(false, nabidka.agenturaSouhlas)
        assertEquals(false, nabidka.uzivatelSouhlas)
    }

    @Test
    fun `GDPR - osobní údaje se do modelu nedostanou`() {
        val nabidka = MpsvRecordParser().parse(dhlFixture())!!
        // Model nemá žádné pole, kam by se osobní údaje mohly dostat:
        // kontrola reflectionem — žádné pole nesmí obsahovat hodnoty z GDPR polí
        val json = JSONObject(nabidka) // pokus o serializaci nesmí vyhodit GDPR pole
        assertFalse(json.toString().contains("Novák"))
        assertFalse(json.toString().contains("jan.novak"))
        assertFalse(json.toString().contains("123456789"))
        assertFalse(json.toString().contains("800 123 456"))
        assertFalse(json.toString().contains("kontakt@"))
        assertFalse(json.toString().contains("volný text"))
    }

    @Test
    fun `tolerantní parsing - null vs hodnota vs chybějící pole`() {
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
        val nabidka = MpsvRecordParser().parse(minimal)!!
        assertNull(nabidka.smennost)
        assertNull(nabidka.mzdaOd)
        assertEquals(MistoVykonu.NIC, nabidka.misto)
        assertTrue(nabidka.vyhody.isEmpty())
    }

    @Test
    fun `všechny směnnosti z číselníku mapují`() {
        val mapovani = mapOf(
            "Smennost/jednoSm" to Smennost.JEDNOSMENNA,
            "Smennost/dvouSm" to Smennost.DVOUSMENNA,
            "Smennost/triSm" to Smennost.TRISMENNA,
            "Smennost/ctyrSm" to Smennost.CTYRSMENNA,
            "Smennost/deleneSm" to Smennost.DELENE_SMENY,
            "Smennost/nepretrzity" to Smennost.NEPRETRZITY,
            "Smennost/nocni" to Smennost.NOCNI,
            "Smennost/pruznaPd" to Smennost.PRUZNA,
            "Smennost/turnus" to Smennost.TURNUS,
            "Smennost/neurceno" to Smennost.NEURCENO,
        )
        mapovani.forEach { (id, expected) ->
            assertEquals(expected, Smennost.fromMpsvId(id))
        }
        assertNull(Smennost.fromMpsvId(null))
        assertNull(Smennost.fromMpsvId("Smennost/neznamyKod"))
    }

    @Test
    fun `pracoviste (adrprov) - RÚIAN kódy obcí se extrahují`() {
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
        val nabidka = MpsvRecordParser().parse(adrprov)!!
        assertEquals(MistoVykonu.TypMistaVykonu.ADRESA_PRACOVISTE, nabidka.misto.typ)
        assertEquals(listOf("Obec/537683"), nabidka.misto.pracovisteObecIds)
    }

    @Test
    fun `okresy i celaCR se mapují, Okres 9999 je placeholder`() {
        val okresFixture = JSONObject(
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
        val nabidka = MpsvRecordParser().parse(okresFixture)!!
        assertEquals(listOf("Okres/3210", "Okres/9999"), nabidka.misto.okresy)
    }

    @Test
    fun `neznámý kód výhody neprolomí parsing`() {
        val sNeznamou = JSONObject(
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
        val nabidka = MpsvRecordParser().parse(sNeznamou)!!
        assertEquals(listOf(Vyhoda.PREMIE), nabidka.vyhody)
    }
}