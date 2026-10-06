package io.github.painter99.jobsearch.data.mpsv

import io.github.painter99.jobsearch.core.model.Nabidka
import org.json.JSONObject

/**
 * Mapování surového MPSV záznamu (JSONObject) na doménový model [Nabidka].
 *
 * GDPR whitelist: do modelu se mapují POUZE pole bez osobních údajů.
 * Osobní údaje (prvniKontaktSeZamestnavatelem, kdeSeHlasit,
 * pracoviste[].telefon/email, upresnujiciInformace) se IGNORUJÍ.
 */
class MpsvRecordParser {

    fun parse(raw: JSONObject): Nabidka? = null // RED stub
}