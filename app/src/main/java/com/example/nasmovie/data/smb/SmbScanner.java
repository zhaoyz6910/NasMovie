package com.example.nasmovie.data.smb;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.nasmovie.data.model.SmbConfig;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SMB 文件扫描器
 * 基于 infuse-android 项目参考实现
 * 支持递归扫描、视频文件识别、NFO 和海报匹配
 */
public class SmbScanner {

    private static final String TAG = "SmbScanner";

    // 视频文件扩展名
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>();
    static {
        VIDEO_EXTENSIONS.add("mp4");
        VIDEO_EXTENSIONS.add("mkv");
        VIDEO_EXTENSIONS.add("avi");
        VIDEO_EXTENSIONS.add("mov");
        VIDEO_EXTENSIONS.add("wmv");
        VIDEO_EXTENSIONS.add("flv");
        VIDEO_EXTENSIONS.add("webm");
        VIDEO_EXTENSIONS.add("m4v");
        VIDEO_EXTENSIONS.add("3gp");
        VIDEO_EXTENSIONS.add("mpg");
        VIDEO_EXTENSIONS.add("mpeg");
        VIDEO_EXTENSIONS.add("m2ts");
        VIDEO_EXTENSIONS.add("ts");
        VIDEO_EXTENSIONS.add("mts");
        VIDEO_EXTENSIONS.add("divx");
        VIDEO_EXTENSIONS.add("xvid");
        VIDEO_EXTENSIONS.add("rm");
        VIDEO_EXTENSIONS.add("rmvb");
    }

    // 海报文件名（按优先级排序）
    private static final List<String> POSTER_NAMES = new ArrayList<>();
    static {
        POSTER_NAMES.add("poster.jpg");
        POSTER_NAMES.add("poster.png");
        POSTER_NAMES.add("folder.jpg");
        POSTER_NAMES.add("folder.png");
        POSTER_NAMES.add("cover.jpg");
        POSTER_NAMES.add("cover.png");
        POSTER_NAMES.add("fanart.jpg");
        POSTER_NAMES.add("fanart.png");
        POSTER_NAMES.add("backdrop.jpg");
        POSTER_NAMES.add("backdrop.png");
    }

    // 扫描状态
    private volatile boolean isScanning = false;
    private volatile boolean isCancelled = false;

    // SMB 客户端
    private SmbClient smbClient;

    // 回调
    private SmbScannerCallback callback;

    // 主线程 Handler
    private Handler mainHandler;

    // 线程池
    private ExecutorService executorService;

    // 扫描统计
    private int totalFiles = 0;
    private int videoFiles = 0;
    private int nfoFiles = 0;
    private int posterFiles = 0;
    private long scanStartTime = 0;

    // 扫描结果
    private List<SmbFileInfo> allFiles;
    private List<ScanResult.ScannedMovie> movies;
    private Map<String, ScanResult.ScannedMovie> movieMap; // 用于匹配 NFO 和海报

    public SmbScanner() {
        this.smbClient = new SmbClient();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newSingleThreadExecutor();
        this.allFiles = new ArrayList<>();
        this.movies = new ArrayList<>();
        this.movieMap = new HashMap<>();
    }

    /**
     * 测试方法：直接列出指定路径的内容（用于调试）
     * @param config SMB 配置
     * @param testPath 要测试的路径
     */
    public void testListPath(SmbConfig config, String testPath) {
        new Thread(() -> {
            try {
                Log.d(TAG, "=== TEST LIST PATH ===");
                Log.d(TAG, "Host: " + config.getHost());
                Log.d(TAG, "Share: " + config.getShareName());
                Log.d(TAG, "Test Path: " + testPath);

                SmbClient client = new SmbClient();
                if (!client.connect(config)) {
                    Log.e(TAG, "Failed to connect");
                    return;
                }

                DiskShare share = client.getDiskShare();
                if (share == null) {
                    Log.e(TAG, "Failed to get share");
                    client.disconnect();
                    return;
                }

                // 尝试列出路径
                String smbPath = convertToSmbPath(testPath);
                if (smbPath.isEmpty()) {
                    smbPath = "\\";
                }
                Log.d(TAG, "Listing: '" + smbPath + "'");

                List<FileIdBothDirectoryInformation> list = share.list(smbPath);
                Log.d(TAG, "Found " + list.size() + " items:");

                for (FileIdBothDirectoryInformation item : list) {
                    String name = item.getFileName();
                    if (name.equals(".") || name.equals("..")) continue;

                    boolean isDir = (item.getFileAttributes() & 0x10) != 0;
                    Log.d(TAG, "  " + (isDir ? "[DIR] " : "[FILE] ") + name +
                          " size=" + item.getEndOfFile() +
                          " attrs=" + item.getFileAttributes());
                }

                client.disconnect();
                Log.d(TAG, "=== END TEST ===");

            } catch (Exception e) {
                Log.e(TAG, "Test failed: " + e.getMessage(), e);
            }
        }).start();
    }
    public void setCallback(SmbScannerCallback callback) {
        this.callback = callback;
    }

    /**
     * 开始扫描（嵌套扫描模式：扫描到影片为止）
     * @param config SMB 配置
     * @param startPath 起始路径（相对于共享文件夹）
     * @param recursive 是否递归扫描（嵌套扫描模式下，遇到视频文件会停止深入）
     */
    public void startScan(SmbConfig config, String startPath, boolean recursive) {
        if (isScanning) {
            Log.w(TAG, "Scan already in progress");
            return;
        }

        isScanning = true;
        isCancelled = false;
        totalFiles = 0;
        videoFiles = 0;
        nfoFiles = 0;
        posterFiles = 0;
        scanStartTime = System.currentTimeMillis();
        allFiles.clear();
        movies.clear();
        movieMap.clear();

        // 通知开始
        notifyScanStart();

        // 在后台线程执行扫描
        executorService.execute(() -> {
            try {
                performScan(config, startPath, recursive);
            } catch (Exception e) {
                Log.e(TAG, "Scan error: " + e.getMessage(), e);
                notifyScanError(e.getMessage());
            }
        });
    }

    /**
     * 执行扫描
     */
    private void performScan(SmbConfig config, String startPath, boolean recursive) throws Exception {
        Log.d(TAG, "Starting scan: " + config.getHost() + ", path: " + startPath);

        // 连接到 SMB 服务器
        boolean connected = smbClient.connect(config);
        if (!connected) {
            throw new Exception("Failed to connect to SMB server: " + config.getHost());
        }

        try {
            DiskShare diskShare = smbClient.getDiskShare();
            if (diskShare == null) {
                throw new Exception("Failed to get disk share");
            }

            // 转换路径格式
            String smbPath = convertToSmbPath(startPath);
            Log.d(TAG, "Converted path: '" + startPath + "' -> '" + smbPath + "'");
            if (smbPath.isEmpty()) {
                smbPath = "\\";
            }
            Log.d(TAG, "Final SMB path: '" + smbPath + "'");

            // 递归扫描
            scanDirectory(diskShare, smbPath, recursive);

            // 处理扫描结果
            processScanResults();

            // 创建扫描结果
            ScanResult result = new ScanResult();
            result.setStatus(isCancelled ? ScanResult.Status.CANCELLED : ScanResult.Status.SUCCESS);
            result.setTotalFiles(totalFiles);
            result.setVideoFiles(videoFiles);
            result.setNfoFiles(nfoFiles);
            result.setPosterFiles(posterFiles);
            result.setScanTime(System.currentTimeMillis() - scanStartTime);
            result.setFiles(allFiles);
            result.setMovies(movies);

            if (isCancelled) {
                notifyScanCancelled();
            } else {
                notifyScanComplete(result);
            }

        } finally {
            smbClient.disconnect();
            isScanning = false;
        }
    }

    /**
     * 扫描目录（嵌套扫描模式）
     * 扫描到影片为止：如果当前目录包含视频文件，则停止继续深入扫描子目录
     * @param share DiskShare
     * @param path 当前路径
     * @param recursive 是否递归
     * @return 该目录或其子目录是否包含视频文件
     */
    private boolean scanDirectory(DiskShare share, String path, boolean recursive) {
        if (isCancelled) {
            return false;
        }

        try {
            List<FileIdBothDirectoryInformation> fileList = share.list(path);
            Log.d(TAG, "Scanning directory: " + path + ", found " + fileList.size() + " items");

            // 第一步：收集当前目录的所有项目
            List<FileIdBothDirectoryInformation> filesInDir = new ArrayList<>();
            List<FileIdBothDirectoryInformation> subDirs = new ArrayList<>();

            for (FileIdBothDirectoryInformation fileInfo : fileList) {
                String fileName = fileInfo.getFileName();

                // 跳过 . 和 ..
                if (fileName.equals(".") || fileName.equals("..")) {
                    continue;
                }

                // 从文件属性判断是否是目录 (FILE_ATTRIBUTE_DIRECTORY = 0x10)
                boolean isDirectory = (fileInfo.getFileAttributes() & 0x10) != 0;

                Log.d(TAG, "Found item: " + fileName + " isDirectory=" + isDirectory + " attrs=" + fileInfo.getFileAttributes());

                if (isDirectory) {
                    subDirs.add(fileInfo);
                } else {
                    filesInDir.add(fileInfo);
                }
            }

            Log.d(TAG, "Directory " + path + ": " + filesInDir.size() + " files, " + subDirs.size() + " subdirs");

            // 第二步：处理当前目录的文件，检查是否有视频文件
            boolean hasVideoInCurrentDir = false;
            Log.d(TAG, "Processing " + filesInDir.size() + " files in " + path);
            for (FileIdBothDirectoryInformation fileInfo : filesInDir) {
                if (isCancelled) {
                    return false;
                }

                String fileName = fileInfo.getFileName();
                String fullPath = path.equals("\\")
                    ? "\\" + fileName
                    : path + "\\" + fileName;

                Log.d(TAG, "Checking file: " + fileName + " ext=" + getExtension(fileName) + " isVideo=" + isVideoFile(fileName));

                // 创建文件信息对象
                SmbFileInfo smbFile = new SmbFileInfo();
                smbFile.setName(fileName);
                smbFile.setPath(fullPath);
                smbFile.setDirectory(false);
                smbFile.setFileSize(fileInfo.getEndOfFile());
                smbFile.setLastModified(fileInfo.getLastWriteTime().toEpoch(java.util.concurrent.TimeUnit.MILLISECONDS));

                allFiles.add(smbFile);
                totalFiles++;

                // 更新进度
                notifyScanProgress(totalFiles, -1, fullPath);

                // 处理文件
                if (processFile(share, smbFile, path)) {
                    hasVideoInCurrentDir = true;
                }
            }

            Log.d(TAG, "Directory " + path + " hasVideoInCurrentDir=" + hasVideoInCurrentDir + " subDirs=" + subDirs.size());

            // 第三步：决定是否继续扫描子目录
            // 如果当前目录有视频文件，停止深入扫描子目录（嵌套扫描到影片为止）
            if (hasVideoInCurrentDir) {
                Log.d(TAG, "Directory " + path + " contains videos, stopping deeper scan");
                return true;
            }

            // 如果当前目录没有视频文件，继续递归扫描子目录
            if (recursive && !subDirs.isEmpty()) {
                Log.d(TAG, "Scanning " + subDirs.size() + " subdirectories in " + path);
                boolean hasVideoInSubDirs = false;

                for (FileIdBothDirectoryInformation dirInfo : subDirs) {
                    if (isCancelled) {
                        Log.d(TAG, "Scan cancelled, returning");
                        return hasVideoInSubDirs;
                    }

                    String dirName = dirInfo.getFileName();
                    String fullPath = path.equals("\\")
                        ? "\\" + dirName
                        : path + "\\" + dirName;

                    Log.d(TAG, "Entering subdirectory: " + dirName + " (full path: " + fullPath + ")");

                    // 递归扫描子目录
                    try {
                        boolean subResult = scanDirectory(share, fullPath, recursive);
                        Log.d(TAG, "Subdirectory " + dirName + " scan result: " + subResult);
                        if (subResult) {
                            hasVideoInSubDirs = true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error scanning subdirectory " + dirName + ": " + e.getMessage());
                    }
                }

                Log.d(TAG, "Finished scanning subdirectories in " + path + ", hasVideoInSubDirs=" + hasVideoInSubDirs);
                return hasVideoInSubDirs;
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error scanning directory " + path + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理文件
     * @param share DiskShare
     * @param fileInfo 文件信息
     * @param parentPath 父目录路径
     * @return 是否是视频文件
     */
    private boolean processFile(DiskShare share, SmbFileInfo fileInfo, String parentPath) {
        String fileName = fileInfo.getName();
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        String baseName = getBaseName(fileName);

        Log.d(TAG, "Processing file: " + fileName + " in " + parentPath);

        // 检查是否是视频文件
        if (isVideoFile(fileName)) {
            Log.d(TAG, "Video file detected: " + fileName);
            videoFiles++;

            // 创建电影对象
            ScanResult.ScannedMovie movie = new ScanResult.ScannedMovie(baseName, fileInfo.getPath());
            movie.setVideoSize(fileInfo.getFileSize());
            movie.setVideoFormat(fileInfo.getExtension());

            // 查找同目录下的 NFO 和海报
            String nfoPath = findNfoFile(share, parentPath, baseName);
            if (nfoPath != null) {
                movie.setNfoPath(nfoPath);
                nfoFiles++;
            }

            String posterPath = findPosterFile(share, parentPath);
            if (posterPath != null) {
                movie.setPosterPath(posterPath);
                posterFiles++;
            }

            movies.add(movie);
            movieMap.put(baseName.toLowerCase(Locale.ROOT), movie);

            notifyVideoFound(fileInfo, movie);

            Log.d(TAG, "Found video: " + fileName + ", nfo: " + (nfoPath != null) + ", poster: " + (posterPath != null));
            return true;
        } else {
            Log.d(TAG, "Not a video file: " + fileName);
        }
        // 检查是否是 NFO 文件
        if (lowerName.endsWith(".nfo")) {
            String nfoBaseName = getBaseName(fileName);
            ScanResult.ScannedMovie movie = movieMap.get(nfoBaseName.toLowerCase(Locale.ROOT));
            if (movie != null && movie.getNfoPath() == null) {
                movie.setNfoPath(fileInfo.getPath());
                nfoFiles++;
                notifyNfoFound(fileInfo);
            }
        }
        // 检查是否是海报文件
        else if (isPosterFile(fileName)) {
            posterFiles++;
            notifyPosterFound(fileInfo);
        }

        return false;
    }

    /**
     * 查找 NFO 文件
     */
    private String findNfoFile(DiskShare share, String dirPath, String baseName) {
        try {
            List<FileIdBothDirectoryInformation> files = share.list(dirPath);
            String nfoName = baseName + ".nfo";

            for (FileIdBothDirectoryInformation file : files) {
                if (file.getFileName().equalsIgnoreCase(nfoName)) {
                    return dirPath.equals("\\")
                        ? "\\" + file.getFileName()
                        : dirPath + "\\" + file.getFileName();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not find NFO file in " + dirPath);
        }
        return null;
    }

    /**
     * 查找海报文件
     */
    private String findPosterFile(DiskShare share, String dirPath) {
        try {
            List<FileIdBothDirectoryInformation> files = share.list(dirPath);

            for (String posterName : POSTER_NAMES) {
                for (FileIdBothDirectoryInformation file : files) {
                    if (file.getFileName().equalsIgnoreCase(posterName)) {
                        return dirPath.equals("\\")
                            ? "\\" + file.getFileName()
                            : dirPath + "\\" + file.getFileName();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not find poster in " + dirPath);
        }
        return null;
    }

    /**
     * 处理扫描结果（匹配 NFO 和海报）
     */
    private void processScanResults() {
        Log.d(TAG, "Processing scan results: " + movies.size() + " movies found");

        // 统计
        int withNfo = 0;
        int withPoster = 0;

        for (ScanResult.ScannedMovie movie : movies) {
            if (movie.hasNfo()) withNfo++;
            if (movie.hasPoster()) withPoster++;
        }

        Log.d(TAG, "Movies with NFO: " + withNfo + ", with poster: " + withPoster);
    }

    /**
     * 判断是否是目录
     */
    private boolean isDirectory(DiskShare share, String path) {
        try {
            share.list(path);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查是否是视频文件
     */
    private boolean isVideoFile(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return false;
        }
        String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        return VIDEO_EXTENSIONS.contains(ext);
    }

    /**
     * 检查是否是海报文件
     */
    private boolean isPosterFile(String fileName) {
        if (fileName == null) return false;
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        for (String posterName : POSTER_NAMES) {
            if (lowerName.equals(posterName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 获取文件名（不带扩展名）
     */
    private String getBaseName(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf(".");
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    /**
     * 转换路径为 SMB 格式
     */
    private String convertToSmbPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return "";
        }
        return path.replace("/", "\\");
    }

    /**
     * 取消扫描
     */
    public void cancelScan() {
        Log.d(TAG, "Cancelling scan");
        isCancelled = true;
    }

    /**
     * 是否正在扫描
     */
    public boolean isScanning() {
        return isScanning;
    }

    /**
     * 释放资源
     */
    public void release() {
        cancelScan();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        smbClient.disconnect();
    }

    // ==================== 回调通知方法 ====================

    private void notifyScanStart() {
        if (callback != null) {
            mainHandler.post(() -> callback.onScanStart());
        }
    }

    private void notifyScanProgress(int current, int total, String currentPath) {
        if (callback != null) {
            mainHandler.post(() -> callback.onScanProgress(current, total, currentPath));
        }
    }

    private void notifyVideoFound(SmbFileInfo fileInfo, ScanResult.ScannedMovie movie) {
        if (callback != null) {
            mainHandler.post(() -> callback.onVideoFound(fileInfo, movie));
        }
    }

    private void notifyNfoFound(SmbFileInfo fileInfo) {
        if (callback != null) {
            mainHandler.post(() -> callback.onNfoFound(fileInfo));
        }
    }

    private void notifyPosterFound(SmbFileInfo fileInfo) {
        if (callback != null) {
            mainHandler.post(() -> callback.onPosterFound(fileInfo));
        }
    }

    private void notifyScanComplete(ScanResult result) {
        if (callback != null) {
            mainHandler.post(() -> callback.onScanComplete(result));
        }
    }

    private void notifyScanError(String error) {
        isScanning = false;
        if (callback != null) {
            mainHandler.post(() -> callback.onScanError(error));
        }
    }

    private void notifyScanCancelled() {
        isScanning = false;
        if (callback != null) {
            mainHandler.post(() -> callback.onScanCancelled());
        }
    }
}
