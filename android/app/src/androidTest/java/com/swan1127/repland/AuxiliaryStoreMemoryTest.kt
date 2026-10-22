package com.swan1127.repland

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swan1127.repland.data.AuxiliaryStore
import com.swan1127.repland.domain.model.RecallGrade
import java.time.LocalDate
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuxiliaryStoreMemoryTest {
    private val today = LocalDate.of(2026, 10, 22)
    private lateinit var context: IsolatedMemoryContext
    private lateinit var store: AuxiliaryStore

    @Before
    fun setUp() {
        context = IsolatedMemoryContext(ApplicationProvider.getApplicationContext())
        store = AuxiliaryStore(context)
    }

    @After
    fun tearDown() {
        context.deleteStores()
    }

    @Test
    fun memoryReferenceIsOptInAndEmptyIsDifferentFromDisabled() {
        assertFalse(store.memoryReferenceEnabled)
        assertNull(store.memoryReference(today))
        store.memoryReferenceEnabled = true
        val recreated = AuxiliaryStore(context)
        assertTrue(recreated.memoryReferenceEnabled)
        assertNotNull(recreated.memoryReference(today))
        assertTrue(requireNotNull(recreated.memoryReference(today)).isEmpty)
    }

    @Test
    fun aReviewedPointAndPersonalisationSurviveStoreRecreationTogether() {
        assertTrue(store.addReviewPointFromText("task", "微积分", "泰勒余项", today))
        val point = store.reviewPoints().single()
        assertTrue(store.gradeReviewPoint(point.id, RecallGrade.FORGOT, today))
        val recreated = AuxiliaryStore(context)
        val reviewed = recreated.reviewPoints().single()
        assertEquals(1, reviewed.memoryState().reviews)
        assertEquals(1, reviewed.memoryState().lapses)
        assertEquals(today, reviewed.memoryState().lastEventOn)
        assertEquals(0.85, recreated.memoryGain, 0.000001)
        assertEquals("0.85", context.aux.getString("memory-gain-v1", null))
        assertNull(context.settings.getString("gain", null))
    }

    @Test
    fun existingGainIsReadThenMigratedByTheNextSuccessfulAnswer() {
        assertTrue(context.settings.edit().putString("gain", "1.4").commit())
        assertEquals(1.4, store.memoryGain, 0.0)
        assertTrue(store.addReviewPointFromText("task", "title", "content", today))
        assertTrue(store.gradeReviewPoint(store.reviewPoints().single().id, RecallGrade.FUZZY, today.plusDays(1)))
        assertEquals("1.4", context.aux.getString("memory-gain-v1", null))
        assertEquals(1.4, AuxiliaryStore(context).memoryGain, 0.0)
    }

    @Test
    fun legacyQueueKeepsDueDatesAndGraduatedRecordsAcrossTheFirstNewGrade() {
        assertTrue(context.aux.edit().putString("review-points", """
            [{"id":"active","taskId":"task","taskTitle":"title","content":"active point","createdOn":"2026-10-22","stage":3},
             {"id":"graduated","taskId":"task","taskTitle":"title","content":"graduated point","createdOn":"2026-10-22","stage":5}]
        """.trimIndent()).commit())
        val active = store.reviewPoints().first { it.id == "active" }
        assertEquals(today.plusDays(7), active.dueOn)
        assertFalse(store.gradeReviewPoint("graduated", RecallGrade.FORGOT, today.plusDays(30)))
        assertTrue(store.gradeReviewPoint("active", RecallGrade.FUZZY, active.dueOn))
        val recreated = AuxiliaryStore(context)
        val graduated = recreated.reviewPoints().first { it.id == "graduated" }
        assertTrue(graduated.isGraduated)
        assertNull(graduated.memory)
        assertEquals(5, graduated.stage)
        assertEquals(4, recreated.reviewPoints().first { it.id == "active" }.memoryState().reviews)
        recreated.memoryReferenceEnabled = true
        assertEquals(1, requireNotNull(recreated.memoryReference(today.plusDays(30))).signals.size)
    }

    @Test
    fun failedCommitDoesNotAcknowledgeOrDoubleApplyAnAnswerOnRetry() {
        assertTrue(store.addReviewPointFromText("task", "title", "content", today))
        val point = store.reviewPoints().single()
        context.failNextAuxCommit = true
        assertFalse(store.gradeReviewPoint(point.id, RecallGrade.FORGOT, today))
        assertEquals(point, AuxiliaryStore(context).reviewPoints().single())
        assertEquals(1.0, store.memoryGain, 0.0)
        assertTrue(store.gradeReviewPoint(point.id, RecallGrade.FORGOT, today))
        assertEquals(1, store.reviewPoints().single().memoryState().reviews)
        assertEquals(0.85, store.memoryGain, 0.000001)
    }

    @Test
    fun clearingFromAnotherStoreResetsMemoryAndMakesAStaleAnswerFail() {
        assertTrue(context.aux.edit().putString("appearance", "DARK").commit())
        assertTrue(context.settings.edit().putString("gain", "1.8").commit())
        store.memoryReferenceEnabled = true
        assertTrue(store.addReviewPointFromText("task", "title", "content", today))
        val pointId = store.reviewPoints().single().id
        assertTrue(store.gradeReviewPoint(pointId, RecallGrade.RECALLED, today.plusDays(1)))
        AuxiliaryStore(context).clearLearningMemory()
        assertFalse(store.gradeReviewPoint(pointId, RecallGrade.FORGOT, today.plusDays(2)))
        assertTrue(store.reviewPoints().isEmpty())
        assertFalse(store.memoryReferenceEnabled)
        assertNull(store.memoryReference(today))
        assertEquals(1.0, store.memoryGain, 0.0)
        assertTrue(store.learningMemorySnapshot().points.isEmpty())
        assertEquals("DARK", context.aux.getString("appearance", null))
    }

    private class IsolatedMemoryContext(base: Context) : ContextWrapper(base) {
        private val prefix = "memory-test-${UUID.randomUUID()}"
        var failNextAuxCommit = false
        val aux: SharedPreferences get() = getSharedPreferences("repland-aux", MODE_PRIVATE)
        val settings: SharedPreferences get() = getSharedPreferences("repland-memory-v1", MODE_PRIVATE)

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val actual = super.getSharedPreferences("$prefix-$name", mode)
            if (name != "repland-aux") return actual
            return object : SharedPreferences by actual {
                override fun edit(): SharedPreferences.Editor {
                    val editor = actual.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                            editor.putString(key, value)
                            return this
                        }

                        override fun commit(): Boolean {
                            if (!failNextAuxCommit) return editor.commit()
                            failNextAuxCommit = false
                            // Android updates memory before a disk commit reports failure.
                            editor.apply()
                            return false
                        }
                    }
                }
            }
        }

        fun deleteStores() {
            listOf("repland-aux", "repland-memory-v1").forEach { name ->
                super.getSharedPreferences("$prefix-$name", MODE_PRIVATE).edit().clear().commit()
                super.deleteSharedPreferences("$prefix-$name")
            }
        }
    }
}
