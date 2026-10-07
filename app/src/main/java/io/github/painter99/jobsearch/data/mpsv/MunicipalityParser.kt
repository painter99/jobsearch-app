package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Municipality
import org.json.JSONObject

/**
 * Parse MPSV číselníku obcí (obce.json): root = {"polozky": [...]},
 * záznam = {id, kod, nazev: {cs}, okres} (ověřeno živě 7. 10. 2026).
 *
 * Tolerantní parsing: pole mohou být null/chybět; záznam bez id se zahodí.
 */
class MunicipalityParser {

    fun parse(root: JSONObject): List<Municipality> {
        val items = root.optJSONArray("polozky") ?: return emptyList()
        val result = mutableListOf<Municipality>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optStringOrNull("id") ?: continue
            result.add(
                Municipality(
                    id = id,
                    code = item.optStringOrNull("kod") ?: "",
                    name = item.optJSONObject("nazev")?.optStringOrNull("cs") ?: "",
                    districtId = item.optStringOrNull("okres"),
                )
            )
        }
        return result
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }
}