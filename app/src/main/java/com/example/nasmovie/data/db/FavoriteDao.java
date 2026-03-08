package com.example.nasmovie.data.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.nasmovie.data.model.Favorite;

import java.util.List;

/**
 * 收藏DAO
 */
@Dao
public interface FavoriteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Favorite favorite);

    @Delete
    void delete(Favorite favorite);

    @Query("DELETE FROM favorite WHERE movieId = :movieId")
    void deleteByMovieId(String movieId);

    @Query("DELETE FROM favorite")
    void deleteAll();

    @Query("SELECT * FROM favorite WHERE movieId = :movieId")
    Favorite getByMovieId(String movieId);

    @Query("SELECT * FROM favorite ORDER BY addTime DESC")
    List<Favorite> getAll();

    @Query("SELECT movieId FROM favorite")
    List<String> getAllMovieIds();

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE movieId = :movieId)")
    boolean isFavorite(String movieId);

    @Query("SELECT COUNT(*) FROM favorite")
    int getCount();
}