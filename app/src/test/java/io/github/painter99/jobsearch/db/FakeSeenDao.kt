package io.github.painter99.jobsearch.db

import io.github.painter99.jobsearch.core.model.OfferKeys

/** Fake SeenDao pro testy ViewModels (in-memory, žádný Android framework). */
class FakeSeenDao : SeenDao {
    val seen = LinkedHashMap<String, Long>()

    override suspend fun markSeen(offer: SeenOfferEntity) {
        seen[offer.offerKey] = offer.seenAtEpochMs
    }

    override suspend fun isSeen(offerKey: String): Boolean = offerKey in seen

    override suspend fun seenKeys(): List<String> = seen.keys.toList()

    override suspend fun count(): Int = seen.size

    companion object {
        fun key(portalId: Long): String = OfferKeys.mpsv(portalId)
    }
}