package io.github.painter99.jobsearch.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * DAO dossierů (US4). Vytvoření/aktualizace = [upsert]; verdikt a poznámky
 * se mění cílenými UPDATE dotazy (updatedAtEpochMs si řídí volající).
 */
@Dao
interface DossierDao {

    @Upsert
    suspend fun upsert(dossier: DossierEntity)

    @Query("SELECT * FROM dossiers WHERE offerKey = :offerKey")
    suspend fun byOfferKey(offerKey: String): DossierEntity?

    @Query("SELECT * FROM dossiers ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<DossierEntity>>

    @Query("UPDATE dossiers SET verdict = :verdict, updatedAtEpochMs = :nowEpochMs WHERE offerKey = :offerKey")
    suspend fun setVerdict(offerKey: String, verdict: String, nowEpochMs: Long)

    @Query("UPDATE dossiers SET notes = :notes, updatedAtEpochMs = :nowEpochMs WHERE offerKey = :offerKey")
    suspend fun setNotes(offerKey: String, notes: String, nowEpochMs: Long)

    @Query("DELETE FROM dossiers WHERE offerKey = :offerKey")
    suspend fun delete(offerKey: String)
}

/** DAO položek checklistu latky (US4). */
@Dao
interface ChecklistDao {

    @Upsert
    suspend fun upsert(entry: ChecklistEntryEntity)

    @Query("SELECT * FROM checklist_entries WHERE offerKey = :offerKey")
    suspend fun forOffer(offerKey: String): List<ChecklistEntryEntity>

    @Query("SELECT itemKey FROM checklist_entries WHERE offerKey = :offerKey AND checked = 1")
    suspend fun checkedKeys(offerKey: String): List<String>

    @Query("DELETE FROM checklist_entries WHERE offerKey = :offerKey")
    suspend fun deleteForOffer(offerKey: String)
}

/** DAO viděných nabídek (dedup sweepů). markSeen je idempotentní (@Upsert). */
@Dao
interface SeenDao {

    @Upsert
    suspend fun markSeen(offer: SeenOfferEntity)

    @Query("SELECT COUNT(*) > 0 FROM seen_offers WHERE offerKey = :offerKey")
    suspend fun isSeen(offerKey: String): Boolean

    @Query("SELECT offerKey FROM seen_offers")
    suspend fun seenKeys(): List<String>

    @Query("SELECT COUNT(*) FROM seen_offers")
    suspend fun count(): Int
}