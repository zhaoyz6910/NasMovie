package com.example.nasmovie.data.model;

import java.util.Locale;

/**
 * 视频文件信息
 */
public class VideoFile {

    private String path;            // SMB路径
    private String name;            // 文件名
    private String extension;       // 扩展名
    private long size;              // 文件大小
    private long lastModified;      // 最后修改时间

    public VideoFile() {}

    public VideoFile(String path, String name) {
        this.path = path;
        this.name = name;
        if (name != null && name.contains(".")) {
            this.extension = name.substring(name.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        }
    }

    // Getters and Setters
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (name != null && name.contains(".")) {
            this.extension = name.substring(name.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        }
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * 判断是否为视频文件
     */
    public boolean isVideoFile() {
        return "mp4".equals(extension) || "mkv".equals(extension);
    }

    /**
     * 获取不带扩展名的文件名
     */
    public String getNameWithoutExtension() {
        if (name == null) return null;
        int dotIndex = name.lastIndexOf(".");
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    /**
     * 获取格式化的文件大小
     */
    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024 * 1024));
        }
    }
}