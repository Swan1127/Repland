package com.swan1127.repland

import com.swan1127.repland.domain.model.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PlacementValidatorTest {
    private val date = LocalDate.of(2026, 10, 3)
    private fun candidate(track: String = "focus") = PlannedSegment("new", "task", date, 480, 510, trackId = track)
    private fun block(kind: TimeBlockKind) = WeeklyTimeBlock("fixed", "保护时段", kind, date.dayOfWeek, 480, 540, null, 1, 1)

    @Test fun hard_constraints_reject_new_work_even_on_another_track() {
        for (kind in listOf(TimeBlockKind.COURSE, TimeBlockKind.REST, TimeBlockKind.OTHER)) {
            assertThrows(IllegalArgumentException::class.java) {
                PlacementValidator.requireValid(candidate("parallel"), emptyList(), listOf(block(kind)), emptyList(), null)
            }
        }
    }
    @Test fun available_time_is_not_a_blocker() {
        PlacementValidator.requireValid(candidate(), emptyList(), listOf(block(TimeBlockKind.AVAILABLE)), emptyList(), null)
    }
    @Test fun adjacent_placement_is_valid_but_same_track_overlap_is_not() {
        val previous = candidate().copy(id = "previous", startMinute = 450, endMinute = 480)
        PlacementValidator.requireValid(candidate(), listOf(previous), emptyList(), emptyList(), null)
        assertThrows(IllegalArgumentException::class.java) {
            PlacementValidator.requireValid(candidate(), listOf(previous.copy(endMinute = 481)), emptyList(), emptyList(), null)
        }
    }
    @Test fun locked_work_blocks_all_tracks() {
        assertThrows(IllegalArgumentException::class.java) {
            PlacementValidator.requireValid(candidate(), listOf(candidate("other").copy(id = "lock", isLocked = true)), emptyList(), emptyList(), null)
        }
    }
    @Test fun blocked_date_exception_is_checked() {
        assertThrows(IllegalArgumentException::class.java) {
            PlacementValidator.requireValid(candidate(), emptyList(), emptyList(),
                listOf(DateOverride("blocked", "例外", DateOverrideType.BLOCKED, date, 480, 540, 1, 1)), null)
        }
    }
}
