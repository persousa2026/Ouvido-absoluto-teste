package com.ouvidoabsoluto.ai

import android.app.Application
import androidx.room.Room
import com.ouvidoabsoluto.ai.data.AppDatabase
import com.ouvidoabsoluto.ai.data.TonalityRepository

class OuvidoAbsolutoApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "ouvido_absoluto.db").build()
    }
    val repository: TonalityRepository by lazy { TonalityRepository(database.tonalityAnalysisDao()) }
}
