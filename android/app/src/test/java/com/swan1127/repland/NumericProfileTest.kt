package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class NumericProfileTest {
    private fun sample(id: String, estimate: Int?, actual: Int?, category: TaskCategory = TaskCategory.COURSE,
                       eligible: Boolean = true) = NumericEstimateSample(id, category, estimate, actual, eligible)
    @Test fun ratios_use_median_not_sum_and_category_samples_never_borrow_global_count() {
        val p = NumericProfileCalculator.estimates(listOf(sample("a",10,20),sample("b",20,30),sample("c",40,40)), emptySet(), emptySet())
        val course = p.single { it.id == "estimate:COURSE" }
        assertEquals(1.5, course.value!!, 0.0001); assertEquals(3, course.count)
        assertEquals(NumericAvailability.USABLE, course.availability)
        assertNull(p.single { it.id == "estimate:OFFICE" }.value)
    }
    @Test fun missing_unconfirmed_zero_incomplete_and_excluded_data_do_not_turn_into_default_scores() {
        val p = NumericProfileCalculator.estimates(listOf(sample("a",null,30),sample("b",0,30),sample("c",30,30,eligible=false),sample("d",30,60)), setOf("d"), emptySet())
        assertTrue(p.all { it.value == null && it.count == 0 && it.availability == NumericAvailability.UNKNOWN })
    }
    @Test fun duplicate_ids_cannot_double_count_and_disabled_retains_inspectable_statistics() {
        val s=sample("a",20,30)
        val p=NumericProfileCalculator.estimates(listOf(s,s),emptySet(),setOf("estimate:COURSE")).single { it.id=="estimate:COURSE" }
        assertEquals(1,p.count); assertEquals(1.5,p.value!!,0.001); assertEquals(NumericAvailability.DISABLED,p.availability)
    }
    @Test fun round_distribution_excludes_pauses_active_and_duplicate_sessions_and_uses_r7_quartiles() {
        val sessions=listOf(10L,20L,40L).mapIndexed { i,m -> ExecutionSession("$i","t","", "p","s",1500,1, m*60000,null,10000000,ExecutionOutcome.CONTINUE) }
        val p=NumericProfileCalculator.rounds(sessions+sessions.first()+sessions.first().copy(id="active",endedAtEpochMillis=null),emptySet(),false)
        assertEquals(3,p.count); assertEquals(20.0,p.value!!,0.001)
        assertEquals(15.0,p.low!!,0.001); assertEquals(30.0,p.high!!,0.001)
    }
    @Test fun one_or_two_samples_are_reference_only_and_cannot_produce_task_advice() {
        val p=NumericProfileCalculator.estimates(listOf(sample("a",20,30)),emptySet(),emptySet()).single { it.id=="estimate:COURSE" }
        assertEquals(NumericAvailability.REFERENCE,p.availability)
        assertNull(NumericProfileCalculator.suggestMinutes(20,p))
    }
    @Test fun suggested_duration_is_relative_to_original_and_unknown_stays_unknown() {
        val p=NumericParameter("estimate:COURSE","估时", "倍",1.5,count=3,sources=listOf("a","b","c"),availability=NumericAvailability.USABLE)
        assertEquals(30,NumericProfileCalculator.suggestMinutes(20,p))
        assertNull(NumericProfileCalculator.suggestMinutes(null,p))
        assertNull(NumericProfileCalculator.suggestMinutes(1440,p))
    }
}
