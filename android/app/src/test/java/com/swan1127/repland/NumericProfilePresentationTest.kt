package com.swan1127.repland

import com.swan1127.repland.ui.profile.numericDisplayNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class NumericProfilePresentationTest {
    @Test fun tiny_positive_execution_minutes_do_not_become_a_zero_minute_reference() {
        assertEquals("<0.01",numericDisplayNumber(1.0/60000))
        assertEquals("<0.01",numericDisplayNumber(0.009))
        assertEquals("0.01",numericDisplayNumber(0.01))
        assertEquals("1.5",numericDisplayNumber(1.5))
        assertEquals("0",numericDisplayNumber(0.0))
        assertEquals("未知",numericDisplayNumber(null))
    }
}
