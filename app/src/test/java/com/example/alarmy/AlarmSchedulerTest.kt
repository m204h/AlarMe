package com.example.alarmy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmSchedulerTest {

    @Test
    fun testRepeatingAlarmCalculatesNextFutureOccurrence() {
        val now = Calendar.getInstance()
        val currentDay = now.get(Calendar.DAY_OF_WEEK)
        // Repeat on tomorrow's day of week
        val tomorrowDay = if (currentDay == Calendar.SATURDAY) Calendar.SUNDAY else currentDay + 1

        val alarm = Alarm(
            id = 101,
            hour = 8,
            minute = 30,
            isEnabled = true,
            daysOfWeek = listOf(tomorrowDay)
        )

        val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm, isNewOrUserEdit = false)
        assertTrue("Trigger time must be strictly in the future", triggerTime > System.currentTimeMillis())

        val resultCal = Calendar.getInstance().apply { timeInMillis = triggerTime }
        assertEquals("Target day of week must match repeat configuration", tomorrowDay, resultCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, resultCal.get(Calendar.MINUTE))
        assertEquals(0, resultCal.get(Calendar.SECOND))
    }

    @Test
    fun testOneTimeAlarmUserEditSchedulesTomorrowIfTimePassedToday() {
        val now = Calendar.getInstance()
        // Pick an hour that has already passed today
        val pastHour = if (now.get(Calendar.HOUR_OF_DAY) > 0) now.get(Calendar.HOUR_OF_DAY) - 1 else 0

        val alarm = Alarm(
            id = 102,
            hour = pastHour,
            minute = 0,
            isEnabled = true,
            daysOfWeek = emptyList()
        )

        val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm, isNewOrUserEdit = true)
        assertTrue("Trigger time must be in the future", triggerTime > System.currentTimeMillis())

        val resultCal = Calendar.getInstance().apply { timeInMillis = triggerTime }
        assertEquals(pastHour, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun testOneTimeAlarmRecoveryReturnsNegativeIfTriggerTimePassed() {
        val nowMillis = System.currentTimeMillis()
        val pastTriggerTime = nowMillis - 3600000L // 1 hour ago

        val alarm = Alarm(
            id = 103,
            hour = 7,
            minute = 30,
            isEnabled = true,
            daysOfWeek = emptyList(),
            triggerTimeMillis = pastTriggerTime
        )

        val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm, isNewOrUserEdit = false)
        assertEquals("Expired one-time alarm must return -1L to avoid rescheduling for tomorrow", -1L, triggerTime)
    }

    @Test
    fun testOneTimeAlarmRecoveryPreservesFutureScheduledTime() {
        val nowMillis = System.currentTimeMillis()
        val futureTriggerTime = nowMillis + 7200000L // 2 hours in the future

        val alarm = Alarm(
            id = 104,
            hour = 7,
            minute = 30,
            isEnabled = true,
            daysOfWeek = emptyList(),
            triggerTimeMillis = futureTriggerTime
        )

        val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm, isNewOrUserEdit = false)
        assertEquals("Future trigger time must be preserved during recovery", futureTriggerTime, triggerTime)
    }

    @Test
    fun testAlarmCopyAndEquality() {
        val alarm = Alarm(
            id = 10,
            hour = 12,
            minute = 15,
            isEnabled = true,
            label = "Lunch",
            daysOfWeek = listOf(2, 3, 4),
            soundUri = "content://media/internal/audio/media/1",
            triggerTimeMillis = 1700000000000L
        )

        val copyAlarm = alarm.copy(isEnabled = false, triggerTimeMillis = 0L)
        assertEquals(10, copyAlarm.id)
        assertEquals(12, copyAlarm.hour)
        assertEquals(15, copyAlarm.minute)
        assertEquals(false, copyAlarm.isEnabled)
        assertEquals("Lunch", copyAlarm.label)
        assertEquals(listOf(2, 3, 4), copyAlarm.daysOfWeek)
        assertEquals("content://media/internal/audio/media/1", copyAlarm.soundUri)
        assertEquals(0L, copyAlarm.triggerTimeMillis)
    }

    @Test
    fun testOneTimeAlarmUserEditSchedulesTodayIfTimeInFutureToday() {
        val now = Calendar.getInstance()
        // Pick an hour that is in the future today
        val futureHour = if (now.get(Calendar.HOUR_OF_DAY) < 23) now.get(Calendar.HOUR_OF_DAY) + 1 else 23

        // Only run test if futureHour is indeed greater than current hour (not midnight wrap around)
        if (futureHour > now.get(Calendar.HOUR_OF_DAY)) {
            val alarm = Alarm(
                id = 105,
                hour = futureHour,
                minute = 0,
                isEnabled = true,
                daysOfWeek = emptyList()
            )

            val triggerTime = AlarmScheduler.calculateNextTriggerTime(alarm, isNewOrUserEdit = true)
            assertTrue("Trigger time must be in the future", triggerTime > System.currentTimeMillis())

            val resultCal = Calendar.getInstance().apply { timeInMillis = triggerTime }
            assertEquals(now.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
            assertEquals(futureHour, resultCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, resultCal.get(Calendar.MINUTE))
        }
    }
}
