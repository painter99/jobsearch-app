package io.github.painter99.jobsearch.data.mpsv

import com.squareup.moshi.JsonReader
import io.github.painter99.jobsearch.core.model.JobOffer
import io.github.painter99.jobsearch.core.model.WorkLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Lokální úložiště nabídek (data-mpsv, M1.4).
 *
 * Formát souboru offers.json (JSON Lines, jeden záznam na řádek):
 * {"portalId":…, "offer": {whitelistovaná pole ve struktuře MPSV záznamu}}
 *
 * - [importBootstrap] — streamující import full dumpu (187 MB → ~12 MB
 *   whitelistované podmnožiny). Moshi JsonReader.nextSource() + [MpsvRecordParser]
 *   (org.json na 1 záznam ~4 KB): 40 000 záznamů bez OOM. Atomický zápis.
 *   Při poškozeném dumpu vyhazuje výjimku (JsonDataException/IOException).
 * - [applyIncrement] — upsert/remove dle [IncrementRecord] (removals dřív,
 *   pak upserty; index se přestaví po každé dávce).
 * - [all] / [count] / [findByPortalId] — čtení pro pipeline/UI.
 *
 * GDPR: do souboru se zapisují POUZE pole modelu [JobOffer] — osobní údaje
 * (kontaktní osoby, kdeSeHlasit, upresnujiciInformace) se do modelu
 * nemapují (MpsvRecordParser whitelist), proto se do souboru nemohou
 * dostat. Vynuceno testem (FileOfferStoreTest).
 *
 * Volání musí být serializovaná (zajišťuje OfferSyncEngine).
 */
interface OfferStore {
    /** Načte existující soubor do paměti (po restartu procesu). Vrací počet nabídek. */
    suspend fun load(): Int

    /** Import full dumpu (bootstrap). Přepíše celý obsah. Vrací počet importovaných nabídek. */
    suspend fun importBootstrap(dumpFile: File): Int

    /** Aplikuje záznamy denního přírůstku. Vrací počet změněných řádků. */
    suspend fun applyIncrement(records: List<IncrementRecord>): Int

    fun all(): List<JobOffer>

    fun count(): Int

    fun findByPortalId(portalId: Long): JobOffer?
}

/**
 * Implementace nad JSON Lines souborem + in-memory index portalId → pozice.
 */
class FileOfferStore(
    private val file: File,
) : OfferStore {

    private val parser = MpsvRecordParser()
    private val index = HashMap<Long, Int>() // portalId → pozice v lines
    private val lines = ArrayList<String>()
    private var loaded = false

    override suspend fun load(): Int = withContext(Dispatchers.IO) {
        if (!loaded) {
            if (file.exists()) {
                file.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.forEachLine { line ->
                        if (line.isNotBlank()) lines.add(line)
                    }
                }
                rebuildIndex()
            }
            loaded = true
        }
        lines.size
    }

    override suspend fun importBootstrap(dumpFile: File): Int = withContext(Dispatchers.IO) {
        val imported = streamBootstrap(dumpFile)
        rewriteFile(imported)
        loaded = true // jinak by load() v applyIncrement načetla soubor znovu (doubling bug, run #17)
        imported.size
    }

    override suspend fun applyIncrement(records: List<IncrementRecord>): Int = withContext(Dispatchers.IO) {
        load()
        var changed = 0

        // 1) Removals — odfiltrovat řádky, pak přestavět index (pozice se posunuly)
        val removedIds = records.asSequence()
            .filter { it.changeType == ChangeType.REMOVED }
            .map { it.portalId }
            .toHashSet()
        if (removedIds.isNotEmpty()) {
            val before = lines.size
            lines.removeAll { line -> linePortalId(line) in removedIds }
            changed += before - lines.size
            rebuildIndex()
        }

        // 2) Upserty (NEW/CHANGED)
        for (record in records) {
            if (record.changeType == ChangeType.REMOVED) continue
            val offer = record.offer ?: continue // bez profese nelze modelovat
            if (offer.portalId != record.portalId) continue
            val position = index[offer.portalId]
            if (position != null) {
                lines[position] = offerToLine(offer)
            } else {
                lines.add(offerToLine(offer))
                index[offer.portalId] = lines.size - 1
            }
            changed++
        }

        if (changed > 0) rewriteFile(lines)
        changed
    }

    override fun all(): List<JobOffer> = lines.mapNotNull { lineToOffer(it) }

    override fun count(): Int = lines.size

    override fun findByPortalId(portalId: Long): JobOffer? =
        index[portalId]?.let { lines.getOrNull(it) }?.let { lineToOffer(it) }

    // --- import full dumpu (streaming) ---

    private fun streamBootstrap(dumpFile: File): List<String> {
        val result = ArrayList<String>()
        dumpFile.inputStream().buffered().use { input ->
            val reader = JsonReader.of(input.source().buffer())
            reader.beginObject()
            while (reader.hasNext()) {
                if (reader.selectName(POLOZKY) == 0) {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        // nextSource(): raw bytes jednoho záznamu — konzumovat
                        // OKAMŽITĚ (před dalším voláním readeru, viz Moshi API)
                        val raw = reader.nextSource().inputStream().readBytes()
                        try {
                            parser.parse(JSONObject(raw.toString(Charsets.UTF_8)))?.let { offer ->
                                result.add(offerToLine(offer))
                            }
                        } catch (e: Exception) {
                            // poškozený záznam → přeskočit (tolerantně), import pokračuje
                        }
                    }
                    reader.endArray()
                } else {
                    reader.skipName()
                    reader.skipValue()
                }
            }
            reader.endObject()
        }
        return result
    }

    // --- serializace řádku (struktura MPSV záznamu → zpětně čitelná parserem) ---

    private fun offerToLine(offer: JobOffer): String = JSONObject().apply {
        put("portalId", offer.portalId)
        put("offer", offerToJson(offer))
    }.toString()

    private fun lineToOffer(line: String): JobOffer? = try {
        val root = JSONObject(line)
        val portalId = root.optLong("portalId")
        root.optJSONObject("offer")?.let { parser.parse(it) }?.takeIf { it.portalId == portalId }
    } catch (e: Exception) {
        null // poškozený řádek přeskočit (tolerantně)
    }

    private fun linePortalId(line: String): Long = try {
        JSONObject(line).optLong("portalId")
    } catch (e: Exception) {
        -1L
    }

    private fun offerToJson(offer: JobOffer): JSONObject = JSONObject().apply {
        put("portalId", offer.portalId)
        put("referencniCislo", offer.referenceNumber)
        put("pozadovanaProfese", JSONObject().put("cs", offer.profession))
        offer.shiftPattern?.let { put("smennost", JSONObject().put("id", "Smennost/${it.mpsvKod}")) }
        offer.salaryFrom?.let { put("mesicniMzdaOd", it) }
        offer.salaryTo?.let { put("mesicniMzdaDo", it) }
        offer.hoursPerWeek?.let { put("pocetHodinTydne", it) }
        offer.employer?.let { put("zamestnavatel", JSONObject().put("ico", it.ico).put("nazev", it.name)) }
        put("mistoVykonuPrace", locationToJson(offer.location))
        put("vyhodyVolnehoMista", JSONArray().apply {
            offer.benefits.forEach { benefit ->
                put(JSONObject().put("vyhoda", JSONObject().put("id", "VyhodyVolnehoMista/${benefit.mpsvKod}")))
            }
        })
        offer.url?.let { put("urlAdresa", it) }
        offer.agencyConsent?.let { put("souhlasAgenturyAgentura", it) }
        offer.userConsent?.let { put("souhlasAgenturyUzivatel", it) }
    }

    private fun locationToJson(location: WorkLocation): JSONObject = JSONObject().apply {
        location.type?.let { put("typMistaVykonuPrace", JSONObject().put("id", "TypMistaVykonuPrace/${it.mpsvKod}")) }
        location.municipalityId?.let { put("obec", JSONObject().put("id", it)) }
        put("okresy", JSONArray().apply { location.districts.forEach { put(JSONObject().put("id", it)) } })
        location.addressText?.let { put("adresaText", it) }
        put("pracoviste", JSONArray().apply {
            location.worksiteMunicipalityIds.forEach { municipalityId ->
                put(JSONObject().put("adresa", JSONObject().put("obec", JSONObject().put("id", municipalityId))))
            }
        })
    }

    // --- atomický zápis + index ---

    private fun rebuildIndex() {
        index.clear()
        lines.forEachIndexed { i, line -> linePortalId(line).let { if (it != -1L) index[it] = i } }
    }

    private fun rewriteFile(newLines: List<String>) {
        // snapshot — newLines může být stejná instance jako lines (aliasing!)
        val snapshot = newLines.toList()
        lines.clear()
        lines.addAll(snapshot)
        rebuildIndex()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.parentFile?.mkdirs()
        try {
            tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                newLines.forEach { line ->
                    writer.write(line)
                    writer.write("\n")
                }
            }
            if (!tmp.renameTo(file)) {
                if (file.exists() && !file.delete()) throw IOException("cannot replace ${file.name}")
                if (!tmp.renameTo(file)) throw IOException("rename failed for ${file.name}")
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    companion object {
        private val POLOZKY = JsonReader.Options.of("polozky")
    }
}