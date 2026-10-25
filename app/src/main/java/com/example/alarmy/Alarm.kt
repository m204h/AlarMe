package com.example.alarmy

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val label: String = "Alarm"
)
