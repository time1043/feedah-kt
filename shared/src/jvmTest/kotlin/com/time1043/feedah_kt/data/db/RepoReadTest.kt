package com.time1043.feedah_kt.data.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RepoReadTest {
    private fun newRepo(): FeedahRepo {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FeedahDatabase.Schema.create(driver)
        val db = FeedahDatabase(driver)
        seedBuckets(
            db,
            listOf(
                SeedFile(
                    "t1",
                    listOf(
                        SeedWord(1, "scene", meaning = "n. 场面、情景"),
                        SeedWord(2, "sense", meaning = "n. 感觉"),
                        SeedWord(3, "scent", meaning = "n. 气味"),
                        SeedWord(4, "cent", meaning = "n. 分币"),
                        SeedWord(5, "donate", meaning = "v. 捐赠"),
                    ),
                ),
            ),
        )
        return FeedahRepo(db)
    }

    @Test
    fun plainQueryIsAContainsMatch() {
        val repo = newRepo()
        // "sen" appears inside "sense" only (scent is s-c-e-n-t, no "sen" run).
        assertEquals(listOf(2), repo.searchWords("t1", "sen").map { it.position })
    }

    @Test
    fun wildcardsFollowSqlSemantics() {
        val repo = newRepo()
        assertEquals(listOf(3, 4), repo.searchWords("t1", "%ent").map { it.position }) // suffix
        assertEquals(listOf(1, 2), repo.searchWords("t1", "s%e").map { it.position }) // s...e
        assertEquals(listOf(4), repo.searchWords("t1", "c_nt").map { it.position }) // c?nt
        assertEquals(listOf(3), repo.searchWords("t1", "s%t").map { it.position }) // s...t
        assertEquals(emptyList(), repo.searchWords("t1", "z%q").map { it.position })
    }

    @Test
    fun matchesAreCaseInsensitive() {
        val repo = newRepo()
        assertEquals(listOf(2), repo.searchWords("t1", "SEN").map { it.position })
    }

    @Test
    fun formsColumnMatchesAndReturnsHeadword() {
        val repo = newRepo()
        // "donated" is not a headword; it appears only inside forms JSON.
        repo.db.feedahQueries.insertWord("t1", 5, "donate", "", "v. 捐赠", """["donated","donating"]""")
        val hits = repo.searchWords("t1", "donated")
        assertEquals(listOf(5), hits.map { it.position })
        assertEquals("donate", hits[0].text)
    }

    @Test
    fun meaningOnlyMatchesWithMatchMeaning() {
        val repo = newRepo()
        assertEquals(emptyList(), repo.searchWords("t1", "感觉"))
        assertEquals(listOf(2), repo.searchWords("t1", "感觉", matchMeaning = true).map { it.position })
    }

    @Test
    fun pipeAlternativesUnionResults() {
        val repo = newRepo()
        // scen → scene, scent (prefix); c_nt → cent.
        assertEquals(
            listOf(1, 3, 4),
            repo.searchWords("t1", "scen|c_nt").map { it.position },
        )
    }

    @Test
    fun limitTruncatesAcrossAlternatives() {
        val repo = newRepo()
        assertEquals(2, repo.searchWords("t1", "s", limit = 2).size)
    }

    @Test
    fun roundFlaggedWordsIsAHistoricalSnapshot() {
        val repo = newRepo()
        repo.setFlag("t1", 2, true)
        repo.advancePointer("t1", 10)
        repo.startNextRound("t1")
        // In round 2 the user unflags it.
        repo.setFlag("t1", 2, false)

        assertEquals(listOf(2), repo.getRoundFlaggedWords("t1", round = 1).map { it.position })
        assertEquals(emptyList(), repo.getFlaggedWords("t1").map { it.position })
    }

    @Test
    fun getRoundWordsOnlyContainsMarkedPositions() {
        val repo = newRepo()
        repo.advancePointer("t1", 4)
        repo.setFlag("t1", 2, true)
        repo.advancePointer("t1", 10)
        repo.startNextRound("t1")

        val rows = repo.getRoundWords("t1", round = 1)
        // Only settled/flagged positions exist: 2 (flag), 4 and 10 (reached).
        assertEquals(listOf(2, 4, 10), rows.map { it.position })
        assertTrue(rows.first { it.position == 2 }.let { it.flagged && !it.reached })
        assertTrue(rows.first { it.position == 4 }.reached)

        val history = repo.getRoundHistory("t1")
        assertEquals(1, history.size)
        assertEquals(1, history[0].round)
    }

    @Test
    fun countFlaggedWordsCountsCurrentFlags() {
        val repo = newRepo()
        repo.setFlag("t1", 1, true)
        repo.setFlag("t1", 3, true)
        repo.setFlag("t1", 3, false)
        assertEquals(1, repo.countFlaggedWords("t1"))
    }
}
