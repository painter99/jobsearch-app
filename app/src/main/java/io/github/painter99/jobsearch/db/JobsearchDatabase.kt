package io.github.painter99.jobsearch.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room databáze appky (M1.5): dossiery + checklisty + seen nabídky.
 *
 * Nabídky samotné (MPSV whitelist) zůstávají v OfferStore (JSON Lines) —
 * Room nese jen uživatelská data. Schéma v1: před v1 destructive migration OK
 * (žádní uživatelé s daty), po v1 proper migrations (PRD §11).
 */
@Database(
    entities = [
        DossierEntity::class,
        ChecklistEntryEntity::class,
        SeenOfferEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class JobsearchDatabase : RoomDatabase() {
    abstract fun dossierDao(): DossierDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun seenDao(): SeenDao

    companion object {
        const val NAME = "jobsearch.db"
    }
}