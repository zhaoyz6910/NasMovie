package com.example.nasmovie.util;

import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

/**
 * 字符串工具类
 */
public class StringUtils {

    /**
     * 判断字符串是否为空
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * 安全获取字符串（为空时返回默认值）
     */
    public static String getOrDefault(String str, String defaultValue) {
        return isEmpty(str) ? defaultValue : str;
    }

    /**
     * 截取字符串
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    /**
     * 连接字符串列表
     */
    public static String join(List<String> list, String separator) {
        if (list == null || list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    /**
     * 连接字符串数组
     */
    public static String join(String[] array, String separator) {
        if (array == null || array.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(separator);
            sb.append(array[i]);
        }
        return sb.toString();
    }

    /**
     * 解析整数（安全）
     */
    public static int parseInt(String str, int defaultValue) {
        if (isEmpty(str)) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析浮点数（安全）
     */
    public static float parseFloat(String str, float defaultValue) {
        if (isEmpty(str)) return defaultValue;
        try {
            return Float.parseFloat(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析长整数（安全）
     */
    public static long parseLong(String str, long defaultValue) {
        if (isEmpty(str)) return defaultValue;
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 判断字符串是否为数字
     */
    public static boolean isNumeric(String str) {
        if (isEmpty(str)) return false;
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }

    /**
     * 移除字符串中的HTML标签
     */
    public static String stripHtml(String html) {
        if (isEmpty(html)) return "";
        return html.replaceAll("<[^>]*>", "");
    }

    /**
     * 格式化评分（保留一位小数）
     */
    public static String formatRating(float rating) {
        return String.format(Locale.getDefault(), "%.1f", rating);
    }

    /**
     * 从年份字符串解析年份
     */
    public static int parseYear(String yearStr) {
        if (isEmpty(yearStr)) return 0;
        try {
            // 尝试直接解析
            return Integer.parseInt(yearStr.trim());
        } catch (NumberFormatException e) {
            // 尝试从日期格式提取
            if (yearStr.length() >= 4) {
                try {
                    return Integer.parseInt(yearStr.substring(0, 4));
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    /**
     * 比较字符串（忽略大小写）
     */
    public static boolean equalsIgnoreCase(String str1, String str2) {
        if (str1 == null && str2 == null) return true;
        if (str1 == null || str2 == null) return false;
        return str1.equalsIgnoreCase(str2);
    }

    /**
     * 检查字符串是否包含关键词（忽略大小写）
     */
    public static boolean containsIgnoreCase(String str, String keyword) {
        if (isEmpty(str) || isEmpty(keyword)) return false;
        return str.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}