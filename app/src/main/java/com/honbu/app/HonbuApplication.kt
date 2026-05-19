package com.honbu.app

import android.app.Application
import com.honbu.app.data.db.AppDatabase
import com.honbu.app.data.preferences.SoundPreferences

class HonbuApplication : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val soundPreferences by lazy { SoundPreferences(this) }
}
