package io.github.painter99.jobsearch.core.model

/**
 * Verdikt dossieru (US4): uživatel rozhodne GO / PODMÍNĚNĚ / VYŘAZENO;
 * OPEN = zatím nerozhodnuto (výchozí při založení dossieru).
 *
 * EN identifikátory dle PRD §9; české UI strings („PODMÍNĚNĚ"…) přijdou s M1.6.
 */
enum class DossierVerdict {
    OPEN,
    GO,
    CONDITIONAL,
    REJECTED;

    companion object {
        /**
         * Tolerantní mapping z perzistence (ukládejte [name]).
         * Neznámý název → OPEN (lokální DB, prakticky nedosažitelné; nikdy nespadnout).
         */
        fun fromName(name: String?): DossierVerdict =
            entries.firstOrNull { it.name == name } ?: OPEN
    }
}