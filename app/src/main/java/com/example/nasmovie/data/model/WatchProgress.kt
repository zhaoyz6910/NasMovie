package com.example.nasmovie.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

/**
 * 观看进度实体
 */
@Entity(
    tableName = "watch_progress",
    indices = [Index("updateTime")]
)
data class WatchProgress(
    @PrimaryKey
    var movieId: String = "",
    var position: Long = 0,
    var duration: Long = 0,
    var updateTime: Long = System.currentTimeMillis(),
    var percentage: Int = 0
) {
    @Ignore
    constructor(movieId: String, position: Long, duration: Long) : this(
        movieId,
        position,
        duration,
        System.currentTimeMillis(),
        if (duration > 0) (position * 100 / duration).toInt() else 0
    )

    /**
     * 判断是否已观看完成（超过90%）
     */
    val isCompleted: Boolean
        get() = percentage >= 90

    /**
     * 获取格式化的播放时间
     */
    val formattedPosition: String
        get() = formatTime(position)

    /**
     * 获取格式化的总时长
     */
    val formattedDuration: String
        get() = formatTime(duration)

    private fun formatTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    fun updatePercentage() {
        percentage = if (duration > 0) (position * 100 / duration).toInt() else 0
    }
}