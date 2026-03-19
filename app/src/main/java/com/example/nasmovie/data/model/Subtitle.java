package com.example.nasmovie.data.model;

import java.util.Locale;

/**
 * 字幕文件信息
 */
public class Subtitle {

    private String path;            // SMB路径
    private String name;            // 文件名
    private String extension;       // 扩展名
    private String language;        // 语言
    private boolean isDefault;      // 是否默认

    public static final String[] SUPPORTED_FORMATS = {"srt", "ass", "ssa", "sub", "vtt"};

    public Subtitle() {}

    public Subtitle(String path, String name) {
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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    /**
     * 判断是否为支持的字幕格式
     */
    public boolean isSupported() {
        if (extension == null) return false;
        for (String format : SUPPORTED_FORMATS) {
            if (format.equals(extension)) return true;
        }
        return false;
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
     * 获取显示名称
     */
    public String getDisplayName() {
        if (language != null && !language.isEmpty()) {
            return language;
        }
        return name;
    }
}