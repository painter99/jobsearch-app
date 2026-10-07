package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.WorkLocation
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.ShiftPattern
import io.github.painter99.jobsearch.core.model.Benefit
import io.github.painter99.jobsearch.core.model.Employer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapování surového MPSV záznamu (JSONObject) na doménový model [JobOffer].
 *
 * GDPR whitelist: do modelu se mapují POUZE pole bez osobních údajů.
 * Osobní údaje (prvniKontaktSeZamestnavatelem, kdeSeHlasit,
 * pracoviste[].telefon/email, upresnujiciInformace) se IGNORUJÍ.
 *
 * Tolerantní parsing: pole mohou být null, chybět, nebo být listem
 * (ověřeno na full dumpu 6. 10. 2026 — pracoviste/adresaText jsou listy).
 */
class MpsvRecordParser {

    fun parse(raw: JSONObject): JobOffer? {
        val referenceNumber = raw.optString("referencniCislo")
        val profession = raw.optJSONObject("pozadovanaProfese")?.optString("cs") ?: return null
        if (profession.isBlank()) return null

        val employerJson = raw.optJSONObject("zamestnavatel")
        val employer = employerJson?.let {
            Employer(
                ico = it.optStringOrNull("ico"),
                name = it.optStringOrNull("nazev") ?: "",
            )
        }

        return JobOffer(
            portalId = raw.optLong("portalId"),
            referenceNumber = referenceNumber,
            profession = profession,
            shiftPattern = ShiftPattern.fromMpsvId(raw.optJSONObject("smennost")?.optString("id")),
            salaryFrom = raw.optIntOrNull("mesicniMzdaOd"),
            salaryTo = raw.optIntOrNull("mesicniMzdaDo"),
            hoursPerWeek = raw.optIntOrNull("pocetHodinTydne"),
            employer = employer,
            location = parseLocation(raw.optJSONObject("mistoVykonuPrace")),
            benefits = parseBenefits(raw.optJSONArray("vyhodyVolnehoMista")),
            url = raw.optStringOrNull("urlAdresa"),
            agencyConsent = raw.optBooleanOrNull("souhlasAgenturyAgentura"),
            userConsent = raw.optBooleanOrNull("souhlasAgenturyUzivatel"),
        )
    }

    private fun parseLocation(location: JSONObject?): WorkLocation {
        if (location == null) return WorkLocation.NONE

        val type = WorkLocation.LocationType.fromMpsvId(
            location.optJSONObject("typMistaVykonuPrace")?.optString("id")
        )
        val municipalityId = location.optJSONObject("obec")?.optStringOrNull("id")

        val districts = mutableListOf<String>()
        location.optJSONArray("okresy")?.forEachObject { districts.add(it.optString("id")) }

        val worksiteMunicipalityIds = mutableListOf<String>()
        location.optJSONArray("pracoviste")?.forEachObject { worksite ->
            worksite.optJSONObject("adresa")?.optJSONObject("obec")?.optStringOrNull("id")
                ?.let { worksiteMunicipalityIds.add(it) }
        }

        return WorkLocation(
            type = type,
            municipalityId = municipalityId,
            districts = districts,
            addressText = location.optStringOrNull("adresaText"),
            worksiteMunicipalityIds = worksiteMunicipalityIds,
        )
    }

    private fun parseBenefits(benefits: JSONArray?): List<Benefit> {
        if (benefits == null) return emptyList()
        val result = mutableListOf<Benefit>()
        benefits.forEachObject { item ->
            val id = item.optJSONObject("vyhoda")?.optString("id")
            Benefit.fromMpsvId(id)?.let { result.add(it) } // neznámý kód → ignorovat (tolerantně)
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