package it.unipi.di.sam.overwave.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transmissions_table")
data class Transmission(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    // Start time of the transmission in ms
    @ColumnInfo(name = "start_time_millis")
    val startTimeMillis: Long = System.currentTimeMillis(),
    // End time of the transmission in ms
    @ColumnInfo(name = "end_time_millis")
    var endTimeMillis: Long = startTimeMillis,
    // The type of the transmission
    @ColumnInfo(name = "wave")
    val meansType: Int,
    // The frequency of the waves
    @ColumnInfo(name = "frequency")
    val frequency: Int,
    // The number of bytes exchanged.
    @ColumnInfo(name = "bytes")
    val bytes: Long
)