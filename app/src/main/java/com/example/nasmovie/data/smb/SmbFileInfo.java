package com.example.nasmovie.data.smb;

import java.util.Locale;

/**
 * SMB文件信息
 */
public class SmbFileInfo {

    private String name;            // 文件名
    private String path;            // 相对路径
    private boolean isDirectory;    // 是否是目录
    private long fileSize;          // 文件大小
    private long lastModified;      // 最后修改时间

    public SmbFileInfo() {}

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    /**
     * 获取文件扩展名
     */
    public String getExtension() {
        if (name == null || !name.contains(".")) {
            return "";
        }
        return name.substring(name.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 判断是否为视频文件
     */
    public boolean isVideoFile() {
        String ext = getExtension();
        return "mp4".equals(ext) || "mkv".equals(ext);
    }

    /**
     * 判断是否为字幕文件
     */
    public boolean isSubtitleFile() {
        String ext = getExtension();
        return "srt".equals(ext) || "ass".equals(ext) || "ssa".equals(ext) || "sub".equals(ext) || "vtt".equals(ext);
    }

    /**
     * 判断是否为NFO文件
     */
    public boolean isNfoFile() {
        return "nfo".equals(getExtension());
    }

    /**
     * 判断是否为 poster 图片（用于首页）
     * 包括：poster.jpg, poster.png, folder.jpg, folder.png, cover.jpg, cover.png, fanart.jpg, fanart.png, backdrop.jpg, backdrop.png
     */
    public boolean isPosterImage() {
        String lowerName = name != null ? name.toLowerCase(Locale.ROOT) : "";
        return "poster.jpg".equals(lowerName) || "poster.png".equals(lowerName)
            || "folder.jpg".equals(lowerName) || "folder.png".equals(lowerName)
            || "cover.jpg".equals(lowerName) || "cover.png".equals(lowerName)
            || "fanart.jpg".equals(lowerName) || "fanart.png".equals(lowerName)
            || "backdrop.jpg".equals(lowerName) || "backdrop.png".equals(lowerName);
    }

    /**
     * 判断是否为 thumb 图片（用于详情页）
     * 包括：thumb.jpg, thumb.png
     */
    public boolean isThumbPoster() {
        String lowerName = name != null ? name.toLowerCase(Locale.ROOT) : "";
        return "thumb.jpg".equals(lowerName) || "thumb.png".equals(lowerName);
    }

    /**
     * 判断是否为图片文件
     */
    public boolean isImageFile() {
        String ext = getExtension();
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext);
    }
}