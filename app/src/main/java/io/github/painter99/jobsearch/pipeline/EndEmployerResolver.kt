package io.github.painter99.jobsearch.pipeline

import io.github.painter99.jobsearch.core.model.Company
import io.github.painter99.jobsearch.data.FetchResult
import io.github.painter99.jobsearch.data.ares.AresClient

/**
 * Resolver koncového zaměstnavatele (pilíř č. 1B, US2).
 *
 * Stopy z inzerátu ([EmployerHints]) → ARES vyhledat (POST, dávka po NACE)
 * → kandidáti se skóre + zdůvodněním. Deterministický scoring (oracle =
 * Python prototyp `agency_resolver.py`); AI řazení až M1.7. N7: výstupem
 * jsou vždy KANDIDÁTI — potvrzuje uživatel, appka netvrdí fakt.
 *
 * Scoring (shodný s prototypem):
 *  +2.0 sídlo v hledané obci; +1.0 sídlo v hledaném okrese;
 *  +2.0 NACE shoda (vyhledávací filtr, ale potvrzuje se u záznamu);
 *  +1.0 právní forma typická pro rodinnou firmu (s.r.o. „112", a.s. „121",
 *       OSVČ „70653" — heuristika, ne pravda o rodině);
 *  +0.5 dlouhá historie (vznik do [EmployerHints.foundedUntilYear]).
 * Kandidáti se skóre 0 se zahazují; řazení sestupně, max [maxCandidates].
 */
class EndEmployerResolver(
    private val aresClient: AresClient,
    private val maxCandidates: Int = 5,
) {

    suspend fun resolve(hints: EmployerHints): FetchResult<List<ResolverCandidate>> =
        when (val r = aresClient.search(hints.naceCodes)) {
            is FetchResult.Success -> FetchResult.Success(score(r.data, hints))
            is FetchResult.HttpError -> r
            is FetchResult.NetworkError -> r
            is FetchResult.ParseError -> r
        }

    /** Scoring nad seznamem firem (testovatelné bez sítě). */
    fun score(companies: List<Company>, hints: EmployerHints): List<ResolverCandidate> {
        val candidates = companies.map { scoreOne(it, hints) }
            .filter { it.score > 0.0 }
            .sortedWith(compareByDescending<ResolverCandidate> { it.score }.thenBy { it.company.businessName })
        return candidates.take(maxCandidates)
    }

    private fun scoreOne(c: Company, hints: EmployerHints): ResolverCandidate {
        var score = 0.0
        val reasons = mutableListOf<String>()

        if (hints.municipalityName != null && c.municipality != null &&
            c.municipality.equals(hints.municipalityName, ignoreCase = true)
        ) {
            score += 2.0
            reasons.add("sídlo v hledané obci (${c.municipality})")
        } else if (hints.districtName != null && c.districtName != null &&
            c.districtName.contains(hints.districtName, ignoreCase = true)
        ) {
            score += 1.0
            reasons.add("sídlo v okolí (okres ${c.districtName})")
        }

        if (hints.naceCodes.any { it in c.czNace }) {
            score += 2.0
            reasons.add("hlavní NACE odpovídá oboru (${hints.naceCodes.joinToString(", ")})")
        }

        if (hints.familyBusiness && c.legalForm in FAMILY_BUSINESS_LEGAL_FORMS) {
            score += 1.0
            reasons.add("právní forma typická pro rodinnou firmu (s.r.o./a.s./OSVČ)")
        }

        val foundedYear = c.foundedOn?.take(4)?.toIntOrNull()
        if (hints.foundedUntilYear != null && foundedYear != null && foundedYear <= hints.foundedUntilYear) {
            score += 0.5
            reasons.add("dlouhá historie (vznik $foundedYear)")
        }

        return ResolverCandidate(company = c, score = score, reasons = reasons)
    }

    companion object {
        /**
         * Právní formy (ARES kódy) typické pro rodinné firmy — heuristika.
         * Shoda s oraclem (prototyp: 145, 112, 70653). a.s. (121) záměrně
         * bez bonusu — každé a.s. v ČR by bonus roztřásl; rozšíření = tuning.
         */
        val FAMILY_BUSINESS_LEGAL_FORMS = setOf("112", "145", "70653")
    }
}