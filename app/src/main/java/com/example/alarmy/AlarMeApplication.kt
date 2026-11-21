package com.example.alarmy

import android.app.Application

class AlarMeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.init(this)
    }
}
