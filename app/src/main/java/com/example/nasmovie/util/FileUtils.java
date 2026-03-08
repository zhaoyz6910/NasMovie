package com.example.nasmovie.util;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

/**
 * 文件工具类
 */
public class FileUtils {

    /**
     * 获取文件扩展名
     */
    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 获取不带扩展名的文件名
     */
    public static String getNameWithoutExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf(".");
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }

    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            DecimalFormat df = new DecimalFormat("#.##");
            return df.format(bytes / (1024.0 * 1024 * 1024)) + " GB";
        }
    }

    /**
     * 格式化时长（毫秒转为时分秒）
     */
    public static String formatDuration(long milliseconds) {
        int seconds = (int) (milliseconds / 1000);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    /**
     * 格式化时长（分钟转为时分）
     */
    public static String formatDurationMinutes(int minutes) {
        int hours = minutes / 60;
        int mins = minutes % 60;

        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, mins);
        } else {
            return String.format("%d分钟", mins);
        }
    }

    /**
     * 计算字符串的MD5哈希值
     */
    public static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    /**
     * 生成电影ID（基于路径）
     */
    public static String generateMovieId(String path) {
        return md5(path);
    }

    /**
     * 判断是否为视频文件
     */
    public static boolean isVideoFile(String filename) {
        String ext = getExtension(filename);
        return "mp4".equals(ext) || "mkv".equals(ext);
    }

    /**
     * 判断是否为字幕文件
     */
    public static boolean isSubtitleFile(String filename) {
        String ext = getExtension(filename);
        return "srt".equals(ext) || "ass".equals(ext) || "ssa".equals(ext)
            || "sub".equals(ext) || "vtt".equals(ext);
    }

    /**
     * 判断是否为海报图片
     */
    public static boolean isPosterImage(String filename) {
        if (filename == null) return false;
        String lowerName = filename.toLowerCase();
        return "poster.jpg".equals(lowerName) || "poster.png".equals(lowerName)
            || "folder.jpg".equals(lowerName) || "folder.png".equals(lowerName)
            || lowerName.startsWith("poster.") || lowerName.startsWith("folder.");
    }

    /**
     * 判断是否为NFO文件
     */
    public static boolean isNfoFile(String filename) {
        return "nfo".equals(getExtension(filename));
    }

    /**
     * 组合SMB路径
     */
    public static String combineSmbPath(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;

            if (sb.length() > 0 && !sb.toString().endsWith("/") && !sb.toString().endsWith("\\")) {
                sb.append("/");
            }

            // 移除开头的斜杠
            if (part.startsWith("/") || part.startsWith("\\")) {
                part = part.substring(1);
            }

            sb.append(part);
        }
        return sb.toString();
    }
}