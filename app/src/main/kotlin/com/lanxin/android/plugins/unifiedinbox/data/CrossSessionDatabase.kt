package com.lanxin.android.plugins.unifiedinbox.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CrossSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CrossSessionDatabase : RoomDatabase() {
    abstract fun crossSessionDao(): CrossSessionDao

    companion object {
        const val DB_NAME = "lanxin_unified_inbox.db"

        @Volatile
        private var INSTANCE: CrossSessionDatabase? = null

        fun getInstance(context: Context): CrossSessionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CrossSessionDatabase::class.java,
                    DB_NAME
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
