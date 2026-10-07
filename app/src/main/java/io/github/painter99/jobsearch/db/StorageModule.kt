package io.github.painter99.jobsearch.db

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.painter99.jobsearch.data.mpsv.FileOfferStore
import io.github.painter99.jobsearch.data.mpsv.OfferStore
import io.github.painter99.jobsearch.data.storage.ApiKeyStore
import io.github.painter99.jobsearch.data.storage.CriteriaStore
import io.github.painter99.jobsearch.data.storage.DataStoreSyncAnchorStore
import io.github.painter99.jobsearch.data.storage.ProfileStore
import io.github.painter99.jobsearch.data.storage.jobsearchDataStore
import io.github.painter99.jobsearch.pipeline.SyncAnchorStore
import javax.inject.Singleton

/**
 * Hilt bindings storage vrstvy (M1.5 + M1.6 vlna A):
 * - Room databáze + DAOs (dossier/checklist/seen),
 * - jeden sdílený DataStore + obálky (latka, profil lokalit, API klíč, sync kotva),
 * - [OfferStore] → [FileOfferStore] nad `filesDir/offers.json` (soubor via [OffersFile]).
 */
@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): JobsearchDatabase =
        Room.databaseBuilder(context, JobsearchDatabase::class.java, JobsearchDatabase.NAME)
            .build()

    @Provides
    fun provideDossierDao(db: JobsearchDatabase): DossierDao = db.dossierDao()

    @Provides
    fun provideChecklistDao(db: JobsearchDatabase): ChecklistDao = db.checklistDao()

    @Provides
    fun provideSeenDao(db: JobsearchDatabase): SeenDao = db.seenDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.jobsearchDataStore

    @Provides
    @Singleton
    fun provideCriteriaStore(dataStore: DataStore<Preferences>): CriteriaStore =
        CriteriaStore(dataStore)

    @Provides
    @Singleton
    fun provideApiKeyStore(dataStore: DataStore<Preferences>): ApiKeyStore = ApiKeyStore(dataStore)

    @Provides
    @Singleton
    fun provideProfileStore(dataStore: DataStore<Preferences>): ProfileStore = ProfileStore(dataStore)

    @Provides
    @Singleton
    fun provideOfferStore(@OffersFile offersFile: java.io.File): OfferStore = FileOfferStore(offersFile)

    @Provides
    @Singleton
    fun provideSyncAnchorStore(dataStore: DataStore<Preferences>): SyncAnchorStore =
        DataStoreSyncAnchorStore(dataStore)
}