package com.example.nasmovie.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 电影实体
 */
@Entity(
    tableName = "movie",
    indices = [
        Index("serverId"),
        Index("year"),
        Index("rating"),
        Index("duration"),
        Index("fileSize"),
        Index("addTime"),
        Index("title")
    ]
)
data class Movie(
    @PrimaryKey
    var id: String = "",
    var title: String? = null,
    var originalTitle: String? = null,
    var plot: String? = null,
    var director: String? = null,
    var actors: String? = null,
    var genres: String? = null,
    var year: Int = 0,
    var rating: Float = 0f,
    var posterPath: String? = null,
    var thumbPath: String? = null,
    var localPosterPath: String? = null,
    var localThumbPath: String? = null,
    var videoPath: String? = null,
    var nfoPath: String? = null,
    var subtitlePaths: String? = null,
    var fileSize: Long = 0,
    var duration: Int = 0,
    var addTime: Long = System.currentTimeMillis(),
    var serverId: Long? = null,  // 改为 Long 类型，与 SmbConfig.id 保持一致
    var folderPath: String? = null
) {
    @Ignore
    var progress: Int = -1

    companion object {
        private val gson = Gson()
    }

    // Helper methods for JSON fields
    val actorList: List<String>
        get() = actors?.takeIf { it.isNotEmpty() }?.let {
            gson.fromJson(it, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } ?: emptyList()

    fun setActorList(actorList: List<String>) {
        this.actors = gson.toJson(actorList)
    }

    val genreList: List<String>
        get() = genres?.takeIf { it.isNotEmpty() }?.let {
            gson.fromJson(it, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } ?: emptyList()

    fun setGenreList(genreList: List<String>) {
        this.genres = gson.toJson(genreList)
    }

    val subtitlePathList: List<String>
        get() = subtitlePaths?.takeIf { it.isNotEmpty() }?.let {
            gson.fromJson(it, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } ?: emptyList()

    fun setSubtitlePathList(pathList: List<String>) {
        this.subtitlePaths = gson.toJson(pathList)
    }
}