package io.github.painter99.jobsearch.core.model

/**
 * Směnnost — MPSV číselník `Smennost/*` (10 hodnot, ověřeno 6. 10. 2026).
 */
enum class Smennost(val mpsvKod: String) {
    JEDNOSMENNA("jednoSm"),
    DVOUSMENNA("dvouSm"),
    TRISMENNA("triSm"),
    CTYRSMENNA("ctyrSm"),
    DELENE_SMENY("deleneSm"),
    NEPRETRZITY("nepretrzity"),
    NOCNI("nocni"),
    PRUZNA("pruznaPd"),
    TURNUS("turnus"),
    NEURCENO("neurceno");

    companion object {
        fun fromMpsvId(id: String?): Smennost? {
            if (id == null) return null
            val kod = id.substringAfterLast('/')
            return entries.firstOrNull { it.mpsvKod == kod }
        }
    }
}

/**
 * Zaměstnavatel z MPSV záznamu (GDPR whitelist: jen IČO a název, žádné kontakty).
 */
data class Zamestnavatel(
    val ico: String?,
    val nazev: String,
)

/**
 * Místo výkonu práce z MPSV záznamu — varianty dle `typMistaVykonuPrace`
 * (obec/okres/adrprov/adrvolna/neurceno/celaCR; strategie matchingu viz MistoVykonuMatcher).
 */
data class MistoVykonu(
    val typ: TypMistaVykonu?,
    val obecId: String?,        // "Obec/<ruian>"
    val okresy: List<String>,   // "Okres/<lau>"
    val adresaText: String?,
    val pracovisteObecIds: List<String>, // RÚIAN kódy obcí z pracovišť (adrprov)
) {
    enum class TypMistaVykonu(val mpsvKod: String) {
        OBEC("obec"),
        OKRES("okres"),
        ADRESA_PRACOVISTE("adrprov"),
        ADRESA_VOLNA("adrvolna"),
        NEURCENO("neurceno"),
        CELA_CR("celaCR");

        companion object {
            fun fromMpsvId(id: String?): TypMistaVykonu? {
                if (id == null) return null
                val kod = id.substringAfterLast('/')
                return entries.firstOrNull { it.mpsvKod == kod }
            }
        }
    }

    companion object {
        val NIC = MistoVykonu(null, null, emptyList(), null, emptyList())
    }
}

/**
 * Výhoda volného místa — MPSV číselník `VyhodyVolnehoMista/*` (10 hodnot).
 */
enum class Vyhoda(val mpsvKod: String, val ceskyNazev: String) {
    UBYT("ubyt", "Ubytování"),
    PREDSK("predsk", "Předškolní zařízení"),
    NATUR("natur", "Naturální výhody"),
    JIZDNE("jizdne", "Jízdní výhody"),
    JINE("jine", "Jiné výhody"),
    STRAV("strav", "Podnikové stravování"),
    DOVOL("dovol", "Dovolená navíc"),
    PREMIE("premie", "Zvláštní prémie"),
    MIMO("mimo", "Mimo okres bydliště"),
    ZAHR("zahr", "V zahraničí");

    companion object {
        fun fromMpsvId(id: String?): Vyhoda? {
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
data class Nabidka(
    val portalId: Long,
    val referencniCislo: String,
    val profese: String,
    val smennost: Smennost?,
    val mzdaOd: Int?,
    val mzdaDo: Int?,
    val pocetHodinTydne: Int?,
    val zamestnavatel: Zamestnavatel?,
    val misto: MistoVykonu,
    val vyhody: List<Vyhoda>,
    val urlAdresa: String?,
    val agenturaSouhlas: Boolean?,  // souhlasAgenturyAgentura
    val uzivatelSouhlas: Boolean?,  // souhlasAgenturyUzivatel
) {
    /** Signál pro auto-fill checklistu latky: „Dovolená navíc" (MPSV kód dovol). */
    fun maDovolenouNavic(): Boolean = Vyhoda.DOVOL in vyhody

    /** Signál pro auto-fill checklistu latky: „Zvláštní prémie" (MPSV kód premie). */
    fun maZvlastniPremie(): Boolean = Vyhoda.PREMIE in vyhody
}