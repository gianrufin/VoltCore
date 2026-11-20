package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TelemetrySnapshotEntity::class,
        PluggedDeviceEntity::class,
        AlertEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VoltCoreDatabase : RoomDatabase() {
    abstract fun bmsDao(): BmsDao

    companion object {
        @Volatile
        private var INSTANCE: VoltCoreDatabase? = null

        fun getInstance(context: Context): VoltCoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoltCoreDatabase::class.java,
                    "voltcore_bms.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
