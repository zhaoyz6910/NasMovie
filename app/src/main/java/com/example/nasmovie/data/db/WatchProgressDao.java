package com.example.nasmovie.data.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.nasmovie.data.model.WatchProgress;

import java.util.List;

/**
 * 观看进度DAO
 */
@Dao
public interface WatchProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(WatchProgress progress);

    @Update
    void update(WatchProgress progress);

    @Delete
    void delete(WatchProgress progress);

    @Query("DELETE FROM watch_progress WHERE movieId = :movieId")
    void deleteByMovieId(String movieId);

    @Query("DELETE FROM watch_progress")
    void deleteAll();

    @Query("SELECT * FROM watch_progress WHERE movieId = :movieId")
    WatchProgress getByMovieId(String movieId);

    @Query("SELECT * FROM watch_progress ORDER BY updateTime DESC")
    List<WatchProgress> getAll();

    @Query("SELECT * FROM watch_progress ORDER BY updateTime DESC LIMIT :limit")
    List<WatchProgress> getRecent(int limit);

    @Query("SELECT * FROM watch_progress WHERE movieId IN (:movieIds)")
    List<WatchProgress> getByMovieIds(List<String> movieIds);

    @Query("SELECT COUNT(*) FROM watch_progress")
    int getCount();
}