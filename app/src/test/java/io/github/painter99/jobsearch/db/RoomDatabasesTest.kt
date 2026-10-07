package io.github.painter99.jobsearch.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.painter99.jobsearch.core.model.DossierVerdict
import io.github.painter99.jobsearch.core.model.OfferKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T4–T5: Room — dossiery, checklisty latky, seen nabídky (in-memory DB, Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomDatabasesTest {

    private lateinit var db: JobsearchDatabase
    private lateinit var dossierDao: DossierDao
    private lateinit var checklistDao: ChecklistDao
    private lateinit var seenDao: SeenDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JobsearchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dossierDao = db.dossierDao()
        checklistDao = db.checklistDao()
        seenDao = db.seenDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- DossierDao ---

    @Test
    fun `dossier - upsert and read back`() = runTest {
        val dossier = DossierEntity(
            offerKey = OfferKeys.mpsv(12345),
            verdict = DossierVerdict.OPEN.name,
            notes = "",
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )
        dossierDao.upsert(dossier)
        assertEquals(dossier, dossierDao.byOfferKey(OfferKeys.mpsv(12345)))
    }

    @Test
    fun `dossier - upsert same key updates instead of duplicating`() = runTest {
        val key = OfferKeys.mpsv(1)
        dossierDao.upsert(DossierEntity(key, DossierVerdict.OPEN.name, "", 1_000L, 1_000L))
        dossierDao.upsert(DossierEntity(key, DossierVerdict.OPEN.name, "poznámka", 1_000L, 2_000L))
        assertEquals("poznámka", dossierDao.byOfferKey(key)?.notes)
    }

    @Test
    fun `dossier - missing key returns null`() = runTest {
        assertNull(dossierDao.byOfferKey(OfferKeys.mpsv(999)))
    }

    @Test
    fun `dossier - setVerdict updates verdict and updatedAt`() = runTest {
        val key = OfferKeys.mpsv(2)
        dossierDao.upsert(DossierEntity(key, DossierVerdict.OPEN.name, "", 1_000L, 1_000L))
        dossierDao.setVerdict(key, DossierVerdict.CONDITIONAL.name, nowEpochMs = 5_000L)
        val stored = dossierDao.byOfferKey(key)!!
        assertEquals(DossierVerdict.CONDITIONAL.name, stored.verdict)
        assertEquals(5_000L, stored.updatedAtEpochMs)
        assertEquals(1_000L, stored.createdAtEpochMs) // createdAt se nemění
    }

    @Test
    fun `dossier - verdict roundtrip all values`() = runTest {
        val key = OfferKeys.mpsv(3)
        dossierDao.upsert(DossierEntity(key, DossierVerdict.OPEN.name, "", 1L, 1L))
        for (verdict in DossierVerdict.entries) {
            dossierDao.setVerdict(key, verdict.name, nowEpochMs = 1L)
            assertEquals(verdict.name, dossierDao.byOfferKey(key)?.verdict)
        }
    }

    @Test
    fun `dossier - setNotes updates notes and updatedAt`() = runTest {
        val key = OfferKeys.mpsv(4)
        dossierDao.upsert(DossierEntity(key, DossierVerdict.OPEN.name, "", 1_000L, 1_000L))
        dossierDao.setNotes(key, "X-servis, zeptat na směny", nowEpochMs = 7_000L)
        val stored = dossierDao.byOfferKey(key)!!
        assertEquals("X-servis, zeptat na směny", stored.notes)
        assertEquals(7_000L, stored.updatedAtEpochMs)
    }

    @Test
    fun `dossier - observeAll sorts by updatedAt descending`() = runTest {
        dossierDao.upsert(DossierEntity(OfferKeys.mpsv(10), DossierVerdict.OPEN.name, "", 1L, 1_000L))
        dossierDao.upsert(DossierEntity(OfferKeys.mpsv(11), DossierVerdict.OPEN.name, "", 1L, 2_000L))
        assertEquals(listOf(OfferKeys.mpsv(11), OfferKeys.mpsv(10)), dossierDao.observeAll().first().map { it.offerKey })

        dossierDao.setVerdict(OfferKeys.mpsv(10), DossierVerdict.GO.name, nowEpochMs = 3_000L)
        assertEquals(listOf(OfferKeys.mpsv(10), OfferKeys.mpsv(11)), dossierDao.observeAll().first().map { it.offerKey })
    }

    @Test
    fun `dossier - delete removes row`() = runTest {
        val key = OfferKeys.mpsv(5)
        dossierDao.upsert(DossierEntity(key, DossierVerdict.OPEN.name, "", 1L, 1L))
        dossierDao.delete(key)
        assertNull(dossierDao.byOfferKey(key))
    }

    // --- ChecklistDao ---

    @Test
    fun `checklist - upsert and checkedKeys only checked items`() = runTest {
        val key = OfferKeys.mpsv(6)
        dossierDao.upsert(DossierEntity(key, DossierVerdict.OPEN.name, "", 1L, 1L))
        checklistDao.upsert(ChecklistEntryEntity(key, "ranní směna", checked = true))
        checklistDao.upsert(ChecklistEntryEntity(key, "OOPP", checked = false))
        checklistDao.upsert(ChecklistEntryEntity(key, "mzda ≥ 40k", checked = true))
        assertEquals(listOf("ranní směna", "mzda ≥ 40k"), checklistDao.checkedKeys(key))
        assertEquals(3, checklistDao.forOffer(key).size)
    }

    @Test
    fun `checklist - cascade delete with dossier`() = runTest {
        val key = OfferKeys.mpsv(7)
        dossierDao.upsert(DossierEntity(key, DossierVerdict.OPEN.name, "", 1L, 1L))
        checklistDao.upsert(ChecklistEntryEntity(key, "položka", checked = true))
        dossierDao.delete(key)
        assertTrue(checklistDao.forOffer(key).isEmpty())
    }

    // --- SeenDao ---

    @Test
    fun `seen - markSeen is idempotent`() = runTest {
        val key = OfferKeys.pracecz("abc-uuid")
        seenDao.markSeen(SeenOfferEntity(key, seenAtEpochMs = 1L))
        seenDao.markSeen(SeenOfferEntity(key, seenAtEpochMs = 2L))
        assertTrue(seenDao.isSeen(key))
        assertEquals(1, seenDao.count())
    }

    @Test
    fun `seen - not seen key returns false`() = runTest {
        assertFalse(seenDao.isSeen(OfferKeys.mpsv(404)))
    }

    @Test
    fun `seen - seenKeys and count across sources`() = runTest {
        seenDao.markSeen(SeenOfferEntity(OfferKeys.mpsv(1), 1L))
        seenDao.markSeen(SeenOfferEntity(OfferKeys.pracecz("u1"), 2L))
        assertEquals(setOf(OfferKeys.mpsv(1), OfferKeys.pracecz("u1")), seenDao.seenKeys().toSet())
        assertEquals(2, seenDao.count())
    }
}