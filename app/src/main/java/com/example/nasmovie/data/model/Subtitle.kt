package com.example.nasmovie.data.model

import java.util.Locale

/**
 * 字幕文件信息
 */
data class Subtitle(
    var path: String? = null,
    var name: String? = null,
    var extension: String? = null,
    var language: String? = null,
    var isDefault: Boolean = false
) {
    companion object {
        val SUPPORTED_FORMATS = arrayOf("srt", "ass", "ssa", "sub", "vtt")
    }

    constructor(path: String, name: String) : this(
        path = path,
        name = name,
        extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    )

    /**
     * 判断是否为支持的字幕格式
     */
    fun isSupported(): Boolean = extension?.let { ext ->
        SUPPORTED_FORMATS.contains(ext)
    } ?: false

    /**
     * 获取不带扩展名的文件名
     */
    val nameWithoutExtension: String?
        get() = name?.let { it.substringBeforeLast('.') }

    /**
     * 获取显示名称
     */
    val displayName: String
        get() {
            val lang = language
            return if (!lang.isNullOrEmpty()) lang else name ?: ""
        }
}