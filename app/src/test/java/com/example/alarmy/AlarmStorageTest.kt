package com.example.alarmy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmStorageTest {

    @Test
    fun testValidEmptyJsonParsesSuccessfully() {
        val result = Alarm.listFromJsonStringResult("[]")
        assertTrue("Valid empty JSON string should produce a successful Result", result.isSuccess)
        assertTrue("Parsed list should be empty", result.getOrNull()?.isEmpty() == true)
    }

    @Test
    fun testMalformedJsonReturnsFailure() {
        val malformedResult = Alarm.listFromJsonStringResult("{invalid json}")
        assertTrue("Malformed JSON string should return a failed Result", malformedResult.isFailure)

        val invalidArrayResult = Alarm.listFromJsonStringResult("[{bad_obj}]")
        assertTrue("Malformed JSON object in array should return a failed Result", invalidArrayResult.isFailure)
    }

    @Test
    fun testValidAlarmListJsonParsesSuccessfully() {
        val alarm = Alarm(
            id = 1,
            hour = 8,
            minute = 30,
            isEnabled = true,
            label = "Morning",
            daysOfWeek = listOf(2, 3),
            soundUri = "content://media/sound",
            triggerTimeMillis = 1000000L,
            snoozeTriggerTimeMillis = 0L
        )
        val jsonStr = Alarm.listToJsonString(listOf(alarm))
        val result = Alarm.listFromJsonStringResult(jsonStr)

        assertTrue(result.isSuccess)
        val list = result.getOrNull()
        assertEquals(1, list?.size)
        assertEquals(alarm, list?.get(0))
    }

    @Test
    fun testListFromJsonStringFallback() {
        val alarms = Alarm.listFromJsonString("invalid json")
        assertTrue("Fallback listFromJsonString should return empty list on malformed JSON", alarms.isEmpty())
    }
}
