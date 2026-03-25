package com.example.nasmovie.data.model

import java.util.Locale

/**
 * 视频文件信息
 */
data class VideoFile(
    var path: String? = null,
    var name: String? = null,
    var extension: String? = null,
    var size: Long = 0,
    var lastModified: Long = 0
) {
    constructor(path: String, name: String) : this(
        path = path,
        name = name,
        extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    )

    /**
     * 判断是否为视频文件
     */
    fun isVideoFile(): Boolean = extension == "mp4" || extension == "mkv"

    /**
     * 获取不带扩展名的文件名
     */
    val nameWithoutExtension: String?
        get() = name?.let { it.substringBeforeLast('.') }

    /**
     * 获取格式化的文件大小
     */
    val formattedSize: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024))
            else -> String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024 * 1024))
        }
}