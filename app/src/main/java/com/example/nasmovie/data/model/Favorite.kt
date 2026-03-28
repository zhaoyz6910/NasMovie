package com.example.nasmovie.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 收藏实体
 */
@Entity(
    tableName = "favorite",
    indices = [Index("addTime"), Index("movieId")],
    foreignKeys = [
        ForeignKey(
            entity = Movie::class,
            parentColumns = ["id"],
            childColumns = ["movieId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class Favorite(
    @PrimaryKey
    var movieId: String = "",
    var addTime: Long = System.currentTimeMillis()
) {
    @Ignore
    constructor(movieId: String) : this(movieId, System.currentTimeMillis())
}