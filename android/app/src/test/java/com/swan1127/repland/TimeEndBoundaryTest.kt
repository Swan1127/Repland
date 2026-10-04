package com.swan1127.repland

import com.swan1127.repland.domain.model.TimeBlockValidator
import org.junit.Assert.*
import org.junit.Test

class TimeEndBoundaryTest {
    @Test fun day_end_round_trips_only_as_an_end() {
        assertEquals("24:00", TimeBlockValidator.formatTime(1440))
        assertEquals(1440, TimeBlockValidator.parseEndTime(" 24:00 "))
        assertNull(TimeBlockValidator.parseTime("24:00"))
        assertEquals(1439, TimeBlockValidator.parseEndTime("23:59"))
    }
    @Test fun invalid_day_end_and_clock_values_are_rejected() {
        listOf("24:01", "24:30", "25:00", "23:60", "-1:00", "", "0:00").forEach {
            assertNull(it, TimeBlockValidator.parseEndTime(it))
        }
    }
}
