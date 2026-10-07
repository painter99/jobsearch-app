package io.github.painter99.jobsearch.data.pracecz

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URI

/**
 * Parse HTML výpisové stránky prace.cz (/nabidky/{kraj}/{obec}/,
 * server-rendered — ověřeno živě 7. 10. 2026).
 *
 * Extrahuje POUZE deep linky na detaily inzerátů:
 * - /nabidka/{uuid}
 * - /firma/{slug}/nabidka/{uuid} (slug firmy = bonus pro resolver)
 *
 * Strip tracking parametry (?rps=, utm_*) z query — Model A: deep linky
 * bez sledovacích parametrů. Duplicitní odkazy (stejný path) dedupuje.
 * Odkazy mimo prace.cz a výpisy jiných lokalit ignoruje.
 *
 * Křehkost (PRD R1): výpisy nemají JSON-LD; jsoup DOM selektory jsou
 * robustnější než regexy, ale změna struktury = HttpError/ParseError
 * signál, ne crash. Testy drží fixture ze živé struktury 7. 10. 2026.
 */
class PraceCzListingParser {

    fun parse(html: String): ListingPage {
        val document = Jsoup.parse(html)
        val deepLinks = LinkedHashSet<String>()
        document.select("a[href]").forEach { element ->
            val href = element.attr("href")
            val link = extractDeepLink(href) ?: return@forEach
            deepLinks.add(link)
        }
        if (deepLinks.isEmpty()) {
            // výpis vždy obsahuje odkazy, pokud lokalita existuje;
            // prázdný výsledek = podezřelá struktura (R1) — ParseError
            throw IOException("no deep links found — suspicious listing structure")
        }
        return ListingPage(
            deepLinks = deepLinks.toList(),
            hasNextPage = hasNextPage(document),
        )
    }

    /** "/nabidka/{uuid}/?rps=…" → absolutní URL bez tracking parametrů; null = ne detail inzerátu. */
    private fun extractDeepLink(href: String): String? {
        if (!href.startsWith("/nabidka/") && !href.startsWith("/firma/")) return null
        if (!href.contains("/nabidka/")) return null // /firma/{slug} bez nabidky = profil firmy, ne inzerát
        val path = href.substringBefore('?')
        if (!path.endsWith("/")) return null
        return "https://www.prace.cz$path"
    }

    /** Paginace ?page=N server-side; hasNext = existuje odkaz na vyšší stranu než [currentPage]. */
    private fun hasNextPage(document: Document): Boolean {
        val currentPage = document.select("nav.pagination a[aria-current], nav.pagination .current")
            .firstOrNull()?.let { element ->
                element.attr("href").substringAfter("page=", "").toIntOrNull()
            } ?: 1
        return document.select("nav.pagination a[href]").any { element ->
            val page = element.attr("href").substringAfter("page=", "").toIntOrNull()
            page != null && page > currentPage
        }
    }
}