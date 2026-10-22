package com.swan1127.repland.domain.model

import com.swan1127.repland.data.room.SubTaskJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip coverage of the hand-rolled [SubTaskJson] codec. There is no JSON library in the
 * project, so both directions are written by hand; the decode path reuses [com.swan1127.repland.data.json.StrictJson].
 *
 * The mutation harness perturbs [com.swan1127.repland.data.room.SubTaskJson] and these tests say
 * whether a regression is caught. A codec bug here is silent at runtime (a corrupt string just
 * yields an empty list), so the tests have to be the ones that notice.
 */
class SubTaskJsonTest {

    private fun roundTrip(subs: List<SubTask>): List<SubTask> = SubTaskJson.decode(SubTaskJson.encode(subs))

    @Test
    fun `empty list encodes to the empty array and decodes back`() {
        assertEquals("[]", SubTaskJson.encode(emptyList()))
        assertTrue(SubTaskJson.decode("[]").isEmpty())
    }

    @Test
    fun `a plain subtask round-trips with its fields intact`() {
        val sub = SubTask(id = "a1", title = "Read chapter 3", isDone = true, createdAtEpochMillis = 1_000L)
        val decoded = roundTrip(listOf(sub))
        assertEquals(1, decoded.size)
        assertEquals(sub, decoded[0])
    }

    @Test
    fun `titles with quotes, backslashes and non-ascii round-trip`() {
        val sub = SubTask(
            id = "b2",
            title = "他说 \"hi\" \\ 然后 完成 80%",
            isDone = false,
            createdAtEpochMillis = 9_999L,
        )
        val decoded = roundTrip(listOf(sub))
        assertEquals(sub.title, decoded[0].title)
    }

    @Test
    fun `a list of several subtasks keeps order and done flags`() {
        val subs = listOf(
            SubTask("a", "one", isDone = true, createdAtEpochMillis = 1L),
            SubTask("b", "two", isDone = false, createdAtEpochMillis = 2L),
            SubTask("c", "three", isDone = true, createdAtEpochMillis = 3L),
        )
        val decoded = roundTrip(subs)
        assertEquals(subs.map { it.id }, decoded.map { it.id })
        assertEquals(subs.map { it.isDone }, decoded.map { it.isDone })
    }

    @Test
    fun `a large createdAt survives the double-to-long decode exactly`() {
        // 1.7e12 exceeds Int.MAX_VALUE, so a decode that truncates through toInt() would corrupt it.
        val sub = SubTask(id = "big", title = "x", isDone = false, createdAtEpochMillis = 1_700_000_000_000L)
        assertEquals(sub.createdAtEpochMillis, roundTrip(listOf(sub))[0].createdAtEpochMillis)
    }

    @Test
    fun `encode escapes control characters so decode survives`() {
        val sub = SubTask(id = "ctl", title = "line1\nline2\ttab", isDone = false, createdAtEpochMillis = 5L)
        val decoded = roundTrip(listOf(sub))
        assertEquals(sub.title, decoded[0].title)
    }

    @Test
    fun `a corrupt string decodes to an empty list rather than throwing`() {
        assertTrue(SubTaskJson.decode("{not valid json").isEmpty())
        assertTrue(SubTaskJson.decode(null).isEmpty())
        assertTrue(SubTaskJson.decode("").isEmpty())
    }
}
