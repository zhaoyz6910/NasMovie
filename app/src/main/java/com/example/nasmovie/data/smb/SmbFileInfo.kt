package com.example.nasmovie.data.smb

import java.util.Locale

/**
 * SMB文件信息
 */
data class SmbFileInfo(
    var name: String? = null,
    var path: String? = null,
    var isDirectory: Boolean = false,
    var fileSize: Long = 0,
    var lastModified: Long = 0
) {
    /**
     * 获取文件扩展名
     */
    val extension: String
        get() = name?.let {
            if (it.contains(".")) it.substringAfterLast('.').lowercase(Locale.ROOT) else ""
        } ?: ""

    /**
     * 判断是否为视频文件
     */
    fun isVideoFile(): Boolean {
        val ext = extension
        return ext == "mp4" || ext == "mkv"
    }

    /**
     * 判断是否为字幕文件
     */
    fun isSubtitleFile(): Boolean {
        val ext = extension
        return ext == "srt" || ext == "ass" || ext == "ssa" || ext == "sub" || ext == "vtt"
    }

    /**
     * 判断是否为NFO文件
     */
    fun isNfoFile(): Boolean = extension == "nfo"

    /**
     * 判断是否为 poster 图片（用于首页）
     * 包括：poster.jpg, poster.png, folder.jpg, folder.png, cover.jpg, cover.png, fanart.jpg, fanart.png, backdrop.jpg, backdrop.png
     */
    fun isPosterImage(): Boolean {
        val lowerName = name?.lowercase(Locale.ROOT) ?: ""
        return lowerName in listOf(
            "poster.jpg", "poster.png",
            "folder.jpg", "folder.png",
            "cover.jpg", "cover.png",
            "fanart.jpg", "fanart.png",
            "backdrop.jpg", "backdrop.png"
        )
    }

    /**
     * 判断是否为 thumb 图片（用于详情页）
     * 包括：thumb.jpg, thumb.png
     */
    fun isThumbPoster(): Boolean {
        val lowerName = name?.lowercase(Locale.ROOT) ?: ""
        return lowerName == "thumb.jpg" || lowerName == "thumb.png"
    }

    /**
     * 判断是否为图片文件
     */
    fun isImageFile(): Boolean {
        val ext = extension
        return ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp"
    }
}