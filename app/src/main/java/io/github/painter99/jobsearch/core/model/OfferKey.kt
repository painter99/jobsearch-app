package io.github.painter99.jobsearch.core.model

/**
 * Unifikovaný klíč nabídky napříč zdroji — primární klíč pro dossier i seen (M1.5).
 *
 * Formát: "mpsv/&lt;portalId&gt;" resp. "pracecz/&lt;uuid&gt;".
 * Jeden klíč = jedna nabídka v Room databázi, nezávisle na zdroji.
 */
object OfferKeys {

    /** Klíč nabídky z MPSV otevřených dat (portalId z MPSV záznamu). */
    fun mpsv(portalId: Long): String = "mpsv/$portalId"

    /** Klíč nabídky z prace.cz (UUID z deep linku /nabidka/&lt;uuid&gt;). */
    fun pracecz(uuid: String): String = "pracecz/$uuid"
}