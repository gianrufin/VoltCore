package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BmsDao {
    @Query("SELECT * FROM telemetry_snapshots ORDER BY timestampMs DESC LIMIT 1")
    fun observeLatestSnapshot(): Flow<TelemetrySnapshotEntity?>

    @Query("SELECT * FROM telemetry_snapshots ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecentSnapshots(limit: Int = 60): Flow<List<TelemetrySnapshotEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: TelemetrySnapshotEntity)

    @Query("DELETE FROM telemetry_snapshots WHERE id NOT IN (SELECT id FROM telemetry_snapshots ORDER BY timestampMs DESC LIMIT 500)")
    suspend fun pruneOldSnapshots()

    @Query("DELETE FROM telemetry_snapshots")
    suspend fun clearSnapshots()

    @Query("SELECT * FROM plugged_devices ORDER BY isActive DESC, updatedAtMs DESC")
    fun observePluggedDevices(): Flow<List<PluggedDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPluggedDevice(device: PluggedDeviceEntity)

    @Update
    suspend fun updatePluggedDevice(device: PluggedDeviceEntity)

    @Query("DELETE FROM plugged_devices WHERE id = :id")
    suspend fun deletePluggedDeviceById(id: Long)

    @Query("SELECT * FROM alert_events ORDER BY timestampMs DESC LIMIT 50")
    fun observeAlertEvents(): Flow<List<AlertEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlertEvent(event: AlertEventEntity)

    @Query("DELETE FROM alert_events")
    suspend fun clearAlerts()

    @Query("SELECT * FROM saved_stations ORDER BY lastConnectedMs DESC")
    fun observeSavedStations(): Flow<List<SavedStationEntity>>

    @Query("SELECT * FROM saved_stations WHERE address = :address LIMIT 1")
    suspend fun getSavedStation(address: String): SavedStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedStation(station: SavedStationEntity)

    @Query("DELETE FROM saved_stations WHERE address = :address")
    suspend fun deleteSavedStation(address: String)
}
