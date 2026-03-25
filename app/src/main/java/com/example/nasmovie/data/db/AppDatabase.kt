package com.example.nasmovie.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.nasmovie.data.model.Favorite
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.data.model.WatchProgress

/**
 * 应用数据库
 */
@Database(
    entities = [
        SmbConfig::class,
        Movie::class,
        WatchProgress::class,
        Favorite::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smbConfigDao(): SmbConfigDao
    abstract fun movieDao(): MovieDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        private const val DATABASE_NAME = "nas_movie.db"

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}