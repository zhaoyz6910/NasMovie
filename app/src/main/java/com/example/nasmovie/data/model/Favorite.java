package com.example.nasmovie.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 收藏实体
 */
@Entity(tableName = "favorite")
public class Favorite {

    @NonNull
    @PrimaryKey
    private String movieId = "";      // 电影ID
    private long addTime;           // 收藏时间

    public Favorite() {
        this.addTime = System.currentTimeMillis();
    }

    @Ignore
    public Favorite(String movieId) {
        this.movieId = movieId;
        this.addTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = movieId;
    }

    public long getAddTime() {
        return addTime;
    }

    public void setAddTime(long addTime) {
        this.addTime = addTime;
    }
}