package com.example.nasmovie.data.smb;

import java.util.List;

/**
 * SMB 扫描结果类
 */
public class ScanResult {

    // 扫描状态
    public enum Status {
        SUCCESS,        // 扫描成功
        PARTIAL,        // 部分成功（部分文件扫描失败）
        ERROR,          // 扫描出错
        CANCELLED       // 用户取消
    }

    private Status status;                      // 扫描状态
    private int totalFiles;                     // 扫描到的文件总数
    private int videoFiles;                     // 视频文件数量
    private int nfoFiles;                       // NFO 文件数量
    private int posterFiles;                    // 海报文件数量
    private long scanTime;                      // 扫描耗时（毫秒）
    private String errorMessage;                // 错误信息
    private List<SmbFileInfo> files;            // 扫描到的文件列表
    private List<ScannedMovie> movies;          // 扫描到的电影列表

    public ScanResult() {
        this.status = Status.SUCCESS;
    }

    // Getters and Setters
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getVideoFiles() {
        return videoFiles;
    }

    public void setVideoFiles(int videoFiles) {
        this.videoFiles = videoFiles;
    }

    public int getNfoFiles() {
        return nfoFiles;
    }

    public void setNfoFiles(int nfoFiles) {
        this.nfoFiles = nfoFiles;
    }

    public int getPosterFiles() {
        return posterFiles;
    }

    public void setPosterFiles(int posterFiles) {
        this.posterFiles = posterFiles;
    }

    public long getScanTime() {
        return scanTime;
    }

    public void setScanTime(long scanTime) {
        this.scanTime = scanTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<SmbFileInfo> getFiles() {
        return files;
    }

    public void setFiles(List<SmbFileInfo> files) {
        this.files = files;
    }

    public List<ScannedMovie> getMovies() {
        return movies;
    }

    public void setMovies(List<ScannedMovie> movies) {
        this.movies = movies;
    }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.PARTIAL;
    }

    /**
     * 获取扫描速度的格式化字符串
     */
    public String getScanSpeed() {
        if (scanTime <= 0) return "0 files/s";
        double filesPerSecond = (double) totalFiles / (scanTime / 1000.0);
        return String.format("%.1f files/s", filesPerSecond);
    }

    /**
     * 获取扫描耗时的格式化字符串
     */
    public String getFormattedScanTime() {
        if (scanTime < 1000) {
            return scanTime + "ms";
        } else if (scanTime < 60000) {
            return String.format("%.1fs", scanTime / 1000.0);
        } else {
            long minutes = scanTime / 60000;
            long seconds = (scanTime % 60000) / 1000;
            return minutes + "m " + seconds + "s";
        }
    }

    @Override
    public String toString() {
        return "ScanResult{" +
            "status=" + status +
            ", totalFiles=" + totalFiles +
            ", videoFiles=" + videoFiles +
            ", nfoFiles=" + nfoFiles +
            ", posterFiles=" + posterFiles +
            ", scanTime=" + scanTime +
            ", errorMessage='" + errorMessage + '\'' +
            '}';
    }

    /**
     * 扫描到的电影信息
     */
    public static class ScannedMovie {
        private String name;                    // 电影名称（文件名不带扩展名）
        private String videoPath;               // 视频文件路径
        private String nfoPath;                 // NFO 文件路径
        private String posterPath;              // 海报图片路径
        private long videoSize;                 // 视频文件大小
        private String videoFormat;             // 视频格式（扩展名）

        public ScannedMovie(String name, String videoPath) {
            this.name = name;
            this.videoPath = videoPath;
        }

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVideoPath() {
            return videoPath;
        }

        public void setVideoPath(String videoPath) {
            this.videoPath = videoPath;
        }

        public String getNfoPath() {
            return nfoPath;
        }

        public void setNfoPath(String nfoPath) {
            this.nfoPath = nfoPath;
        }

        public String getPosterPath() {
            return posterPath;
        }

        public void setPosterPath(String posterPath) {
            this.posterPath = posterPath;
        }

        public long getVideoSize() {
            return videoSize;
        }

        public void setVideoSize(long videoSize) {
            this.videoSize = videoSize;
        }

        public String getVideoFormat() {
            return videoFormat;
        }

        public void setVideoFormat(String videoFormat) {
            this.videoFormat = videoFormat;
        }

        /**
         * 是否有 NFO 信息
         */
        public boolean hasNfo() {
            return nfoPath != null && !nfoPath.isEmpty();
        }

        /**
         * 是否有海报
         */
        public boolean hasPoster() {
            return posterPath != null && !posterPath.isEmpty();
        }

        @Override
        public String toString() {
            return "ScannedMovie{" +
                "name='" + name + '\'' +
                ", videoPath='" + videoPath + '\'' +
                ", nfoPath='" + nfoPath + '\'' +
                ", posterPath='" + posterPath + '\'' +
                ", videoSize=" + videoSize +
                ", videoFormat='" + videoFormat + '\'' +
                '}';
        }
    }
}
