package io.github.painter99.jobsearch.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Dossier k nabídce (US4) — jeden na offerKey (formát [io.github.painter99.jobsearch.core.model.OfferKeys]).
 *
 * verdict = [io.github.painter99.jobsearch.core.model.DossierVerdict].name (perzistence tolerantní,
 * neznámá hodnota → OPEN). Vlastní obsah nabídky (deep linky, metadata) leží v OfferStore —
 * Room nese jen uživatelskou práci nad nabídkou (verdikt, poznámky, checklist), nikdy kopii inzerátu.
 */
@Entity(tableName = "dossiers")
data class DossierEntity(
    @PrimaryKey val offerKey: String,
    val verdict: String,
    val notes: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

/**
 * Jedna položka checklistu latky u dossieru (US4). itemKey = identifikátor položky
 * (definuje UI M1.6 — storage je agnostická, ukládá jen stav).
 */
@Entity(
    tableName = "checklist_entries",
    primaryKeys = ["offerKey", "itemKey"],
    foreignKeys = [
        ForeignKey(
            entity = DossierEntity::class,
            parentColumns = ["offerKey"],
            childColumns = ["offerKey"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("offerKey")],
)
data class ChecklistEntryEntity(
    val offerKey: String,
    val itemKey: String,
    val checked: Boolean,
)

/** Viděná nabídka (dedup sweepů — znovu se nezobrazuje jako nová). */
@Entity(tableName = "seen_offers")
data class SeenOfferEntity(
    @PrimaryKey val offerKey: String,
    val seenAtEpochMs: Long,
)