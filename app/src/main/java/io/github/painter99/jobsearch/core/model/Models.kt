package io.github.painter99.jobsearch.core.model

/**
 * Směnnost — MPSV číselník `Smennost/<id>` (10 hodnot, ověřeno 6. 10. 2026).
 */
enum class ShiftPattern(val mpsvKod: String) {
    SINGLE_SHIFT("jednoSm"),
    TWO_SHIFT("dvouSm"),
    THREE_SHIFT("triSm"),
    FOUR_SHIFT("ctyrSm"),
    SPLIT_SHIFTS("deleneSm"),
    CONTINUOUS("nepretrzity"),
    NIGHT_SHIFT("nocni"),
    FLEXIBLE("pruznaPd"),
    ROTATING("turnus"),
    UNSPECIFIED("neurceno");

    companion object {
        fun fromMpsvId(id: String?): ShiftPattern? {
            if (id == null) return null
            val kod = id.substringAfterLast('/')
            return entries.firstOrNull { it.mpsvKod == kod }
        }
    }
}

/**
 * Zaměstnavatel z MPSV záznamu (GDPR whitelist: jen IČO a název, žádné kontakty).
 */
data class Employer(
    val ico: String?,
    val name: String,
)

/**
 * Místo výkonu práce z MPSV záznamu — varianty dle `typMistaVykonuPrace`
 * (obec/okres/adrprov/adrvolna/neurceno/celaCR; strategie matchingu viz LocationFilter).
 */
data class WorkLocation(
    val type: LocationType?,
    val municipalityId: String?,          // "Obec/<ruian>"
    val districts: List<String>,          // "Okres/<lau>"
    val addressText: String?,
    val worksiteMunicipalityIds: List<String>, // RÚIAN kódy obcí z pracovišť (adrprov)
) {
    enum class LocationType(val mpsvKod: String) {
        MUNICIPALITY("obec"),
        DISTRICT("okres"),
        WORKSITE_ADDRESS("adrprov"),
        FREE_ADDRESS("adrvolna"),
        UNSPECIFIED("neurceno"),
        WHOLE_CR("celaCR");

        companion object {
            fun fromMpsvId(id: String?): LocationType? {
                if (id == null) return null
                val kod = id.substringAfterLast('/')
                return entries.firstOrNull { it.mpsvKod == kod }
            }
        }
    }

    companion object {
        val NONE = WorkLocation(type = null, municipalityId = null, districts = emptyList(), addressText = null, worksiteMunicipalityIds = emptyList())
    }
}

/**
 * Výhoda volného místa — MPSV číselník `VyhodyVolnehoMista/<id>` (10 hodnot).
 */
enum class Benefit(val mpsvKod: String, val czechName: String) {
    ACCOMMODATION("ubyt", "Ubytování"),
    PRESCHOOL("predsk", "Předškolní zařízení"),
    NATURAL("natur", "Naturální výhody"),
    TRANSPORT("jizdne", "Jízdní výhody"),
    OTHER("jine", "Jiné výhody"),
    CANTEEN("strav", "Podnikové stravování"),
    EXTRA_VACATION("dovol", "Dovolená navíc"),
    SPECIAL_BONUS("premie", "Zvláštní prémie"),
    OUT_OF_DISTRICT("mimo", "Mimo okres bydliště"),
    ABROAD("zahr", "V zahraničí");

    companion object {
        fun fromMpsvId(id: String?): Benefit? {
            if (id == null) return null
            val kod = id.substringAfterLast('/')
            return entries.firstOrNull { it.mpsvKod == kod }
        }
    }
}

/**
 * Nabídka (volné místo) z MPSV otevřených dat.
 *
 * GDPR whitelist: model nese POUZE pole bez osobních údajů.
 * Osobní údaje v surových datech (jména, telefony, e-maily kontaktních osob
 * v `prvniKontaktSeZamestnavatelem`, `kdeSeHlasit`, `pracoviste[].telefon/email`,
 * `upresnujiciInformace`) se do modelu NEMAPUJÍ — viz MpsvRecordParser.
 */
data class JobOffer(
    val portalId: Long,
    val referenceNumber: String,
    val profession: String,
    val shiftPattern: ShiftPattern?,
    val salaryFrom: Int?,
    val salaryTo: Int?,
    val hoursPerWeek: Int?,
    val employer: Employer?,
    val location: WorkLocation,
    val benefits: List<Benefit>,
    val url: String?,
    val agencyConsent: Boolean?,   // souhlasAgenturyAgentura
    val userConsent: Boolean?,     // souhlasAgenturyUzivatel
) {
    /** Signál pro auto-fill checklistu latky: „Dovolená navíc" (MPSV kód dovol). */
    fun hasExtraVacation(): Boolean = Benefit.EXTRA_VACATION in benefits

    /** Signál pro auto-fill checklistu latky: „Zvláštní prémie" (MPSV kód premie). */
    fun hasSpecialBonus(): Boolean = Benefit.SPECIAL_BONUS in benefits
}