package com.example.nasmovie.service;

import android.content.Context;

import com.example.nasmovie.data.db.SmbConfigDao;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.data.smb.SmbClient;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 电影业务服务
 */
public class MovieService {

    private final Context context;
    private final MovieRepository repository;
    private final SmbConfigDao smbConfigDao;
    private final ExecutorService executor;
    private final ScanService scanService;

    public MovieService(Context context) {
        this.context = context;
        this.repository = new MovieRepository(context);
        this.smbConfigDao = com.example.nasmovie.NASMovieApp.getInstance().getDatabase().smbConfigDao();
        this.executor = Executors.newSingleThreadExecutor();
        this.scanService = new ScanService(context);
    }

    // ==================== 服务器管理 ====================

    /**
     * 获取所有服务器配置
     */
    public List<SmbConfig> getAllServers() {
        return smbConfigDao.getAll();
    }

    /**
     * 获取服务器配置
     */
    public SmbConfig getServerById(long id) {
        return smbConfigDao.getById(id);
    }

    /**
     * 保存服务器配置
     */
    public long saveServer(SmbConfig config) {
        if (config.getId() == 0) {
            return smbConfigDao.insert(config);
        } else {
            smbConfigDao.update(config);
            return config.getId();
        }
    }

    /**
     * 删除服务器配置
     */
    public void deleteServer(long id) {
        smbConfigDao.deleteById(id);
    }

    /**
     * 测试服务器连接
     */
    public void testConnection(SmbConfig config, ConnectionCallback callback) {
        executor.execute(() -> {
            SmbClient client = new SmbClient();
            boolean success = client.testConnection(config);
            if (success) {
                callback.onSuccess();
            } else {
                callback.onError("连接失败");
            }
        });
    }

    // ==================== 电影操作 ====================

    /**
     * 获取所有电影
     */
    public List<com.example.nasmovie.data.model.Movie> getAllMovies() {
        return repository.getAllMovies();
    }

    /**
     * 获取电影详情
     */
    public com.example.nasmovie.data.model.Movie getMovieById(String id) {
        return repository.getMovieById(id);
    }

    /**
     * 搜索电影
     */
    public List<com.example.nasmovie.data.model.Movie> searchMovies(String keyword) {
        return repository.searchMovies(keyword);
    }

    /**
     * 扫描媒体库
     */
    public void scanLibrary(long serverId, ScanService.ScanCallback callback) {
        scanService.scanServer(serverId, callback);
    }

    /**
     * 扫描所有媒体库
     */
    public void scanAllLibraries(ScanService.ScanCallback callback) {
        scanService.scanAllServers(callback);
    }

    /**
     * 停止扫描
     */
    public void stopScan() {
        scanService.stopScan();
    }

    // ==================== 收藏操作 ====================

    /**
     * 判断是否已收藏
     */
    public boolean isFavorite(String movieId) {
        return repository.isFavorite(movieId);
    }

    /**
     * 添加收藏
     */
    public void addFavorite(String movieId) {
        repository.addFavorite(movieId);
    }

    /**
     * 取消收藏
     */
    public void removeFavorite(String movieId) {
        repository.removeFavorite(movieId);
    }

    /**
     * 获取收藏列表
     */
    public List<com.example.nasmovie.data.model.Movie> getFavoriteMovies() {
        return repository.getFavoriteMovies();
    }

    // ==================== 观看进度 ====================

    /**
     * 获取观看进度
     */
    public com.example.nasmovie.data.model.WatchProgress getWatchProgress(String movieId) {
        return repository.getWatchProgress(movieId);
    }

    /**
     * 保存观看进度
     */
    public void saveWatchProgress(String movieId, long position, long duration) {
        repository.saveWatchProgress(movieId, position, duration);
    }

    /**
     * 获取最近观看的电影
     */
    public List<com.example.nasmovie.data.model.WatchProgress> getRecentWatchProgress(int limit) {
        return repository.getRecentWatchProgress(limit);
    }

    // ==================== 回调接口 ====================

    public interface ConnectionCallback {
        void onSuccess();
        void onError(String error);
    }
}