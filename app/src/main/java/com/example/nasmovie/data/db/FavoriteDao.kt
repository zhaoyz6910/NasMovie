package com.example.nasmovie.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.nasmovie.data.model.Favorite

/**
 * 收藏DAO
 */
@Dao
interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite): Long

    @Delete
    suspend fun delete(favorite: Favorite)

    @Query("DELETE FROM favorite WHERE movieId = :movieId")
    suspend fun deleteByMovieId(movieId: String)

    @Query("DELETE FROM favorite")
    suspend fun deleteAll()

    @Query("SELECT * FROM favorite WHERE movieId = :movieId")
    suspend fun getByMovieId(movieId: String): Favorite?

    @Query("SELECT * FROM favorite ORDER BY addTime DESC")
    suspend fun getAll(): List<Favorite>

    @Query("SELECT movieId FROM favorite")
    suspend fun getAllMovieIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE movieId = :movieId)")
    suspend fun isFavorite(movieId: String): Boolean

    @Query("SELECT COUNT(*) FROM favorite")
    suspend fun getCount(): Int

    @Query("DELETE FROM favorite WHERE movieId IN (:movieIds)")
    suspend fun deleteByMovieIds(movieIds: List<String>)

    @Query("DELETE FROM favorite WHERE movieId IN (SELECT id FROM movie WHERE serverId = :serverId)")
    suspend fun deleteByServerId(serverId: Long)
}