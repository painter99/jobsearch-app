package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.MistoVykonu
import io.github.painter99.jobsearch.core.model.Nabidka
import io.github.painter99.jobsearch.core.model.Smennost
import io.github.painter99.jobsearch.core.model.Vyhoda
import io.github.painter99.jobsearch.core.model.Zamestnavatel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapování surového MPSV záznamu (JSONObject) na doménový model [Nabidka].
 *
 * GDPR whitelist: do modelu se mapují POUZE pole bez osobních údajů.
 * Osobní údaje (prvniKontaktSeZamestnavatelem, kdeSeHlasit,
 * pracoviste[].telefon/email, upresnujiciInformace) se IGNORUJÍ.
 *
 * Tolerantní parsing: pole mohou být null, chybět, nebo být listem
 * (ověřeno na full dumpu 6. 10. 2026 — pracoviste/adresaText jsou listy).
 */
class MpsvRecordParser {

    fun parse(raw: JSONObject): Nabidka? {
        val referencniCislo = raw.optString("referencniCislo")
        val profese = raw.optJSONObject("pozadovanaProfese")?.optString("cs") ?: return null
        if (profese.isBlank()) return null

        val zamestnavatelJson = raw.optJSONObject("zamestnavatel")
        val zamestnavatel = zamestnavatelJson?.let {
            Zamestnavatel(
                ico = it.optStringOrNull("ico"),
                nazev = it.optStringOrNull("nazev") ?: "",
            )
        }

        return Nabidka(
            portalId = raw.optLong("portalId"),
            referencniCislo = referencniCislo,
            profese = profese,
            smennost = Smennost.fromMpsvId(raw.optJSONObject("smennost")?.optString("id")),
            mzdaOd = raw.optIntOrNull("mesicniMzdaOd"),
            mzdaDo = raw.optIntOrNull("mesicniMzdaDo"),
            pocetHodinTydne = raw.optIntOrNull("pocetHodinTydne"),
            zamestnavatel = zamestnavatel,
            misto = parseMisto(raw.optJSONObject("mistoVykonuPrace")),
            vyhody = parseVyhody(raw.optJSONArray("vyhodyVolnehoMista")),
            urlAdresa = raw.optStringOrNull("urlAdresa"),
            agenturaSouhlas = raw.optBooleanOrNull("souhlasAgenturyAgentura"),
            uzivatelSouhlas = raw.optBooleanOrNull("souhlasAgenturyUzivatel"),
        )
    }

    private fun parseMisto(misto: JSONObject?): MistoVykonu {
        if (misto == null) return MistoVykonu.NIC

        val typ = MistoVykonu.TypMistaVykonu.fromMpsvId(
            misto.optJSONObject("typMistaVykonuPrace")?.optString("id")
        )
        val obecId = misto.optJSONObject("obec")?.optStringOrNull("id")

        val okresy = mutableListOf<String>()
        misto.optJSONArray("okresy")?.forEachObject { okresy.add(it.optString("id")) }

        val pracovisteObecIds = mutableListOf<String>()
        misto.optJSONArray("pracoviste")?.forEachObject { pracoviste ->
            pracoviste.optJSONObject("adresa")?.optJSONObject("obec")?.optStringOrNull("id")
                ?.let { pracovisteObecIds.add(it) }
        }

        return MistoVykonu(
            typ = typ,
            obecId = obecId,
            okresy = okresy,
            adresaText = misto.optStringOrNull("adresaText"),
            pracovisteObecIds = pracovisteObecIds,
        )
    }

    private fun parseVyhody(vyhody: JSONArray?): List<Vyhoda> {
        if (vyhody == null) return emptyList()
        val result = mutableListOf<Vyhoda>()
        vyhody.forEachObject { item ->
            val id = item.optJSONObject("vyhoda")?.optString("id")
            Vyhoda.fromMpsvId(id)?.let { result.add(it) } // neznámý kód → ignorovat (tolerantně)
        }
        return result
    }

    // --- Tolerantní org.json extensiony (opt*OrNull: null vs. JSONObject.NULL) ---

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key, -1).takeIf { it >= 0 }

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (isNull(key)) null else optBoolean(key)

    private fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            (opt(i) as? JSONObject)?.let(action)
        }
    }
}