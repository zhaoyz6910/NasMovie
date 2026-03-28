package com.example.nasmovie.data.repository

import android.util.Log
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.data.db.AppDatabase
import com.example.nasmovie.data.db.FavoriteDao
import com.example.nasmovie.data.db.MovieDao
import com.example.nasmovie.data.db.WatchProgressDao
import com.example.nasmovie.data.model.Favorite
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.WatchProgress
import java.io.IOException

/**
 * 电影数据仓库 (Kotlin 协程重构版)
 * 统一管理本地数据库操作
 * 使用单例模式，避免重复创建实例
 */
object MovieRepository {

    private const val TAG = "MovieRepository"

    enum class SortType {
        TITLE_ASC,      // 标题 A-Z
        ADD_TIME_DESC,  // 最新添加
        YEAR_DESC,      // 年份最新
        RATING_DESC,    // 评分最高
        DURATION_DESC,  // 时长最长
        FILE_SIZE_DESC  // 文件最大
    }

    private val database: AppDatabase by lazy { NASMovieApp.getInstance().database }
    private val movieDao: MovieDao by lazy { database.movieDao() }
    private val watchProgressDao: WatchProgressDao by lazy { database.watchProgressDao() }
    private val favoriteDao: FavoriteDao by lazy { database.favoriteDao() }

    // ==================== 电影操作 ====================

    suspend fun getAllMovies(): List<Movie> = withErrorHandling("获取电影列表") {
        movieDao.getAll()
    }

    suspend fun getAllMovies(sortType: SortType): List<Movie> = withErrorHandling("获取排序电影列表") {
        when (sortType) {
            SortType.ADD_TIME_DESC -> movieDao.getAllByAddTime()
            SortType.YEAR_DESC -> movieDao.getAllByYearDesc()
            SortType.RATING_DESC -> movieDao.getAllByRatingDesc()
            SortType.DURATION_DESC -> movieDao.getAllByDurationDesc()
            SortType.FILE_SIZE_DESC -> movieDao.getAllByFileSizeDesc()
            SortType.TITLE_ASC -> movieDao.getAll()
        }
    }

    suspend fun getMovieById(id: String): Movie? = withErrorHandling("获取电影详情") {
        movieDao.getById(id)
    }

    suspend fun searchMovies(keyword: String): List<Movie> = withErrorHandling("搜索电影") {
        movieDao.search(keyword)
    }

    suspend fun saveMovie(movie: Movie) = withErrorHandling("保存电影") {
        movieDao.insert(movie)
    }

    suspend fun saveMovies(movies: List<Movie>) = withErrorHandling("批量保存电影") {
        movieDao.insertAll(movies)
    }

    suspend fun deleteMovie(id: String) = withErrorHandling("删除电影") {
        movieDao.deleteById(id)
    }

    suspend fun deleteMoviesByServer(serverId: Long) = withErrorHandling("删除服务器电影") {
        movieDao.deleteByServerId(serverId)
    }

    suspend fun getMovieCount(): Int = withErrorHandling("获取电影数量") {
        movieDao.getCount()
    }

    // ==================== 观看进度操作 ====================

    suspend fun getWatchProgress(movieId: String): WatchProgress? = withErrorHandling("获取观看进度") {
        watchProgressDao.getByMovieId(movieId)
    }

    suspend fun saveWatchProgress(movieId: String, position: Long, duration: Long) = withErrorHandling("保存观看进度") {
        val progress = WatchProgress(movieId, position, duration)
        watchProgressDao.insert(progress)
    }

    suspend fun deleteWatchProgress(movieId: String) = withErrorHandling("删除观看进度") {
        watchProgressDao.deleteByMovieId(movieId)
    }

    suspend fun getRecentWatchProgress(limit: Int): List<WatchProgress> = withErrorHandling("获取最近观看进度") {
        watchProgressDao.getRecent(limit)
    }

    /**
     * 获取最近观看的电影（使用 JOIN 查询，避免 N+1 问题）
     */
    suspend fun getRecentWatchedMovies(limit: Int): List<Movie> = withErrorHandling("获取最近观看电影") {
        watchProgressDao.getRecentMoviesWithProgress(limit)
    }

    // ==================== 收藏操作 ====================

    suspend fun isFavorite(movieId: String): Boolean = withErrorHandling("检查收藏状态") {
        favoriteDao.isFavorite(movieId)
    }

    suspend fun addFavorite(movieId: String) = withErrorHandling("添加收藏") {
        val favorite = Favorite(movieId)
        favoriteDao.insert(favorite)
    }

    suspend fun removeFavorite(movieId: String) = withErrorHandling("取消收藏") {
        favoriteDao.deleteByMovieId(movieId)
    }

    suspend fun getAllFavoriteIds(): List<String> = withErrorHandling("获取收藏ID列表") {
        favoriteDao.getAllMovieIds()
    }

    suspend fun getFavoriteMovies(): List<Movie> = withErrorHandling("获取收藏电影") {
        val ids = favoriteDao.getAllMovieIds()
        if (ids.isEmpty()) {
            emptyList()
        } else {
            movieDao.getByIds(ids)
        }
    }

    /**
     * 统一错误处理
     * 捕获数据库操作异常，记录日志并重新抛出包装后的异常
     * 注意：DAO方法已经是suspend函数，由Room自动管理线程，无需额外的withContext
     */
    private suspend fun <T> withErrorHandling(operation: String, block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "数据库操作失败: $operation", e)
            throw IOException("数据库操作失败: $operation (${e.message})", e)
        }
    }
}
