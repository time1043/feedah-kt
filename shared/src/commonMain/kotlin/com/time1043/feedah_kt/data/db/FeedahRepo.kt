package com.time1043.feedah_kt.data.db

import com.time1043.feedah_kt.logic.DailyPointerRow
import com.time1043.feedah_kt.logic.DailyStatRow
import com.time1043.feedah_kt.logic.dayBounds
import com.time1043.feedah_kt.logic.nowMs
import com.time1043.feedah_kt.logic.todayLocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class BucketInfo(val id: String, val wordCount: Int)

data class Progress(val round: Int, val pointer: Int, val startedAt: Long)

data class WordRow(
    val bucketId: String,
    val position: Int,
    val text: String,
    val ipa: String,
    val meaning: String,
    val forms: List<String>,
    val flagged: Boolean,
    val flaggedAt: Long,
)

data class RoundWordRow(val position: Int, val reached: Boolean, val flagged: Boolean)

data class RoundHistoryRow(
    val bucketId: String,
    val round: Int,
    val startedAt: Long,
    val finishedAt: Long,
    val updatedAt: Long,
)

/**
 * The single SQL access layer, a statement-for-statement port of the original
 * src/db/repo.ts. Generated SQLDelight rows use Long for INTEGER columns and
 * 0/1 integers for booleans; this class narrows to the domain types above so
 * the UI never sees storage shapes.
 */
class FeedahRepo(internal val db: FeedahDatabase) {
    private val q get() = db.feedahQueries

    private fun toWordRow(row: Word): WordRow = WordRow(
        bucketId = row.bucket_id,
        position = row.position.toInt(),
        text = row.text,
        ipa = row.ipa,
        meaning = row.meaning,
        forms = Json.decodeFromString(ListSerializer(String.serializer()), row.forms),
        flagged = row.flagged != 0L,
        flaggedAt = row.flagged_at,
    )

    // ===== reads: buckets / progress / words =====

    fun listBuckets(): List<BucketInfo> =
        q.selectBuckets().executeAsList().map { BucketInfo(it.id, it.word_count.toInt()) }

    fun getProgress(bucketId: String): Progress {
        val row = q.selectProgress(bucketId).executeAsOneOrNull() ?: return Progress(1, 0, 0)
        return Progress(row.round.toInt(), row.pointer.toInt(), row.started_at)
    }

    fun getWordCount(bucketId: String): Int =
        q.selectWordCount(bucketId).executeAsOneOrNull()?.toInt() ?: 0

    fun getWord(bucketId: String, position: Int): WordRow? =
        q.selectWord(bucketId, position.toLong()).executeAsOneOrNull()?.let(::toWordRow)

    fun getWords(bucketId: String): List<WordRow> =
        q.selectWords(bucketId).executeAsList().map(::toWordRow)

    // ===== writes: settle / rounds / flags =====

    /**
     * Records a hand-settled card: marks the word reached in the current round and
     * moves the monotonic pointer forward. Only the settled position is marked —
     * positions skipped by a drag jump stay unreached. Starting a new round is
     * handled separately by [startNextRound].
     */
    fun advancePointer(bucketId: String, position: Int): Progress {
        val before = getProgress(bucketId)
        if (position <= before.pointer) return before

        val wordCount = getWordCount(bucketId)
        val flagged = q.selectWordFlag(bucketId, position.toLong()).executeAsOneOrNull() ?: 0L
        val settledAt = nowMs()
        val globalPosition = (before.round - 1L) * wordCount + position

        db.transaction {
            q.insertRoundWordReached(
                bucket_id = bucketId,
                round = before.round.toLong(),
                position = position.toLong(),
                flagged = flagged,
                reached_at = settledAt,
                updated_at = settledAt,
            )
            if (before.startedAt == 0L) {
                q.updateProgressPointerAndStartedAt(position.toLong(), settledAt, settledAt, bucketId)
            } else {
                q.updateProgressPointer(position.toLong(), settledAt, bucketId)
            }
            q.upsertDailyPointer(todayLocalDate(), bucketId, globalPosition, settledAt)
        }

        return before.copy(
            pointer = position,
            startedAt = if (before.startedAt > 0) before.startedAt else settledAt,
        )
    }

    /** Moves to the next round after the previous one was fully walked through. */
    fun startNextRound(bucketId: String): Progress {
        val before = getProgress(bucketId)
        val wordCount = getWordCount(bucketId)
        // Idempotent: only advance from a fully walked-through round, so a
        // duplicated settle on the round-end card cannot skip a round.
        if (before.pointer < wordCount) return before
        val now = nowMs()
        val startedAt = if (before.startedAt > 0) before.startedAt else now

        db.transaction {
            q.upsertRoundHistory(
                bucket_id = bucketId,
                round = before.round.toLong(),
                started_at = startedAt,
                finished_at = now,
                updated_at = now,
            )
            q.updateProgressNextRound(
                round = (before.round + 1).toLong(),
                started_at = now,
                progress_updated_at = now,
                bucket_id = bucketId,
            )
        }

        return Progress(before.round + 1, 0, now)
    }

    // ===== reads: flags / rounds =====

    fun getFlaggedWords(bucketId: String): List<WordRow> =
        q.selectFlaggedWords(bucketId).executeAsList().map(::toWordRow)

    /** Words that were flagged during a specific round (historical snapshot). */
    fun getRoundFlaggedWords(bucketId: String, round: Int): List<WordRow> =
        q.selectRoundFlaggedWords(bucketId, round.toLong()).executeAsList().map(::toWordRow)

    fun countFlaggedWords(bucketId: String): Int =
        (q.countFlaggedWords(bucketId).executeAsOneOrNull() ?: 0L).toInt()

    fun getRoundHistory(bucketId: String): List<RoundHistoryRow> =
        q.selectRoundHistory(bucketId).executeAsList().map {
            RoundHistoryRow(
                bucketId = it.bucket_id,
                round = it.round.toInt(),
                startedAt = it.started_at,
                finishedAt = it.finished_at,
                updatedAt = it.updated_at,
            )
        }

    fun getRoundWords(bucketId: String, round: Int): List<RoundWordRow> =
        q.selectRoundWords(bucketId, round.toLong()).executeAsList().map {
            RoundWordRow(it.position.toInt(), it.reached != 0L, it.flagged != 0L)
        }

    // ===== search =====

    /**
     * Translates user input into a SQL LIKE pattern, matching SQL semantics:
     *   %  any run of zero or more characters
     *   _  exactly one character
     * Only % and _ are treated as wildcards; any other character is literal.
     * A query with no wildcard characters is wrapped as a contains-match (%query%),
     * so a plain word search still finds substrings. Examples: `ter%` matches words
     * starting with ter, `%ter` ending with ter, `%ter%` containing ter, `wo%rd`
     * exactly starting with wo and ending with rd.
     */
    private fun toLikePattern(query: String): String {
        val out = StringBuilder()
        var hasWildcard = false
        for (ch in query) {
            when (ch) {
                '%' -> { out.append('%'); hasWildcard = true }
                '_' -> { out.append('_'); hasWildcard = true }
                '\\' -> out.append("\\\\") // literal backslash doubles as the escape char
                else -> out.append(ch)
            }
        }
        return if (hasWildcard) out.toString() else "%$out%"
    }

    fun searchWords(
        bucketId: String,
        query: String,
        matchMeaning: Boolean = false,
        limit: Int = 100,
    ): List<WordRow> {
        // `|` splits the query into alternatives whose results are unioned, so two
        // similar words that a single pattern cannot describe can be looked up at
        // once (`scene|sense`, `sc_ne|s_nse`). Each alternative keeps the %/_ rules.
        val trimmed = query.trim()
        val alternatives = trimmed.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val parts = if (alternatives.isNotEmpty()) alternatives else listOf(trimmed)

        val merged = LinkedHashMap<Long, WordRow>()
        for (part in parts) {
            val pattern = toLikePattern(part)
            // Forms match like the text does: a query may name any inflected form
            // ("donate") and the hit is the headword that owns it ("donation").
            // Forms is a JSON array in a text column, so LIKE runs over its
            // serialized text; the row returned is always the word itself.
            val rows = if (matchMeaning) {
                q.searchWordsByTextOrMeaning(bucketId, pattern, pattern, pattern, limit.toLong())
            } else {
                q.searchWordsByText(bucketId, pattern, pattern, limit.toLong())
            }.executeAsList()
            for (row in rows) if (!merged.containsKey(row.position)) merged[row.position] = toWordRow(row)
        }
        return merged.values.sortedBy { it.position }.take(limit)
    }

    /** Toggles the flag on a word and records it against the current round. */
    fun setFlag(bucketId: String, position: Int, flagged: Boolean) {
        val round = getProgress(bucketId).round
        val now = nowMs()

        db.transaction {
            q.updateWordFlag(
                flagged = if (flagged) 1L else 0L,
                flagged_at = now,
                bucket_id = bucketId,
                position = position.toLong(),
            )
            if (flagged) {
                q.insertRoundWordFlagged(
                    bucket_id = bucketId,
                    round = round.toLong(),
                    position = position.toLong(),
                    updated_at = now,
                )
            } else {
                val row = q.selectRoundWord(bucketId, round.toLong(), position.toLong()).executeAsOneOrNull()
                if (row != null && row.reached == 0L) {
                    q.deleteRoundWord(bucketId, round.toLong(), position.toLong())
                } else if (row != null) {
                    q.updateRoundWordFlagOff(now, bucketId, round.toLong(), position.toLong())
                }
            }
        }
    }

    // ===== stats =====

    fun addDailyTime(day: String, feedSeconds: Long, appSeconds: Long) {
        q.upsertDailyStat(day, feedSeconds, appSeconds, nowMs())
    }

    fun listDailyStats(): List<DailyStatRow> =
        q.selectDailyStats().executeAsList().map {
            DailyStatRow(it.day, it.feed_seconds, it.app_seconds)
        }

    fun listDailyPointers(): List<DailyPointerRow> =
        q.selectDailyPointers().executeAsList().map(::toDailyPointerRow)

    fun getDailyPointersForBucket(bucketId: String): List<DailyPointerRow> =
        q.selectDailyPointersForBucket(bucketId).executeAsList().map(::toDailyPointerRow)

    fun getDailyStat(day: String): DailyStatRow? =
        q.selectDailyStat(day).executeAsOneOrNull()?.let {
            DailyStatRow(it.day, it.feed_seconds, it.app_seconds)
        }

    private fun toDailyPointerRow(row: Daily_pointer): DailyPointerRow =
        DailyPointerRow(row.day, row.bucket_id, row.global_position)

    private data class GlobalRange(val bucketId: String, val from: Long, val to: Long)

    /**
     * Distinct words completed on a local day, across all buckets, rebuilt from
     * the daily pointer high-water marks: the day's globalPosition gain over the
     * previous recorded snapshot names exactly the global positions settled that
     * day — the same rule the stats word counts use, so the queue always matches
     * the number. Per-word reached timestamps are not synced, so the pointer
     * deltas are what survives a reinstall.
     */
    fun getWordsCompletedOn(day: String): List<WordRow> {
        val rows = q.selectDailyPointersByBucketDay().executeAsList() // ordered (bucket_id, day)
        val prevByBucket = HashMap<String, Long>()
        val ranges = ArrayList<GlobalRange>()
        for (row in rows) {
            if (row.day == day) {
                val from = (prevByBucket[row.bucket_id] ?: 0L) + 1
                if (row.global_position >= from) {
                    ranges.add(GlobalRange(row.bucket_id, from, row.global_position))
                }
            }
            prevByBucket[row.bucket_id] = row.global_position
        }

        val out = ArrayList<WordRow>()
        val bucketWords = HashMap<String, List<Word>>() // SQLDelight has no variadic IN
        for (range in ranges) {
            val wordCount = getWordCount(range.bucketId)
            if (wordCount <= 0) continue
            // A day's gain is contiguous global positions; crossing a round
            // boundary wraps the within-round positions, and every round replays
            // the same word set, so distinct positions is all the identity needed.
            val positions = HashSet<Long>()
            var gp = range.from
            while (gp <= range.to) {
                positions.add(((gp - 1) % wordCount) + 1)
                gp++
            }
            val words = bucketWords.getOrPut(range.bucketId) { q.selectWords(range.bucketId).executeAsList() }
            out.addAll(words.filter { it.position in positions }.map(::toWordRow))
        }
        return out
    }

    /**
     * Recovers the start timestamp of a round from historical word settlements
     * or daily pointer snapshots, used to repair missing or zero startedAt.
     */
    fun recoverRoundStartedAt(bucketId: String, round: Int, wordCount: Int): Long {
        val earliestWord = q.minRoundReachedAt(bucketId, round.toLong())
            .executeAsOneOrNull()?.min_reached_at
        if (earliestWord != null && earliestWord > 0) return earliestWord

        val startGlobalPos = (round - 1L) * wordCount + 1
        val pointers = q.selectDailyPointersForBucket(bucketId).executeAsList()
        for (p in pointers) {
            if (p.global_position >= startGlobalPos) {
                return dayBounds(p.day).start
            }
        }
        return 0
    }

    // ===== review cursor =====

    /** Furthest card settled in a review session (0 when none was recorded). */
    fun getReviewCursor(key: String): Int =
        (q.selectReviewCursor(key).executeAsOneOrNull() ?: 0L).toInt()

    /**
     * Records the card a review session settled on. Monotonic: swiping back to
     * re-view earlier cards never lowers the resume position, matching the feed's
     * high-water pointer.
     */
    fun saveReviewCursor(key: String, position: Int) {
        if (position <= 0) return
        q.upsertReviewCursor(key, position.toLong())
    }

    /** Drops a review session's cursor — the queue was finished. */
    fun clearReviewCursor(key: String) {
        q.clearReviewCursor(key)
    }
}
