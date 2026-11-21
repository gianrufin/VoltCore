package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TelemetrySnapshotEntity::class,
        PluggedDeviceEntity::class,
        AlertEventEntity::class,
        SavedStationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VoltCoreDatabase : RoomDatabase() {
    abstract fun bmsDao(): BmsDao

    companion object {
        @Volatile
        private var INSTANCE: VoltCoreDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE telemetry_snapshots ADD COLUMN exactSocPercent REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    "UPDATE telemetry_snapshots SET exactSocPercent = CAST(socPercent AS REAL)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_stations (
                        address TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        protocolName TEXT NOT NULL DEFAULT 'Smart BMS',
                        nominalVoltage REAL NOT NULL DEFAULT 12.8,
                        nominalAh REAL NOT NULL DEFAULT 80.0,
                        capacityWh REAL NOT NULL DEFAULT 1024.0,
                        cellCount INTEGER NOT NULL DEFAULT 0,
                        lastConnectedMs INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): VoltCoreDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    VoltCoreDatabase::class.java,
                    "voltcore_bms.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
