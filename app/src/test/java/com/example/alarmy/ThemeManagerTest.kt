package com.example.alarmy

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeManagerTest {

    @Test
    fun testThemeConstants() {
        assertEquals("light", ThemeManager.THEME_LIGHT)
        assertEquals("dark", ThemeManager.THEME_DARK)
    }
}
