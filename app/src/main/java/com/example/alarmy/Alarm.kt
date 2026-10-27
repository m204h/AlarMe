package com.example.alarmy

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val label: String = "Alarm",
    val daysOfWeek: List<Int> = emptyList() // Calendar day constants: 1=Sun, 2=Mon, ... 7=Sat
)
