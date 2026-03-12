package com.example.nasmovie.data.repository;

import android.content.Context;
import android.util.Log;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.data.db.AppDatabase;
import com.example.nasmovie.data.db.FavoriteDao;
import com.example.nasmovie.data.db.MovieDao;
import com.example.nasmovie.data.db.WatchProgressDao;
import com.example.nasmovie.data.model.Favorite;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.WatchProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 电影数据仓库
 * 统一管理本地数据库操作
 * (扫描逻辑已迁移至 ScanService)
 */
public class MovieRepository {

    private static final String TAG = "MovieRepository";

    private final AppDatabase database;
    private final MovieDao movieDao;
    private final WatchProgressDao watchProgressDao;
    private final FavoriteDao favoriteDao;
    private final ExecutorService executor;

    public MovieRepository(Context context) {
        database = NASMovieApp.getInstance().getDatabase();
        movieDao = database.movieDao();
        watchProgressDao = database.watchProgressDao();
        favoriteDao = database.favoriteDao();
        executor = Executors.newFixedThreadPool(4);
    }

    // ==================== 电影操作 ====================

    /**
     * 获取所有电影
     */
    public List<Movie> getAllMovies() {
        return movieDao.getAll();
    }

    /**
     * 根据ID获取电影
     */
    public Movie getMovieById(String id) {
        return movieDao.getById(id);
    }

    /**
     * 搜索电影
     */
    public List<Movie> searchMovies(String keyword) {
        return movieDao.search(keyword);
    }

    /**
     * 保存电影
     */
    public void saveMovie(Movie movie) {
        movieDao.insert(movie);
    }

    /**
     * 保存电影列表
     */
    public void saveMovies(List<Movie> movies) {
        movieDao.insertAll(movies);
    }

    /**
     * 删除电影
     */
    public void deleteMovie(String id) {
        movieDao.deleteById(id);
    }

    /**
     * 删除指定服务器的所有电影
     */
    public void deleteMoviesByServer(String serverId) {
        movieDao.deleteByServerId(serverId);
    }

    /**
     * 获取电影数量
     */
    public int getMovieCount() {
        return movieDao.getCount();
    }

    // ==================== 观看进度操作 ====================

    /**
     * 获取观看进度
     */
    public WatchProgress getWatchProgress(String movieId) {
        return watchProgressDao.getByMovieId(movieId);
    }

    /**
     * 保存观看进度
     */
    public void saveWatchProgress(String movieId, long position, long duration) {
        WatchProgress progress = new WatchProgress(movieId, position, duration);
        watchProgressDao.insert(progress);
    }

    /**
     * 删除观看进度
     */
    public void deleteWatchProgress(String movieId) {
        watchProgressDao.deleteByMovieId(movieId);
    }

    /**
     * 获取最近观看记录
     */
    public List<WatchProgress> getRecentWatchProgress(int limit) {
        return watchProgressDao.getRecent(limit);
    }

    // ==================== 收藏操作 ====================

    /**
     * 判断是否已收藏
     */
    public boolean isFavorite(String movieId) {
        return favoriteDao.isFavorite(movieId);
    }

    /**
     * 添加收藏
     */
    public void addFavorite(String movieId) {
        Favorite favorite = new Favorite(movieId);
        favoriteDao.insert(favorite);
    }

    /**
     * 取消收藏
     */
    public void removeFavorite(String movieId) {
        favoriteDao.deleteByMovieId(movieId);
    }

    /**
     * 获取所有收藏的电影ID
     */
    public List<String> getAllFavoriteIds() {
        return favoriteDao.getAllMovieIds();
    }

    /**
     * 获取收藏列表
     */
    public List<Movie> getFavoriteMovies() {
        List<String> ids = favoriteDao.getAllMovieIds();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return movieDao.getByIds(ids);
    }

    /**
     * 关闭资源
     */
    public void close() {
        executor.shutdown();
    }
}