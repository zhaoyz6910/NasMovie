package com.example.nasmovie.service

import android.content.Context
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.data.db.SmbConfigDao
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.data.model.WatchProgress
import com.example.nasmovie.data.repository.MovieRepository
import com.example.nasmovie.data.smb.SmbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 电影业务服务 (Kotlin 重构版)
 */
class MovieService(private val context: Context) {

    private val repository = MovieRepository()
    private val smbConfigDao: SmbConfigDao = NASMovieApp.getInstance().database.smbConfigDao()
    private val scanService = ScanService(context)

    // ==================== 服务器管理 ====================

    suspend fun getAllServers(): List<SmbConfig> = withContext(Dispatchers.IO) {
        smbConfigDao.getAll()
    }

    suspend fun getServerById(id: Long): SmbConfig? = withContext(Dispatchers.IO) {
        smbConfigDao.getById(id)
    }

    suspend fun saveServer(config: SmbConfig): Long = withContext(Dispatchers.IO) {
        if (config.id == 0L) {
            smbConfigDao.insert(config)
        } else {
            smbConfigDao.update(config)
            config.id
        }
    }

    suspend fun deleteServer(id: Long) = withContext(Dispatchers.IO) {
        smbConfigDao.deleteById(id)
    }

    suspend fun testConnection(config: SmbConfig): Boolean = withContext(Dispatchers.IO) {
        val client = SmbClient()
        client.testConnection(config)
    }

    // ==================== 电影操作 ====================

    suspend fun getAllMovies(): List<Movie> {
        return repository.getAllMovies()
    }

    suspend fun getMovieById(id: String): Movie? {
        return repository.getMovieById(id)
    }

    suspend fun searchMovies(keyword: String): List<Movie> {
        return repository.searchMovies(keyword)
    }

    fun scanLibrary(serverId: Long, callback: ScanService.ScanCallback) {
        scanService.scanServer(serverId, callback)
    }

    fun scanAllLibraries(callback: ScanService.ScanCallback) {
        scanService.scanAllServers(callback)
    }

    fun stopScan() {
        scanService.stopScan()
    }

    // ==================== 收藏操作 ====================

    suspend fun isFavorite(movieId: String): Boolean {
        return repository.isFavorite(movieId)
    }

    suspend fun addFavorite(movieId: String) {
        repository.addFavorite(movieId)
    }

    suspend fun removeFavorite(movieId: String) {
        repository.removeFavorite(movieId)
    }

    suspend fun getFavoriteMovies(): List<Movie> {
        return repository.getFavoriteMovies()
    }

    // ==================== 观看进度 ====================

    suspend fun getWatchProgress(movieId: String): WatchProgress? {
        return repository.getWatchProgress(movieId)
    }

    suspend fun saveWatchProgress(movieId: String, position: Long, duration: Long) {
        repository.saveWatchProgress(movieId, position, duration)
    }

    suspend fun getRecentWatchProgress(limit: Int): List<WatchProgress> {
        return repository.getRecentWatchProgress(limit)
    }
}
