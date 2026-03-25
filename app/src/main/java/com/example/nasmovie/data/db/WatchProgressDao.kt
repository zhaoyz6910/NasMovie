package com.example.nasmovie.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nasmovie.data.model.WatchProgress

/**
 * 观看进度DAO
 */
@Dao
interface WatchProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: WatchProgress): Long

    @Update
    suspend fun update(progress: WatchProgress)

    @Delete
    suspend fun delete(progress: WatchProgress)

    @Query("DELETE FROM watch_progress WHERE movieId = :movieId")
    suspend fun deleteByMovieId(movieId: String)

    @Query("DELETE FROM watch_progress")
    suspend fun deleteAll()

    @Query("SELECT * FROM watch_progress WHERE movieId = :movieId")
    suspend fun getByMovieId(movieId: String): WatchProgress?

    @Query("SELECT * FROM watch_progress ORDER BY updateTime DESC")
    suspend fun getAll(): List<WatchProgress>

    @Query("SELECT * FROM watch_progress ORDER BY updateTime DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<WatchProgress>

    @Query("SELECT * FROM watch_progress WHERE movieId IN (:movieIds)")
    suspend fun getByMovieIds(movieIds: List<String>): List<WatchProgress>

    @Query("SELECT COUNT(*) FROM watch_progress")
    suspend fun getCount(): Int
}