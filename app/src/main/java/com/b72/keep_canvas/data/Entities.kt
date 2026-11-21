package com.b72.keep_canvas.data

import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Something ML Kit found written in a note: when, where, a phone number, a flight, a parcel. [text] is as written; [millis] is the moment a date resolves to. */
class EntHit(val kind: String, val text: String, val millis: Long = 0L)

/**
 * Finds dates, addresses, phone numbers, flights, parcels and the like in a note's words, on the phone and with no assistant: it is
 * what lets smart layout show "tomorrow at 8" or an address the plain readers did not recognise. English only for now. The small model
 * it needs (a few MB) is fetched from Google the first time, and the work is simply tried again later if that is not possible.
 */
object Entities {
    private val client by lazy { EntityExtraction.getClient(EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()) }
    @Volatile private var ready = false
    @Volatile private var retryAt = 0L

    /** The finds ([] when there are none), or null when the model could not be had right now. */
    suspend fun find(text: String): List<EntHit>? {
        val t = text.take(4000)
        if (t.length < 8) return emptyList()
        if (System.currentTimeMillis() < retryAt) return null
        return try {
            if (!ready) { withTimeout(20_000) { client.downloadModelIfNeeded().await() }; ready = true }
            val out = ArrayList<EntHit>()
            for (a in client.annotate(EntityExtractionParams.Builder(t).build()).await()) {
                val said = t.substring(a.start.coerceIn(0, t.length), a.end.coerceIn(0, t.length)).trim()
                if (said.isEmpty()) continue
                for (e in a.entities) {
                    val h = when (e.type) {
                        Entity.TYPE_DATE_TIME -> EntHit("When", said, (e as? DateTimeEntity)?.timestampMillis ?: 0L)
                        Entity.TYPE_ADDRESS -> EntHit("Where", said)
                        Entity.TYPE_PHONE -> EntHit("Phone", said)
                        Entity.TYPE_EMAIL -> EntHit("Email", said)
                        Entity.TYPE_URL -> EntHit("Link", said)
                        Entity.TYPE_FLIGHT_NUMBER -> EntHit("Flight", said)
                        Entity.TYPE_TRACKING_NUMBER -> EntHit("Tracking", said)
                        else -> null
                    }
                    if (h != null && out.none { it.kind == h.kind && it.text.equals(h.text, true) }) out.add(h)
                }
            }
            out.take(10)
        } catch (e: Throwable) {
            retryAt = System.currentTimeMillis() + 10 * 60_000L
            null
        }
    }
}

/** True when the block has words that have not been searched for entities (or have changed since). */
fun entStale(b: Block): Boolean = (b.kind == Kind.Text || b.kind == Kind.Poster || b.kind == Kind.Info) && b.body.length >= 12 && b.dbl("entH") != b.body.hashCode().toDouble()

fun entToData(hits: List<EntHit>): JsonArray = JsonArray(hits.map { h -> buildJsonObject { put("k", h.kind); put("t", h.text); put("ms", h.millis.toDouble()) } })

fun Block.entities(): List<EntHit> = (data["ent"] as? JsonArray).orEmpty().mapNotNull { e ->
    val o = e as? JsonObject ?: return@mapNotNull null
    val k = (o["k"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
    val t = (o["t"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
    EntHit(k, t, (o["ms"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toLong() ?: 0L)
}
