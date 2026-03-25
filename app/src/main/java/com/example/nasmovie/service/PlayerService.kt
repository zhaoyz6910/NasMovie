package com.example.nasmovie.service

import android.content.Context
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.WatchProgress
import com.example.nasmovie.data.repository.MovieRepository

/**
 * 播放业务服务 (Kotlin 重构版)
 */
class PlayerService(private val context: Context) {

    private val repository = MovieRepository(context)

    /**
     * 获取电影信息
     */
    suspend fun getMovie(movieId: String): Movie? {
        return repository.getMovieById(movieId)
    }

    /**
     * 获取观看进度
     */
    suspend fun getWatchPosition(movieId: String): Long {
        val progress = repository.getWatchProgress(movieId)
        return progress?.position ?: 0L
    }

    /**
     * 保存播放进度
     */
    suspend fun saveProgress(movieId: String, position: Long, duration: Long) {
        repository.saveWatchProgress(movieId, position, duration)
    }

    /**
     * 清除播放进度
     */
    suspend fun clearProgress(movieId: String) {
        repository.deleteWatchProgress(movieId)
    }

    /**
     * 计算播放百分比
     */
    fun calculateProgressPercent(position: Long, duration: Long): Int {
        if (duration <= 0) return 0
        return (position * 100 / duration).toInt()
    }

    /**
     * 检查是否需要继续播放
     */
    suspend fun shouldResume(movieId: String): Boolean {
        val progress = repository.getWatchProgress(movieId) ?: return false
        // 如果进度超过5%且未完成，则建议继续播放
        return progress.percentage > 5 && !progress.isCompleted
    }
}
