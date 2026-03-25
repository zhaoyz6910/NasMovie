package com.example.nasmovie.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nasmovie.data.model.Movie

/**
 * 电影DAO
 */
@Dao
interface MovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: Movie): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<Movie>)

    @Update
    suspend fun update(movie: Movie)

    @Delete
    suspend fun delete(movie: Movie)

    @Query("DELETE FROM movie WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM movie WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("DELETE FROM movie")
    suspend fun deleteAll()

    @Query("SELECT * FROM movie WHERE id = :id")
    suspend fun getById(id: String): Movie?

    @Query("SELECT * FROM movie ORDER BY title ASC")
    suspend fun getAll(): List<Movie>

    @Query("SELECT * FROM movie ORDER BY addTime DESC")
    suspend fun getAllByAddTime(): List<Movie>

    @Query("SELECT * FROM movie WHERE serverId = :serverId ORDER BY title ASC")
    suspend fun getByServerId(serverId: String): List<Movie>

    @Query("SELECT * FROM movie WHERE LOWER(title) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(originalTitle) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(actors) LIKE '%' || LOWER(:keyword) || '%'")
    suspend fun search(keyword: String): List<Movie>

    @Query("SELECT * FROM movie WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<Movie>

    @Query("SELECT COUNT(*) FROM movie")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM movie WHERE serverId = :serverId")
    suspend fun getCountByServerId(serverId: String): Int

    // ========== 排序查询 ==========

    @Query("SELECT * FROM movie ORDER BY year DESC")
    suspend fun getAllByYearDesc(): List<Movie>

    @Query("SELECT * FROM movie ORDER BY rating DESC")
    suspend fun getAllByRatingDesc(): List<Movie>

    @Query("SELECT * FROM movie ORDER BY duration DESC")
    suspend fun getAllByDurationDesc(): List<Movie>

    @Query("SELECT * FROM movie ORDER BY fileSize DESC")
    suspend fun getAllByFileSizeDesc(): List<Movie>
}