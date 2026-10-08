package io.github.painter99.jobsearch.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.OfferKeys
import io.github.painter99.jobsearch.core.model.ShiftPattern

/** Unifikovaný klíč nabídky pro UI (formát [OfferKeys]). */
fun JobOffer.key(): String = OfferKeys.mpsv(portalId)

/**
 * Řádek nabídky v seznamu (M1.6 vlna A; M1.6c U2 redesign dle WSW
 * StationCard vzoru): ElevatedCard, profese, zaměstnavatel, mzda,
 * směnnost; badge „viděno" pro dedup sweepů.
 */
@Composable
fun OfferRow(
    offer: JobOffer,
    seen: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = offer.profession,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (seen) {
                    Badge { Text(text = "viděno") }
                }
            }
            offer.employer?.let {
                Text(text = it.name, style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                offer.salaryFrom?.let {
                    Text(
                        text = "$it Kč/měs" + (offer.salaryTo?.let { to -> " – $to" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                offer.shiftPattern?.let {
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Text(text = shiftLabel(it), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** České názvy směnnosti (UI strings CZ dle PRD §9). */
fun shiftLabel(pattern: ShiftPattern): String = when (pattern) {
    ShiftPattern.SINGLE_SHIFT -> "jednosměnná"
    ShiftPattern.TWO_SHIFT -> "dvousměnná"
    ShiftPattern.THREE_SHIFT -> "třisměnná"
    ShiftPattern.FOUR_SHIFT -> "čtyřsměnná"
    ShiftPattern.SPLIT_SHIFTS -> "dělené směny"
    ShiftPattern.CONTINUOUS -> "nepřetržitý provoz"
    ShiftPattern.NIGHT_SHIFT -> "noční"
    ShiftPattern.FLEXIBLE -> "pružná pracovní doba"
    ShiftPattern.ROTATING -> "turnus"
    ShiftPattern.UNSPECIFIED -> "směnnost neurčena"
}