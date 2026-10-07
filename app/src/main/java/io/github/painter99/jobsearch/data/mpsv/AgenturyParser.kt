package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Agentura
import io.github.painter99.jobsearch.core.model.AgenturaPovoleni
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapování MPSV „Agentury práce" (agentury-prace.json) na doménový model [Agentura].
 *
 * Struktura ověřena živě 7. 10. 2026: root = {"polozky": [...]}, záznam má
 * ico, nazev, povoleni[], odpovednyZastupce, kontaktniOsoby, adresaSidla…
 *
 * GDPR whitelist: odpovednyZastupce a kontaktniOsoby jsou osobní údaje —
 * parser je NEČTE a do modelu neprotečou (vynuceno absencí pole v modelu,
 * stejné pravidlo jako u MpsvRecordParser).
 *
 * Tolerantní parsing: pole mohou být null, chybět nebo být prázdná;
 * záznam bez IČO se zahodí (IČO je klíč detekce agentury).
 */
class AgenturyParser {

    fun parse(root: JSONObject): List<Agentura> = parsePolozky(root)

    fun parsePolozky(root: JSONObject): List<Agentura> {
        val polozky = root.optJSONArray("polozky") ?: return emptyList()
        val result = mutableListOf<Agentura>()
        for (i in 0 until polozky.length()) {
            val item = polozky.optJSONObject(i) ?: continue
            val ico = item.optStringOrNull("ico") ?: continue
            result.add(
                Agentura(
                    ico = ico,
                    nazev = item.optStringOrNull("nazev") ?: "",
                    povoleni = parsePovoleni(item.optJSONArray("povoleni")),
                )
            )
        }
        return result
    }

    private fun parsePovoleni(povoleni: JSONArray?): List<AgenturaPovoleni> {
        if (povoleni == null) return emptyList()
        val result = mutableListOf<AgenturaPovoleni>()
        for (i in 0 until povoleni.length()) {
            val p = povoleni.optJSONObject(i) ?: continue
            result.add(
                AgenturaPovoleni(
                    druhyPraci = p.optJSONObject("druhyPraci")?.optStringOrNull("cs"),
                    platnostOd = p.optStringOrNull("platnostOd"),
                    platnostDo = p.optStringOrNull("platnostDo"),
                )
            )
        }
        return result
    }

    // --- Tolerantní org.json extensiony (null vs. JSONObject.NULL) ---

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }
}