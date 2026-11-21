package com.example.alarmy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeChangeTest {

    @Test
    fun testOneTimeAlarmRecalculatedOnTimeChangeForward() {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        // Choose an hour earlier today (already passed)
        val pastHour = if (currentHour > 0) currentHour - 1 else 0

        val alarm = Alarm(
            id = 301,
            hour = pastHour,
            minute = 0,
            isEnabled = true,
            daysOfWeek = emptyList(),
            triggerTimeMillis = System.currentTimeMillis() - 3600000L // old passed trigger time
        )

        // On time change recalculation, since pastHour is in the past today, it should reschedule for tomorrow at pastHour
        val recalculatedTime = AlarmScheduler.calculateNextTriggerTime(
            alarm = alarm,
            isTimeOrTimezoneChange = true
        )

        assertTrue("Recalculated time must be strictly in the future", recalculatedTime > System.currentTimeMillis())

        val resultCal = Calendar.getInstance().apply { timeInMillis = recalculatedTime }
        assertEquals(pastHour, resultCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCal.get(Calendar.MINUTE))
    }

    @Test
    fun testOneTimeAlarmRecalculatedOnTimeChangeBackward() {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        // Choose an hour later today
        val futureHour = if (currentHour < 23) currentHour + 1 else 23

        if (futureHour > currentHour) {
            val alarm = Alarm(
                id = 302,
                hour = futureHour,
                minute = 0,
                isEnabled = true,
                daysOfWeek = emptyList(),
                triggerTimeMillis = 0L
            )

            val recalculatedTime = AlarmScheduler.calculateNextTriggerTime(
                alarm = alarm,
                isTimeOrTimezoneChange = true
            )

            assertTrue("Recalculated time must be in the future", recalculatedTime > System.currentTimeMillis())

            val resultCal = Calendar.getInstance().apply { timeInMillis = recalculatedTime }
            assertEquals(now.get(Calendar.DAY_OF_YEAR), resultCal.get(Calendar.DAY_OF_YEAR))
            assertEquals(futureHour, resultCal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, resultCal.get(Calendar.MINUTE))
        }
    }

    @Test
    fun testOneTimeAlarmRecalculatedOnTimezoneChange() {
        val defaultTz = TimeZone.getDefault()
        try {
            // Set timezone to UTC
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            val alarm = Alarm(
                id = 303,
                hour = 9,
                minute = 30,
                isEnabled = true,
                daysOfWeek = emptyList()
            )

            val triggerUtc = AlarmScheduler.calculateNextTriggerTime(
                alarm = alarm,
                isTimeOrTimezoneChange = true
            )

            val calUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = triggerUtc }
            assertEquals(9, calUtc.get(Calendar.HOUR_OF_DAY))
            assertEquals(30, calUtc.get(Calendar.MINUTE))

            // Switch timezone to EST (UTC-5)
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

            val triggerEst = AlarmScheduler.calculateNextTriggerTime(
                alarm = alarm,
                isTimeOrTimezoneChange = true
            )

            val calEst = Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).apply { timeInMillis = triggerEst }
            assertEquals(9, calEst.get(Calendar.HOUR_OF_DAY))
            assertEquals(30, calEst.get(Calendar.MINUTE))
        } finally {
            TimeZone.setDefault(defaultTz)
        }
    }
}
