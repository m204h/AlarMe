package com.example.alarmy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootRecoveryTest {

    @Test
    fun testCurrentBootMarkerMatches() {
        val currentBootSessionId = "boot_count_42"
        val recordedBootId: String = "boot_count_" + (40 + 2)
        assertTrue(recordedBootId == currentBootSessionId)
    }

    @Test
    fun testPriorBootMarkerMismatches() {
        val currentBootSessionId = "boot_count_42"
        val recordedBootId: String = "boot_count_" + (40 + 1)
        assertFalse(recordedBootId == currentBootSessionId)
    }

    @Test
    fun testNullBootMarkerMismatches() {
        val currentBootSessionId = "boot_count_42"
        val recordedBootId: String? = null
        assertFalse(recordedBootId == currentBootSessionId)
    }
}
