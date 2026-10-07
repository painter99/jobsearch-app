package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.JobOffer

/**
 * Dedup nabídek napříč zdroji (pipeline-dedupe, AC1).
 *
 * Klíče v pořadí priority (první nalezený klíč rozhoduje):
 * 1. [JobOffer.portalId] — MPSV primární klíč (unikátní napříč záznamy),
 * 2. [JobOffer.referenceNumber] — MPSV referenční číslo,
 * 3. kanonizované [JobOffer.url] (bez tracking parametrů, lowercase host),
 * 4. IČO + profese (párování pro inzeráty z webů bez ID).
 *
 * Deterministické; první výskyt vyhrává (zachová pořadí vstupu).
 * Nabídka bez žádného identifikátoru se dedupovat nedá — ponechá se
 * (tolerantně, netvrdíme duplicity bez důkazu).
 */
object OfferDedupe {

    /** Kanonizace URL: strip tracking parametrů (rps, utm_*), lowercase host. */
    fun canonicalUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val parts = trimmed.split('?', limit = 2)
        val base = parts[0]
        val query = parts.getOrNull(1)

        val schemeEnd = base.indexOf("://")
        val hostStart = if (schemeEnd >= 0) schemeEnd + 3 else 0
        val pathStart = base.indexOf('/', hostStart).let { if (it < 0) base.length else it }
        val host = base.substring(hostStart, pathStart).lowercase()
        val path = base.substring(pathStart)

        val cleanedQuery = query
            ?.split('&')
            ?.filter { it.isNotBlank() }
            ?.filter { p ->
                val key = p.substringBefore('=').lowercase()
                key != "rps" && !key.startsWith("utm_")
            }
            ?.joinToString("&")

        return buildString {
            append(base, 0, hostStart)
            append(host)
            append(path)
            if (!cleanedQuery.isNullOrEmpty()) append('?').append(cleanedQuery)
        }
    }

    fun dedupe(offers: List<JobOffer>): List<JobOffer> {
        val seen = mutableSetOf<String>()
        return offers.filter { offer ->
            val key = dedupeKey(offer) ?: return@filter true
            seen.add(key)
        }
    }

    /** Vrací dedup klíč, nebo null, když nabídka nemá žádný identifikátor. */
    private fun dedupeKey(offer: JobOffer): String? = when {
        offer.portalId != 0L -> "portalId:${offer.portalId}"
        offer.referenceNumber.isNotBlank() -> "ref:${offer.referenceNumber}"
        !offer.url.isNullOrBlank() -> "url:${canonicalUrl(offer.url)}"
        offer.employer?.ico != null && offer.profession.isNotBlank() ->
            "icoProf:${offer.employer.ico}|${offer.profession.trim().lowercase()}"
        else -> null
    }
}