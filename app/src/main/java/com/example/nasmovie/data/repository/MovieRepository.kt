package com.example.nasmovie.data.repository

import android.content.Context
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.data.db.AppDatabase
import com.example.nasmovie.data.db.FavoriteDao
import com.example.nasmovie.data.db.MovieDao
import com.example.nasmovie.data.db.WatchProgressDao
import com.example.nasmovie.data.model.Favorite
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 电影数据仓库 (Kotlin 协程重构版)
 * 统一管理本地数据库操作
 */
class MovieRepository(context: Context) {

    enum class SortType {
        TITLE_ASC,      // 标题 A-Z
        ADD_TIME_DESC,  // 最新添加
        YEAR_DESC,      // 年份最新
        RATING_DESC,    // 评分最高
        DURATION_DESC,  // 时长最长
        FILE_SIZE_DESC  // 文件最大
    }

    private val database: AppDatabase = NASMovieApp.getInstance().database
    private val movieDao: MovieDao = database.movieDao()
    private val watchProgressDao: WatchProgressDao = database.watchProgressDao()
    private val favoriteDao: FavoriteDao = database.favoriteDao()

    // ==================== 电影操作 ====================

    suspend fun getAllMovies(): List<Movie> = withContext(Dispatchers.IO) {
        movieDao.getAll()
    }

    suspend fun getAllMovies(sortType: SortType): List<Movie> = withContext(Dispatchers.IO) {
        when (sortType) {
            SortType.ADD_TIME_DESC -> movieDao.getAllByAddTime()
            SortType.YEAR_DESC -> movieDao.getAllByYearDesc()
            SortType.RATING_DESC -> movieDao.getAllByRatingDesc()
            SortType.DURATION_DESC -> movieDao.getAllByDurationDesc()
            SortType.FILE_SIZE_DESC -> movieDao.getAllByFileSizeDesc()
            SortType.TITLE_ASC -> movieDao.getAll()
        }
    }

    suspend fun getMovieById(id: String): Movie? = withContext(Dispatchers.IO) {
        movieDao.getById(id)
    }

    suspend fun searchMovies(keyword: String): List<Movie> = withContext(Dispatchers.IO) {
        movieDao.search(keyword)
    }

    suspend fun saveMovie(movie: Movie) = withContext(Dispatchers.IO) {
        movieDao.insert(movie)
    }

    suspend fun saveMovies(movies: List<Movie>) = withContext(Dispatchers.IO) {
        movieDao.insertAll(movies)
    }

    suspend fun deleteMovie(id: String) = withContext(Dispatchers.IO) {
        movieDao.deleteById(id)
    }

    suspend fun deleteMoviesByServer(serverId: String) = withContext(Dispatchers.IO) {
        movieDao.deleteByServerId(serverId)
    }

    suspend fun getMovieCount(): Int = withContext(Dispatchers.IO) {
        movieDao.getCount()
    }

    // ==================== 观看进度操作 ====================

    suspend fun getWatchProgress(movieId: String): WatchProgress? = withContext(Dispatchers.IO) {
        watchProgressDao.getByMovieId(movieId)
    }

    suspend fun saveWatchProgress(movieId: String, position: Long, duration: Long) = withContext(Dispatchers.IO) {
        val progress = WatchProgress(movieId, position, duration)
        watchProgressDao.insert(progress)
    }

    suspend fun deleteWatchProgress(movieId: String) = withContext(Dispatchers.IO) {
        watchProgressDao.deleteByMovieId(movieId)
    }

    suspend fun getRecentWatchProgress(limit: Int): List<WatchProgress> = withContext(Dispatchers.IO) {
        watchProgressDao.getRecent(limit)
    }

    // ==================== 收藏操作 ====================

    suspend fun isFavorite(movieId: String): Boolean = withContext(Dispatchers.IO) {
        favoriteDao.isFavorite(movieId)
    }

    suspend fun addFavorite(movieId: String) = withContext(Dispatchers.IO) {
        val favorite = Favorite(movieId)
        favoriteDao.insert(favorite)
    }

    suspend fun removeFavorite(movieId: String) = withContext(Dispatchers.IO) {
        favoriteDao.deleteByMovieId(movieId)
    }

    suspend fun getAllFavoriteIds(): List<String> = withContext(Dispatchers.IO) {
        favoriteDao.getAllMovieIds()
    }

    suspend fun getFavoriteMovies(): List<Movie> = withContext(Dispatchers.IO) {
        val ids = favoriteDao.getAllMovieIds()
        if (ids.isEmpty()) {
            emptyList()
        } else {
            movieDao.getByIds(ids)
        }
    }

    fun close() {
        // No longer using ExecutorService, so nothing to shut down
    }
}
