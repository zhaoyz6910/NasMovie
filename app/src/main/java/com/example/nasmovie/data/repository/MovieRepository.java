package com.example.nasmovie.data.repository;

import android.content.Context;
import android.os.Looper;
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

    /**
     * 电影排序类型
     */
    public enum SortType {
        TITLE_ASC,      // 标题 A-Z
        ADD_TIME_DESC,  // 最新添加
        YEAR_DESC,      // 年份最新
        RATING_DESC,    // 评分最高
        DURATION_DESC,  // 时长最长
        FILE_SIZE_DESC  // 文件最大
    }

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

    /**
     * 检查是否在主线程，如果是则抛出异常
     */
    private void assertNotMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("数据库操作不能在主线程执行");
        }
    }

    // ==================== 电影操作 ====================

    /**
     * 获取所有电影
     */
    public List<Movie> getAllMovies() {
        assertNotMainThread();
        return movieDao.getAll();
    }

    /**
     * 按排序类型获取电影
     */
    public List<Movie> getAllMovies(SortType sortType) {
        assertNotMainThread();
        switch (sortType) {
            case ADD_TIME_DESC:
                return movieDao.getAllByAddTime();
            case YEAR_DESC:
                return movieDao.getAllByYearDesc();
            case RATING_DESC:
                return movieDao.getAllByRatingDesc();
            case DURATION_DESC:
                return movieDao.getAllByDurationDesc();
            case FILE_SIZE_DESC:
                return movieDao.getAllByFileSizeDesc();
            case TITLE_ASC:
            default:
                return movieDao.getAll();
        }
    }

    /**
     * 根据ID获取电影
     */
    public Movie getMovieById(String id) {
        assertNotMainThread();
        return movieDao.getById(id);
    }

    /**
     * 搜索电影
     */
    public List<Movie> searchMovies(String keyword) {
        assertNotMainThread();
        return movieDao.search(keyword);
    }

    /**
     * 保存电影
     */
    public void saveMovie(Movie movie) {
        assertNotMainThread();
        movieDao.insert(movie);
    }

    /**
     * 保存电影列表
     */
    public void saveMovies(List<Movie> movies) {
        assertNotMainThread();
        movieDao.insertAll(movies);
    }

    /**
     * 删除电影
     */
    public void deleteMovie(String id) {
        assertNotMainThread();
        movieDao.deleteById(id);
    }

    /**
     * 删除指定服务器的所有电影
     */
    public void deleteMoviesByServer(String serverId) {
        assertNotMainThread();
        movieDao.deleteByServerId(serverId);
    }

    /**
     * 获取电影数量
     */
    public int getMovieCount() {
        assertNotMainThread();
        return movieDao.getCount();
    }

    // ==================== 观看进度操作 ====================

    /**
     * 获取观看进度
     */
    public WatchProgress getWatchProgress(String movieId) {
        assertNotMainThread();
        return watchProgressDao.getByMovieId(movieId);
    }

    /**
     * 保存观看进度
     */
    public void saveWatchProgress(String movieId, long position, long duration) {
        assertNotMainThread();
        WatchProgress progress = new WatchProgress(movieId, position, duration);
        watchProgressDao.insert(progress);
    }

    /**
     * 删除观看进度
     */
    public void deleteWatchProgress(String movieId) {
        assertNotMainThread();
        watchProgressDao.deleteByMovieId(movieId);
    }

    /**
     * 获取最近观看记录
     */
    public List<WatchProgress> getRecentWatchProgress(int limit) {
        assertNotMainThread();
        return watchProgressDao.getRecent(limit);
    }

    // ==================== 收藏操作 ====================

    /**
     * 判断是否已收藏
     */
    public boolean isFavorite(String movieId) {
        assertNotMainThread();
        return favoriteDao.isFavorite(movieId);
    }

    /**
     * 添加收藏
     */
    public void addFavorite(String movieId) {
        assertNotMainThread();
        Favorite favorite = new Favorite(movieId);
        favoriteDao.insert(favorite);
    }

    /**
     * 取消收藏
     */
    public void removeFavorite(String movieId) {
        assertNotMainThread();
        favoriteDao.deleteByMovieId(movieId);
    }

    /**
     * 获取所有收藏的电影ID
     */
    public List<String> getAllFavoriteIds() {
        assertNotMainThread();
        return favoriteDao.getAllMovieIds();
    }

    /**
     * 获取收藏列表
     */
    public List<Movie> getFavoriteMovies() {
        assertNotMainThread();
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