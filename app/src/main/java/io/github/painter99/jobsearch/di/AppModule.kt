package io.github.painter99.jobsearch.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.painter99.jobsearch.data.mpsv.MpsvCiselnikyClient
import io.github.painter99.jobsearch.data.mpsv.MpsvOffersClient
import io.github.painter99.jobsearch.data.mpsv.MunicipalityRepository
import io.github.painter99.jobsearch.pipeline.OfferSyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Kvalifikátor pro appkový CoroutineScope (supervizor dlouhých běhů — sync, bootstrap). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * Hilt bindings appkových služeb (M1.6 vlna A): OkHttp, MPSV klienti,
 * repozitář obcí, sync engine, app scope, hodiny, filesDir.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient()

    @Provides
    @Singleton
    fun provideMpsvOffersClient(client: OkHttpClient): MpsvOffersClient = MpsvOffersClient(client)

    @Provides
    @Singleton
    fun provideMpsvCiselnikyClient(client: OkHttpClient): MpsvCiselnikyClient =
        MpsvCiselnikyClient(client)

    @Provides
    @Singleton
    fun provideMunicipalityRepository(client: MpsvCiselnikyClient): MunicipalityRepository =
        MunicipalityRepository(client)

    @Provides
    @Singleton
    fun provideOfferSyncEngine(
        client: MpsvOffersClient,
        store: io.github.painter99.jobsearch.data.mpsv.OfferStore,
        anchorStore: io.github.painter99.jobsearch.pipeline.SyncAnchorStore,
    ): OfferSyncEngine = OfferSyncEngine(client, store, anchorStore)

    @Provides
    @Singleton
    fun provideFilesDir(@ApplicationContext context: Context): File = context.filesDir
}