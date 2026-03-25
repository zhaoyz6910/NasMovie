package com.example.nasmovie.util

import java.util.Locale

/**
 * 字符串工具类
 */
object StringUtils {

    /**
     * 判断字符串是否为空
     */
    fun isEmpty(str: String?): Boolean = str == null || str.trim().isEmpty()

    /**
     * 判断字符串是否不为空
     */
    fun isNotEmpty(str: String?): Boolean = !isEmpty(str)

    /**
     * 安全获取字符串（为空时返回默认值）
     */
    fun getOrDefault(str: String?, defaultValue: String): String =
        if (isEmpty(str)) defaultValue else str!!

    /**
     * 截取字符串
     */
    fun truncate(str: String?, maxLength: Int): String? {
        if (str == null) return null
        if (str.length <= maxLength) return str
        return str.substring(0, maxLength) + "..."
    }

    /**
     * 连接字符串列表
     */
    fun join(list: List<String>?, separator: String): String {
        if (list.isNullOrEmpty()) return ""
        return list.joinToString(separator)
    }

    /**
     * 连接字符串数组
     */
    fun join(array: Array<String>?, separator: String): String {
        if (array == null || array.isEmpty()) return ""
        return array.joinToString(separator)
    }

    /**
     * 解析整数（安全）
     */
    fun parseInt(str: String?, defaultValue: Int): Int {
        if (isEmpty(str)) return defaultValue
        return try {
            str!!.trim().toInt()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    /**
     * 解析浮点数（安全）
     */
    fun parseFloat(str: String?, defaultValue: Float): Float {
        if (isEmpty(str)) return defaultValue
        return try {
            str!!.trim().toFloat()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    /**
     * 解析长整数（安全）
     */
    fun parseLong(str: String?, defaultValue: Long): Long {
        if (isEmpty(str)) return defaultValue
        return try {
            str!!.trim().toLong()
        } catch (e: NumberFormatException) {
            defaultValue
        }
    }

    /**
     * 判断字符串是否为数字
     */
    fun isNumeric(str: String?): Boolean {
        if (isEmpty(str)) return false
        return str!!.all { it.isDigit() }
    }

    /**
     * 移除字符串中的HTML标签
     */
    fun stripHtml(html: String?): String {
        if (isEmpty(html)) return ""
        return html!!.replace("<[^>]*>".toRegex(), "")
    }

    /**
     * 格式化评分（保留一位小数）
     */
    fun formatRating(rating: Float): String =
        String.format(Locale.getDefault(), "%.1f", rating)

    /**
     * 从年份字符串解析年份
     */
    fun parseYear(yearStr: String?): Int {
        if (isEmpty(yearStr)) return 0
        return try {
            yearStr!!.trim().toInt()
        } catch (e: NumberFormatException) {
            if (yearStr!!.length >= 4) {
                try {
                    yearStr.substring(0, 4).toInt()
                } catch (e2: NumberFormatException) {
                    0
                }
            } else {
                0
            }
        }
    }

    /**
     * 比较字符串（忽略大小写）
     */
    fun equalsIgnoreCase(str1: String?, str2: String?): Boolean {
        if (str1 == null && str2 == null) return true
        if (str1 == null || str2 == null) return false
        return str1.equals(str2, ignoreCase = true)
    }

    /**
     * 检查字符串是否包含关键词（忽略大小写）
     */
    fun containsIgnoreCase(str: String?, keyword: String?): Boolean {
        if (isEmpty(str) || isEmpty(keyword)) return false
        return str!!.lowercase(Locale.ROOT).contains(keyword!!.lowercase(Locale.ROOT))
    }
}