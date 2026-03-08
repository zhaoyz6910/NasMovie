package com.example.nasmovie.service;

import android.content.Context;

import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.data.repository.MovieRepository;

/**
 * 播放业务服务
 */
public class PlayerService {

    private final Context context;
    private final MovieRepository repository;

    public PlayerService(Context context) {
        this.context = context;
        this.repository = new MovieRepository(context);
    }

    /**
     * 获取电影信息
     */
    public Movie getMovie(String movieId) {
        return repository.getMovieById(movieId);
    }

    /**
     * 获取观看进度
     */
    public long getWatchPosition(String movieId) {
        WatchProgress progress = repository.getWatchProgress(movieId);
        return progress != null ? progress.getPosition() : 0;
    }

    /**
     * 保存播放进度
     */
    public void saveProgress(String movieId, long position, long duration) {
        repository.saveWatchProgress(movieId, position, duration);
    }

    /**
     * 清除播放进度
     */
    public void clearProgress(String movieId) {
        repository.deleteWatchProgress(movieId);
    }

    /**
     * 计算播放百分比
     */
    public int calculateProgressPercent(long position, long duration) {
        if (duration <= 0) return 0;
        return (int) (position * 100 / duration);
    }

    /**
     * 检查是否需要继续播放
     */
    public boolean shouldResume(String movieId) {
        WatchProgress progress = repository.getWatchProgress(movieId);
        if (progress == null) return false;
        // 如果进度超过5%且未完成，则建议继续播放
        return progress.getPercentage() > 5 && !progress.isCompleted();
    }
}