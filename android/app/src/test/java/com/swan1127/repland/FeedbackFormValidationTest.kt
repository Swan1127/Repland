package com.swan1127.repland

import com.swan1127.repland.ui.tasks.FeedbackFormValidation
import org.junit.Assert.*
import org.junit.Test

class FeedbackFormValidationTest {
    @Test fun empty_is_unknown_but_nonempty_invalid_input_is_not() {
        assertTrue(FeedbackFormValidation.validNumber("", 1..1440))
        assertTrue(FeedbackFormValidation.validNumber(" 45 ", 1..1440))
        listOf("-1", "+1", "1.5", "abc", "99999999999999999999", "0", "1441").forEach {
            assertFalse(it, FeedbackFormValidation.validNumber(it, 1..1440))
        }
        assertTrue(FeedbackFormValidation.validNumber("1440", 1..1440))
    }
    @Test fun partial_progress_is_required_and_cannot_be_zero_or_complete() {
        listOf("", " ", "0", "100", "-1", "999999999999999").forEach {
            assertFalse(it, FeedbackFormValidation.validNumber(it, 1..99, required = true))
        }
        assertTrue(FeedbackFormValidation.validNumber("1", 1..99, required = true))
        assertTrue(FeedbackFormValidation.validNumber("99", 1..99, required = true))
        assertTrue(FeedbackFormValidation.validNumber("0", 0..100))
        assertTrue(FeedbackFormValidation.validNumber("100", 0..100))
    }
}
