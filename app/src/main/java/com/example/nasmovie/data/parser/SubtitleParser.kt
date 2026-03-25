package com.example.nasmovie.data.parser

import android.text.TextUtils
import android.util.Log
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern

/**
 * 字幕文件解析器
 * 支持SRT和ASS格式
 */
object SubtitleParser {

    private const val TAG = "SubtitleParser"

    // SRT时间格式正则表达式
    private val SRT_TIME_PATTERN = Pattern.compile(
        "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})"
    )

    /**
     * 字幕条目
     */
    data class SubtitleEntry(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val text: String
    )

    /**
     * 字幕数据
     */
    class SubtitleData {
        var entries: MutableList<SubtitleEntry> = mutableListOf()
        var format: String? = null
    }

    /**
     * 解析字幕文件
     */
    fun parse(data: ByteArray?, extension: String?): SubtitleData? {
        if (data == null || data.isEmpty()) {
            return null
        }

        val ext = extension?.lowercase(Locale.ROOT) ?: ""

        // 检测编码
        val charset = detectCharset(data)
        val content = String(data, charset)

        return try {
            when (ext) {
                "srt" -> parseSrt(content)
                "ass", "ssa" -> parseAss(content)
                "vtt" -> parseVtt(content)
                else -> parseSrt(content) // 默认尝试SRT格式
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing subtitle: ${e.message}")
            null
        }
    }

    /**
     * 解析SRT格式字幕
     */
    fun parseSrt(content: String): SubtitleData {
        val subtitleData = SubtitleData()
        subtitleData.format = "srt"

        val lines = content.split("\\r?\\n".toRegex())
        var i = 0

        while (i < lines.size) {
            var line = lines[i].trim()

            // 跳过序号行
            if (isNumeric(line)) {
                i++
                if (i >= lines.size) break
                line = lines[i].trim()
            }

            // 解析时间轴
            val matcher = SRT_TIME_PATTERN.matcher(line)
            if (matcher.matches()) {
                val startTime = parseTime(
                    matcher.group(1), matcher.group(2),
                    matcher.group(3), matcher.group(4)
                )
                val endTime = parseTime(
                    matcher.group(5), matcher.group(6),
                    matcher.group(7), matcher.group(8)
                )

                // 读取字幕文本
                val textBuilder = StringBuilder()
                i++
                while (i < lines.size) {
                    val textLine = lines[i].trim()
                    if (textLine.isEmpty() ||
                        SRT_TIME_PATTERN.matcher(textLine).matches() ||
                        isNumeric(textLine)) {
                        break
                    }
                    if (textBuilder.isNotEmpty()) {
                        textBuilder.append("\n")
                    }
                    textBuilder.append(textLine)
                    i++
                }

                if (textBuilder.isNotEmpty()) {
                    subtitleData.entries.add(SubtitleEntry(startTime, endTime, textBuilder.toString()))
                }
                continue
            }

            i++
        }

        return subtitleData
    }

    /**
     * 解析ASS/SSA格式字幕
     */
    fun parseAss(content: String): SubtitleData {
        val subtitleData = SubtitleData()
        subtitleData.format = "ass"

        val lines = content.split("\\r?\\n".toRegex())
        var inEvents = false

        for (line in lines) {
            val trimmedLine = line.trim()

            if (trimmedLine.startsWith("[Events]")) {
                inEvents = true
                continue
            }

            if (trimmedLine.startsWith("[") && trimmedLine.endsWith("]")) {
                inEvents = false
                continue
            }

            if (inEvents && trimmedLine.startsWith("Dialogue:")) {
                // 解析对话行
                // 格式: Dialogue: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
                val parts = trimmedLine.substring(9).split(",", limit = 10)
                if (parts.size >= 10) {
                    try {
                        val startTime = parseAssTime(parts[1].trim())
                        val endTime = parseAssTime(parts[2].trim())
                        val text = cleanAssText(parts[9])

                        if (!TextUtils.isEmpty(text)) {
                            subtitleData.entries.add(SubtitleEntry(startTime, endTime, text))
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing ASS line: $trimmedLine")
                    }
                }
            }
        }

        return subtitleData
    }

    /**
     * 解析WebVTT格式字幕
     */
    fun parseVtt(content: String): SubtitleData {
        val subtitleData = SubtitleData()
        subtitleData.format = "vtt"

        val lines = content.split("\\r?\\n".toRegex())
        var i = 0

        // 跳过WEBVTT头部
        while (i < lines.size && !lines[i].contains("-->")) {
            i++
        }

        while (i < lines.size) {
            val line = lines[i].trim()

            // 解析时间轴 (格式: 00:00:00.000 --> 00:00:00.000)
            if (line.contains("-->")) {
                val times = line.split("-->")
                if (times.size == 2) {
                    val startTime = parseVttTime(times[0].trim())
                    val endTime = parseVttTime(times[1].trim())

                    // 读取字幕文本
                    val textBuilder = StringBuilder()
                    i++
                    while (i < lines.size) {
                        val textLine = lines[i].trim()
                        if (textLine.isEmpty() || textLine.contains("-->")) {
                            break
                        }
                        if (textBuilder.isNotEmpty()) {
                            textBuilder.append("\n")
                        }
                        // 移除VTT标签
                        val cleanLine = textLine.replace("<[^>]+>".toRegex(), "")
                        textBuilder.append(cleanLine)
                        i++
                    }

                    if (textBuilder.isNotEmpty()) {
                        subtitleData.entries.add(SubtitleEntry(startTime, endTime, textBuilder.toString()))
                    }
                    continue
                }
            }

            i++
        }

        return subtitleData
    }

    /**
     * 解析SRT时间 (HH:MM:SS,mmm)
     */
    private fun parseTime(hours: String?, minutes: String?, seconds: String?, millis: String?): Long {
        val h = hours?.toLongOrNull() ?: 0L
        val m = minutes?.toLongOrNull() ?: 0L
        val s = seconds?.toLongOrNull() ?: 0L
        val ms = millis?.toLongOrNull() ?: 0L
        return h * 3600000 + m * 60000 + s * 1000 + ms
    }

    /**
     * 解析ASS时间 (H:MM:SS.mm)
     */
    private fun parseAssTime(time: String): Long {
        val parts = time.split("[:.]".toRegex())
        return if (parts.size >= 3) {
            val hours = parts[0].toLongOrNull() ?: 0
            val minutes = parts[1].toLongOrNull() ?: 0
            val seconds = parts[2].toLongOrNull() ?: 0
            val centis = if (parts.size > 3) parts[3].toLongOrNull() ?: 0 else 0

            hours * 3600000 + minutes * 60000 + seconds * 1000 + centis * 10
        } else {
            0
        }
    }

    /**
     * 解析VTT时间 (HH:MM:SS.mmm 或 MM:SS.mmm)
     */
    private fun parseVttTime(time: String): Long {
        val trimmedTime = time.trim()
        val mainParts = trimmedTime.split(".")
        var millis = 0L
        if (mainParts.size > 1) {
            millis = mainParts[1].toLongOrNull() ?: 0
        }

        val timeParts = mainParts[0].split(":")
        return when (timeParts.size) {
            3 -> {
                val h = timeParts[0].toLongOrNull() ?: 0L
                val m = timeParts[1].toLongOrNull() ?: 0L
                val s = timeParts[2].toLongOrNull() ?: 0L
                h * 3600000 + m * 60000 + s * 1000 + millis
            }
            2 -> {
                val m = timeParts[0].toLongOrNull() ?: 0L
                val s = timeParts[1].toLongOrNull() ?: 0L
                m * 60000 + s * 1000 + millis
            }
            else -> 0
        }
    }

    /**
     * 清理ASS字幕文本
     */
    private fun cleanAssText(text: String): String {
        return text
            .replace("\\{[^}]*\\}".toRegex(), "")
            .replace("\\\\N", "\n")
            .replace("\\\\n", "\n")
            .replace("\\\\h", " ")
            .trim()
    }

    /**
     * 检测字符编码
     */
    private fun detectCharset(data: ByteArray): Charset {
        // 检查BOM
        if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) {
            return StandardCharsets.UTF_8
        }
        if (data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte()) {
            return Charset.forName("UTF-16LE")
        }
        if (data.size >= 2 && data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte()) {
            return Charset.forName("UTF-16BE")
        }

        // 默认使用UTF-8
        return StandardCharsets.UTF_8
    }

    /**
     * 检查字符串是否为数字
     */
    private fun isNumeric(str: String?): Boolean {
        if (TextUtils.isEmpty(str)) {
            return false
        }
        return str!!.all { it.isDigit() }
    }
}