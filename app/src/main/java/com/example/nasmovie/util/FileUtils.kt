package com.example.nasmovie.util

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.DecimalFormat
import java.util.Locale

/**
 * 文件工具类
 */
object FileUtils {

    /**
     * 获取文件扩展名
     */
    fun getExtension(filename: String?): String {
        if (filename == null || !filename.contains(".")) {
            return ""
        }
        return filename.substring(filename.lastIndexOf(".") + 1).lowercase(Locale.ROOT)
    }

    /**
     * 获取不带扩展名的文件名
     */
    fun getNameWithoutExtension(filename: String?): String {
        if (filename == null) {
            return ""
        }
        val dotIndex = filename.lastIndexOf(".")
        return if (dotIndex > 0) filename.substring(0, dotIndex) else filename
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024))
            else -> {
                val df = DecimalFormat("#.##")
                df.format(bytes / (1024.0 * 1024 * 1024)) + " GB"
            }
        }
    }

    /**
     * 格式化时长（毫秒转为时分秒）
     */
    fun formatDuration(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).toInt()
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val secs = seconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    /**
     * 格式化时长（分钟转为时分）
     */
    fun formatDurationMinutes(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d小时%d分钟", hours, mins)
        } else {
            String.format(Locale.getDefault(), "%d分钟", mins)
        }
    }

    /**
     * 计算字符串的MD5哈希值
     */
    fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            input.hashCode().toString()
        }
    }

    /**
     * 生成电影ID（基于路径）
     */
    fun generateMovieId(path: String): String = md5(path)

    /**
     * 判断是否为视频文件
     */
    fun isVideoFile(filename: String?): Boolean {
        val ext = getExtension(filename)
        return ext in AppConstants.VIDEO_EXTENSIONS
    }

    /**
     * 判断是否为字幕文件
     */
    fun isSubtitleFile(filename: String?): Boolean {
        val ext = getExtension(filename)
        return ext in AppConstants.SUBTITLE_EXTENSIONS
    }

    /**
     * 判断是否为海报图片
     */
    fun isPosterImage(filename: String?): Boolean {
        if (filename == null) return false
        val lowerName = filename.lowercase(Locale.ROOT)
        return lowerName in listOf(
            "poster.jpg", "poster.png",
            "folder.jpg", "folder.png"
        ) || lowerName.startsWith("poster.") || lowerName.startsWith("folder.")
    }

    /**
     * 判断是否为NFO文件
     */
    fun isNfoFile(filename: String?): Boolean = getExtension(filename) == "nfo"

    /**
     * 组合SMB路径
     */
    fun combineSmbPath(vararg parts: String?): String {
        val sb = StringBuilder()
        for (part in parts) {
            if (part.isNullOrEmpty()) continue

            if (sb.isNotEmpty() && !sb.endsWith("/") && !sb.endsWith("\\")) {
                sb.append("/")
            }

            // 移除开头的斜杠
            val trimmedPart = part.removePrefix("/").removePrefix("\\")

            sb.append(trimmedPart)
        }
        return sb.toString()
    }
}