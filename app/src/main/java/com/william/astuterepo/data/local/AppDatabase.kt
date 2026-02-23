package com.william.astuterepo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.william.astuterepo.data.model.AppEntry

@Database(
    entities = [AppEntry::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}
