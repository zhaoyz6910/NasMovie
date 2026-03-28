package com.example.nasmovie.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomWarnings
import androidx.room.Update
import com.example.nasmovie.data.model.Movie
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

    @Query("DELETE FROM watch_progress WHERE movieId IN (:movieIds)")
    suspend fun deleteByMovieIds(movieIds: List<String>)

    @Query("DELETE FROM watch_progress WHERE movieId IN (SELECT id FROM movie WHERE serverId = :serverId)")
    suspend fun deleteByServerId(serverId: Long)

    /**
     * 获取最近观看的电影（使用 JOIN 查询，避免 N+1 问题）
     * 直接返回 Movie 对象，progress 字段会被填充
     * progress 字段是 @Ignore，仅用于运行时显示
     */
    @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH)
    @Query(
        """
        SELECT movie.*, watch_progress.percentage as progress
        FROM movie
        INNER JOIN watch_progress ON movie.id = watch_progress.movieId
        ORDER BY watch_progress.updateTime DESC
        LIMIT :limit
        """
    )
    suspend fun getRecentMoviesWithProgress(limit: Int): List<Movie>
}