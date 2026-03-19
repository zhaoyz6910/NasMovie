package com.example.nasmovie.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 观看进度实体
 */
@Entity(tableName = "watch_progress", indices = {
    @Index(value = "updateTime")
})
public class WatchProgress {

    @NonNull
    @PrimaryKey
    private String movieId = "";      // 电影ID
    private long position;          // 播放位置（毫秒）
    private long duration;          // 总时长（毫秒）
    private long updateTime;        // 更新时间
    private int percentage;         // 播放百分比

    public WatchProgress() {
        this.updateTime = System.currentTimeMillis();
    }

    @Ignore
    public WatchProgress(String movieId, long position, long duration) {
        this.movieId = movieId;
        this.position = position;
        this.duration = duration;
        this.updateTime = System.currentTimeMillis();
        this.percentage = duration > 0 ? (int) (position * 100 / duration) : 0;
    }

    // Getters and Setters
    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = movieId;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
        this.percentage = duration > 0 ? (int) (position * 100 / duration) : 0;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
        this.percentage = duration > 0 ? (int) (position * 100 / duration) : 0;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public int getPercentage() {
        return percentage;
    }

    public void setPercentage(int percentage) {
        this.percentage = percentage;
    }

    /**
     * 判断是否已观看完成（超过90%）
     */
    public boolean isCompleted() {
        return percentage >= 90;
    }

    /**
     * 获取格式化的播放时间
     */
    public String getFormattedPosition() {
        return formatTime(position);
    }

    /**
     * 获取格式化的总时长
     */
    public String getFormattedDuration() {
        return formatTime(duration);
    }

    private String formatTime(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }
}