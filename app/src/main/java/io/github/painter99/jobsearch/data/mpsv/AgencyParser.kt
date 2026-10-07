package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Agency
import io.github.painter99.jobsearch.core.model.AgencyPermit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapování MPSV „Agentury práce" (agentury-prace.json) na doménový model [Agency].
 *
 * Struktura ověřena živě 7. 10. 2026: root = {"polozky": [...]}, záznam má
 * ico, nazev, povoleni[], odpovednyZastupce, kontaktniOsoby, adresaSidla…
 *
 * GDPR whitelist: odpovednyZastupce a kontaktniOsoby jsou osobní údaje —
 * parser je NEČTE a do modelu neprotečou (vynuceno absencí pole v modelu,
 * stejné pravidlo jako MpsvRecordParser).
 *
 * Tolerantní parsing: pole mohou být null, chybět nebo být prázdná;
 * záznam bez IČO se zahodí (IČO je klíč detekce agentury).
 */
class AgencyParser {

    fun parse(root: JSONObject): List<Agency> = parseItems(root)

    fun parseItems(root: JSONObject): List<Agency> {
        val items = root.optJSONArray("polozky") ?: return emptyList()
        val result = mutableListOf<Agency>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val ico = item.optStringOrNull("ico") ?: continue
            result.add(
                Agency(
                    ico = ico,
                    name = item.optStringOrNull("nazev") ?: "",
                    permits = parsePermits(item.optJSONArray("povoleni")),
                )
            )
        }
        return result
    }

    private fun parsePermits(permits: JSONArray?): List<AgencyPermit> {
        if (permits == null) return emptyList()
        val result = mutableListOf<AgencyPermit>()
        for (i in 0 until permits.length()) {
            val p = permits.optJSONObject(i) ?: continue
            result.add(
                AgencyPermit(
                    workTypes = p.optJSONObject("druhyPraci")?.optStringOrNull("cs"),
                    validFrom = p.optStringOrNull("platnostOd"),
                    validTo = p.optStringOrNull("platnostDo"),
                )
            )
        }
        return result
    }

    // --- Tolerantní org.json extensiony (null vs. JSONObject.NULL) ---

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }
}