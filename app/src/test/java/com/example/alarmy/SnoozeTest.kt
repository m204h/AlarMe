package com.example.alarmy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozeTest {

    @Test
    fun testOneTimeAlarmSnoozeStatePreservedWithoutReEnabling() {
        val now = System.currentTimeMillis()
        val snoozeTime = now + 300000L // 5 minutes in future

        // One-time alarm is disabled when ringing starts, but has an active snoozeTriggerTimeMillis
        val snoozedOneTimeAlarm = Alarm(
            id = 201,
            hour = 8,
            minute = 0,
            isEnabled = false,
            daysOfWeek = emptyList(),
            triggerTimeMillis = 0L,
            snoozeTriggerTimeMillis = snoozeTime
        )

        assertFalse("Original one-time alarm remains disabled", snoozedOneTimeAlarm.isEnabled)
        assertTrue("Snooze trigger time is active in the future", snoozedOneTimeAlarm.snoozeTriggerTimeMillis > now)

        // Verify JSON round-trip retains snoozeTriggerTimeMillis
        val jsonStr = Alarm.listToJsonString(listOf(snoozedOneTimeAlarm))
        val restoredAlarm = Alarm.listFromJsonString(jsonStr).first()

        assertEquals(snoozeTime, restoredAlarm.snoozeTriggerTimeMillis)
        assertFalse(restoredAlarm.isEnabled)
    }

    @Test
    fun testRepeatingAlarmSnoozeStatePreserved() {
        val now = System.currentTimeMillis()
        val snoozeTime = now + 300000L // 5 minutes in future

        val snoozedRepeatingAlarm = Alarm(
            id = 202,
            hour = 8,
            minute = 0,
            isEnabled = true,
            daysOfWeek = listOf(2, 3, 4, 5, 6),
            triggerTimeMillis = now + 86400000L,
            snoozeTriggerTimeMillis = snoozeTime
        )

        assertTrue("Repeating alarm remains enabled", snoozedRepeatingAlarm.isEnabled)
        assertEquals(snoozeTime, snoozedRepeatingAlarm.snoozeTriggerTimeMillis)

        // Round-trip check
        val jsonStr = Alarm.listToJsonString(listOf(snoozedRepeatingAlarm))
        val restoredAlarm = Alarm.listFromJsonString(jsonStr).first()

        assertEquals(snoozeTime, restoredAlarm.snoozeTriggerTimeMillis)
        assertTrue(restoredAlarm.isEnabled)
    }
}
