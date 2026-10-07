package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.JobOffer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typ změny z MPSV přírůstku (číselník typy-zmen-opendata):
 * `TypyZmenOpenData/novy` / `zmeneny` / `zruseny`.
 */
enum class ChangeType(val mpsvKod: String) {
    NEW("novy"),
    CHANGED("zmeneny"),
    REMOVED("zruseny");

    companion object {
        fun fromMpsvId(id: String?): ChangeType? {
            if (id == null) return null
            val kod = id.substringAfterLast('/')
            return entries.firstOrNull { it.mpsvKod == kod }
        }
    }
}

/**
 * Jeden záznam denního přírůstku: co se s nabídkou stalo ([changeType])
 * + kompletní záznam nabídky (i zrušené nabídky mají plných 39 klíčů,
 * ověřeno živě 7. 10. 2026 — parsují se stejně přes [MpsvRecordParser]).
 */
data class IncrementRecord(
    val portalId: Long,
    val changeType: ChangeType,
    val offer: JobOffer?,
)

/**
 * Parse denního přírůstku (root = {"polozky": [...]}).
 *
 * Tolerantní parsing: neznámý typ změny → záznam se přeskočí (parsery
 * musí přežít nový typ z číselníku); záznam bez profese → offer = null
 * (MpsvRecordParser vrací null), ale portalId se zachová pro REMOVED.
 *
 * GDPR: přírůstek má stejné schéma jako full dump → [MpsvRecordParser]
 * whitelist platí beze změny (osobní údaje se do modelu nemapují).
 */
class IncrementParser(private val recordParser: MpsvRecordParser = MpsvRecordParser()) {

    fun parse(root: JSONObject): List<IncrementRecord> {
        val items = root.optJSONArray("polozky") ?: return emptyList()
        val result = mutableListOf<IncrementRecord>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val changeType = ChangeType.fromMpsvId(
                item.optJSONObject("typZmenyOpenData")?.optString("id")
            ) ?: continue // neznámý typ → přeskočit (tolerantně)
            val offer = recordParser.parse(item)
            val portalId = if (offer != null) offer.portalId else item.optLong("portalId")
            result.add(IncrementRecord(portalId, changeType, offer))
        }
        return result
    }

    /** Parse z textu (vstup z [MpsvOffersClient.fetchIncrement]). */
    fun parseText(text: String): List<IncrementRecord> = parse(JSONObject(text))
}