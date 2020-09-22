package it.unipi.di.sam.overwave.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Transmission::class], version = 3, exportSchema = false)
abstract class TransmissionDatabase : RoomDatabase() {

    abstract val transmissionDatabaseDao: TransmissionDatabaseDao

    companion object {
        @Volatile
        private var INSTANCE: TransmissionDatabase? = null

        fun getInstance(context: Context): TransmissionDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context,
                        TransmissionDatabase::class.java,
                        "transmission_samples_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}