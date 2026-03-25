package com.example.nasmovie.data.smb

import java.util.Locale

/**
 * SMB 扫描结果类
 */
data class ScanResult(
    var status: Status = Status.SUCCESS,
    var totalFiles: Int = 0,
    var videoFiles: Int = 0,
    var nfoFiles: Int = 0,
    var posterFiles: Int = 0,
    var scanTime: Long = 0,
    var errorMessage: String? = null,
    var files: List<SmbFileInfo>? = null,
    var movies: List<ScannedMovie>? = null
) {
    /**
     * 扫描状态
     */
    enum class Status {
        SUCCESS,    // 扫描成功
        PARTIAL,    // 部分成功（部分文件扫描失败）
        ERROR,      // 扫描出错
        CANCELLED   // 用户取消
    }

    /**
     * 是否成功
     */
    val isSuccess: Boolean
        get() = status == Status.SUCCESS || status == Status.PARTIAL

    /**
     * 获取扫描速度的格式化字符串
     */
    val scanSpeed: String
        get() {
            if (scanTime <= 0) return "0 files/s"
            val filesPerSecond = totalFiles.toDouble() / (scanTime / 1000.0)
            return String.format(Locale.US, "%.1f files/s", filesPerSecond)
        }

    /**
     * 获取扫描耗时的格式化字符串
     */
    val formattedScanTime: String
        get() = when {
            scanTime < 1000 -> "${scanTime}ms"
            scanTime < 60000 -> String.format(Locale.US, "%.1fs", scanTime / 1000.0)
            else -> {
                val minutes = scanTime / 60000
                val seconds = scanTime % 60000 / 1000
                "${minutes}m ${seconds}s"
            }
        }

    /**
     * 扫描到的电影信息
     */
    data class ScannedMovie(
        var name: String? = null,
        var videoPath: String? = null,
        var nfoPath: String? = null,
        var posterPath: String? = null,
        var videoSize: Long = 0,
        var videoFormat: String? = null
    ) {
        /**
         * 是否有 NFO 信息
         */
        fun hasNfo(): Boolean = !nfoPath.isNullOrEmpty()

        /**
         * 是否有海报
         */
        fun hasPoster(): Boolean = !posterPath.isNullOrEmpty()
    }
}