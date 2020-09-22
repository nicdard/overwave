package it.unipi.di.sam.overwave.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TransmissionDatabaseDao {

    @Insert
    fun insert(transmission: Transmission)

    @Update
    fun update(transmission: Transmission)

    @Query("SELECT * FROM transmissions_table WHERE id = :id")
    fun getById(id: Long): Transmission?

    @Query("DELETE FROM transmissions_table")
    fun clear()

    @Query("SELECT * FROM transmissions_table WHERE wave = :wave")
    fun getByWave(wave: Int): List<Transmission>

    @Query("SELECT * FROM transmissions_table ORDER BY id DESC LIMIT 1")
    fun getCurrent(): Transmission?

    @Query("SELECT * FROM transmissions_table ORDER BY id DESC")
    fun getAllTransmissions(): LiveData<List<Transmission>>
}
