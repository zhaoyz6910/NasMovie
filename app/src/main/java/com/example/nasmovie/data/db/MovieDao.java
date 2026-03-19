package com.example.nasmovie.data.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.nasmovie.data.model.Movie;

import java.util.List;

/**
 * 电影DAO
 */
@Dao
public interface MovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Movie movie);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Movie> movies);

    @Update
    void update(Movie movie);

    @Delete
    void delete(Movie movie);

    @Query("DELETE FROM movie WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM movie WHERE serverId = :serverId")
    void deleteByServerId(String serverId);

    @Query("DELETE FROM movie")
    void deleteAll();

    @Query("SELECT * FROM movie WHERE id = :id")
    Movie getById(String id);

    @Query("SELECT * FROM movie ORDER BY title ASC")
    List<Movie> getAll();

    @Query("SELECT * FROM movie ORDER BY addTime DESC")
    List<Movie> getAllByAddTime();

    @Query("SELECT * FROM movie WHERE serverId = :serverId ORDER BY title ASC")
    List<Movie> getByServerId(String serverId);

    @Query("SELECT * FROM movie WHERE LOWER(title) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(originalTitle) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(actors) LIKE '%' || LOWER(:keyword) || '%'")
    List<Movie> search(String keyword);

    @Query("SELECT * FROM movie WHERE id IN (:ids)")
    List<Movie> getByIds(List<String> ids);

    @Query("SELECT COUNT(*) FROM movie")
    int getCount();

    @Query("SELECT COUNT(*) FROM movie WHERE serverId = :serverId")
    int getCountByServerId(String serverId);

    // ========== 排序查询 ==========

    @Query("SELECT * FROM movie ORDER BY year DESC")
    List<Movie> getAllByYearDesc();

    @Query("SELECT * FROM movie ORDER BY rating DESC")
    List<Movie> getAllByRatingDesc();

    @Query("SELECT * FROM movie ORDER BY duration DESC")
    List<Movie> getAllByDurationDesc();

    @Query("SELECT * FROM movie ORDER BY fileSize DESC")
    List<Movie> getAllByFileSizeDesc();
}