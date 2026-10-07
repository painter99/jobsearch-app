package io.github.painter99.jobsearch.db

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** Kvalifikátor souboru offers.json (sdílený OfferStore + bootstrap download). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OffersFile

/**
 * Hilt provider souboru offers.json (odděleno od StorageModule — AppModule
 * ho potřebuje pro bootstrap download, OfferStore i OffersViewModel).
 */
@Module
@InstallIn(SingletonComponent::class)
object OffersFileModule {

    @Provides
    @Singleton
    @OffersFile
    fun provideOffersFile(@ApplicationContext context: Context): File =
        File(context.filesDir, "offers.json")
}