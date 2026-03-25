package com.example.nasmovie.data.model

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 收藏实体
 */
@Entity(
    tableName = "favorite",
    indices = [Index("addTime")]
)
data class Favorite(
    @PrimaryKey
    var movieId: String = "",
    var addTime: Long = System.currentTimeMillis()
) {
    @Ignore
    constructor(movieId: String) : this(movieId, System.currentTimeMillis())
}