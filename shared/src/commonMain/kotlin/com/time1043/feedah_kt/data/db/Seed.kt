package com.time1043.feedah_kt.data.db

import com.time1043.feedah_kt.logic.nowMs
import feedah_kt.shared.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SeedWord(
    val position: Long,
    val word: String,
    val ipa: String = "",
    val meaning: String = "",
    val forms: List<String> = emptyList(),
)

@Serializable
data class SeedFile(val name: String, val words: List<SeedWord>)

/** The active bucket before the user picks one; matches settings' default. */
const val DEFAULT_BUCKET_ID = "2050"

/**
 * Each bucket JSON is bundled into the app at build time (composeResources).
 * Seeding runs on first DB open and on every later cold start; it is idempotent
 * against the device's stored bucket.word_count, so a rebuild that changes a
 * bucket's word count re-seeds that bucket on the user's device (flags reset),
 * while an unchanged bundle leaves existing data untouched.
 */
suspend fun loadBundledBuckets(): List<SeedFile> = listOf(
    Json.decodeFromString<SeedFile>(Res.readBytes("files/data/2050.json").decodeToString()),
    Json.decodeFromString<SeedFile>(Res.readBytes("files/data/700.json").decodeToString()),
    Json.decodeFromString<SeedFile>(Res.readBytes("files/data/370.json").decodeToString()),
)

private val formsJson = Json

/** Idempotent: inserts missing buckets, refreshes words when the data changed. */
fun seedBuckets(db: FeedahDatabase, files: List<SeedFile>) {
    for (file in files) {
        val existing = db.feedahQueries.selectWordCount(file.name).executeAsOneOrNull()

        if (existing != null && existing == file.words.size.toLong()) {
            db.feedahQueries.insertBucketProgressIfAbsent(file.name, nowMs())
            continue
        }

        db.transaction {
            db.feedahQueries.upsertBucket(file.name, file.words.size.toLong())
            if (existing != null) {
                // Word count changed → full re-insert; flags reset (flagged=0 in
                // insertWord), while bucket_progress is preserved (do-nothing below).
                db.feedahQueries.deleteWords(file.name)
            }
            for (w in file.words) {
                db.feedahQueries.insertWord(
                    bucket_id = file.name,
                    position = w.position,
                    text = w.word,
                    ipa = w.ipa,
                    meaning = w.meaning,
                    forms = formsJson.encodeToString(w.forms),
                )
            }
            db.feedahQueries.insertBucketProgressIfAbsent(file.name, nowMs())
        }
    }
}
