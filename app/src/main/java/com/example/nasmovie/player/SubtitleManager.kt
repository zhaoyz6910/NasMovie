package com.example.nasmovie.player

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.TextView
import com.example.nasmovie.data.parser.SubtitleParser
import com.example.nasmovie.data.smb.SmbClient
import com.example.nasmovie.data.smb.SmbFileReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

/**
 * 字幕管理类
 * 负责字幕加载和显示
 */
class SubtitleManager(private val context: Context) {

    private var smbClient: SmbClient? = null
    private var fileReader: SmbFileReader? = null
    private var subtitleView: TextView? = null

    private var entries: MutableList<SubtitleParser.SubtitleEntry> = mutableListOf()
    private var currentSubtitlePath: String? = null
    private var currentEntryIndex = -1

    companion object {
        private const val TAG = "SubtitleManager"
    }

    /**
     * 设置SMB客户端
     */
    fun setSmbClient(client: SmbClient?) {
        this.smbClient = client
        if (client != null && client.diskShare != null) {
            this.fileReader = SmbFileReader(client.diskShare)
        }
    }

    /**
     * 设置字幕显示视图
     */
    fun setSubtitleView(view: TextView?) {
        this.subtitleView = view
    }

    /**
     * 加载字幕文件（支持SMB和本地文件）
     */
    fun loadSubtitle(path: String): Boolean {
        return try {
            val data: ByteArray?

            // 判断是本地文件还是SMB文件
            if (path.startsWith("/") || path.contains(":\\") || path.contains(":/")) {
                // 本地文件
                data = readLocalFile(path)
            } else {
                // SMB文件
                val reader = fileReader
                if (reader == null) {
                    Log.e(TAG, "SMB file reader not initialized")
                    return false
                }
                data = reader.readAllBytes(path)
            }

            if (data == null) {
                Log.e(TAG, "Failed to read subtitle file: $path")
                return false
            }

            val extension = getExtension(path)
            val subtitleData = SubtitleParser.parse(data, extension)

            if (subtitleData == null || subtitleData.entries.isEmpty()) {
                Log.e(TAG, "Failed to parse subtitle file: $path")
                return false
            }

            entries = subtitleData.entries.toMutableList()
            currentSubtitlePath = path
            currentEntryIndex = -1

            Log.i(TAG, "Loaded ${entries.size} subtitle entries")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error loading subtitle: ${e.message}")
            false
        }
    }

    /**
     * 读取本地文件
     */
    @Throws(IOException::class)
    private fun readLocalFile(path: String): ByteArray? {
        val file = File(path)
        if (!file.exists()) {
            Log.e(TAG, "Local file not found: $path")
            return null
        }

        return FileInputStream(file).use { fis ->
            val fileSize = file.length().toInt()
            val buffer = ByteArray(fileSize)
            val bytesRead = fis.read(buffer)
            if (bytesRead != fileSize) {
                Log.w(TAG, "Partial read: $bytesRead of $fileSize")
            }
            buffer
        }
    }

    /**
     * 更新字幕显示
     * @param positionMs 当前播放位置（毫秒）
     */
    fun update(positionMs: Long) {
        if (entries.isEmpty() || subtitleView == null) {
            return
        }

        // 查找当前应该显示的字幕
        val index = findSubtitleIndex(positionMs)

        if (index != currentEntryIndex) {
            currentEntryIndex = index

            val view = subtitleView
            if (index >= 0) {
                val entry = entries[index]
                view?.text = entry.text
                view?.visibility = View.VISIBLE
            } else {
                view?.visibility = View.GONE
            }
        }
    }

    /**
     * 查找指定时间对应的字幕索引（二分查找优化）
     */
    private fun findSubtitleIndex(positionMs: Long): Int {
        var left = 0
        var right = entries.size - 1

        while (left <= right) {
            val mid = left + (right - left) / 2
            val entry = entries[mid]

            if (positionMs >= entry.startTimeMs && positionMs <= entry.endTimeMs) {
                return mid
            } else if (positionMs < entry.startTimeMs) {
                right = mid - 1
            } else {
                left = mid + 1
            }
        }
        return -1
    }

    /**
     * 清除字幕
     */
    fun clear() {
        entries.clear()
        currentEntryIndex = -1
        subtitleView?.visibility = View.GONE
    }

    /**
     * 是否已加载字幕
     */
    fun hasSubtitle(): Boolean = entries.isNotEmpty()

    /**
     * 获取当前字幕路径
     */
    fun getCurrentSubtitlePath(): String? = currentSubtitlePath

    /**
     * 获取字幕条目数量
     */
    fun getSubtitleCount(): Int = entries.size

    /**
     * 获取文件扩展名
     */
    private fun getExtension(path: String?): String {
        if (path == null || !path.contains(".")) {
            return ""
        }
        return path.substringAfterLast('.').lowercase(Locale.ROOT)
    }
}