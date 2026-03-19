package com.example.nasmovie.data.smb;

import android.content.Context;
import android.util.Log;

import com.example.nasmovie.data.model.SmbConfig;
import com.hierynomus.smbj.share.DiskShare;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SMB 图片缓存管理器
 * 负责将 SMB 图片下载到本地缓存，并提供缓存路径
 */
public class SmbImageCache {

    private static final String TAG = "SmbImageCache";

    // 缓存目录名
    private static final String CACHE_DIR = "poster_cache";

    // 最大缓存大小 (100MB)
    private static final long MAX_CACHE_SIZE = 100 * 1024 * 1024;

    // 线程池
    private final ExecutorService executorService;

    // 上下文
    private final Context context;

    // 下载任务记录（避免重复下载）
    private final ConcurrentHashMap<String, Boolean> downloadingTasks;

    // 缓存映射（SMB路径 -> 本地路径）
    private final ConcurrentHashMap<String, String> cacheMap;

    public SmbImageCache(Context context) {
        this.context = context.getApplicationContext();
        this.executorService = Executors.newFixedThreadPool(3);
        this.downloadingTasks = new ConcurrentHashMap<>();
        this.cacheMap = new ConcurrentHashMap<>();

        // 初始化缓存目录
        initCacheDir();
    }

    /**
     * 初始化缓存目录
     */
    private void initCacheDir() {
        File cacheDir = getCacheDir();
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    /**
     * 获取缓存目录
     */
    private File getCacheDir() {
        return new File(context.getCacheDir(), CACHE_DIR);
    }

    /**
     * 获取缓存文件的本地路径
     * 如果缓存存在，返回本地路径；如果不存在，返回 null
     */
    public String getCachedImagePath(String smbPath) {
        // 先检查内存缓存
        if (cacheMap.containsKey(smbPath)) {
            String localPath = cacheMap.get(smbPath);
            if (new File(localPath).exists()) {
                return localPath;
            }
            // 文件不存在，从缓存中移除
            cacheMap.remove(smbPath);
        }

        // 检查磁盘缓存
        File cacheFile = new File(getCacheDir(), generateCacheFileName(smbPath));
        if (cacheFile.exists() && cacheFile.length() > 0) {
            cacheMap.put(smbPath, cacheFile.getAbsolutePath());
            return cacheFile.getAbsolutePath();
        }

        return null;
    }

    /**
     * 异步下载图片
     * @param smbPath SMB图片路径
     * @param config SMB配置
     * @param callback 下载回调
     */
    public void downloadImageAsync(String smbPath, SmbConfig config, DownloadCallback callback) {
        // 先检查本地缓存
        String cachedPath = getCachedImagePath(smbPath);
        if (cachedPath != null) {
            if (callback != null) {
                callback.onSuccess(cachedPath);
            }
            return;
        }

        // 检查是否已经在下载中
        if (downloadingTasks.putIfAbsent(smbPath, true) != null) {
            Log.d(TAG, "Image already downloading: " + smbPath);
            return;
        }

        executorService.execute(() -> {
            try {
                String localPath = downloadImage(smbPath, config);
                downloadingTasks.remove(smbPath);

                if (localPath != null && callback != null) {
                    callback.onSuccess(localPath);
                } else if (callback != null) {
                    callback.onError("Download failed");
                }
            } catch (Exception e) {
                Log.e(TAG, "Download error: " + e.getMessage(), e);
                downloadingTasks.remove(smbPath);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    /**
     * 同步下载图片
     * @param smbPath SMB图片路径
     * @param config SMB配置
     * @return 本地缓存路径，失败返回 null
     */
    public String downloadImage(String smbPath, SmbConfig config) {
        SmbClient client = null;
        FileOutputStream fos = null;
        InputStream is = null;

        try {
            // 检查是否已缓存
            String cachedPath = getCachedImagePath(smbPath);
            if (cachedPath != null) {
                Log.d(TAG, "Image already cached: " + smbPath);
                return cachedPath;
            }

            // 连接 SMB
            client = new SmbClient();
            if (!client.connect(config)) {
                Log.e(TAG, "Failed to connect to SMB server");
                return null;
            }

            DiskShare share = client.getDiskShare();
            if (share == null) {
                Log.e(TAG, "Failed to get disk share");
                return null;
            }

            // 读取 SMB 文件
            is = client.readFile(smbPath);
            if (is == null) {
                Log.e(TAG, "Failed to read SMB file: " + smbPath);
                return null;
            }

            // 创建缓存文件
            File cacheFile = new File(getCacheDir(), generateCacheFileName(smbPath));
            fos = new FileOutputStream(cacheFile);

            // 写入数据
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            fos.flush();

            Log.d(TAG, "Downloaded " + totalBytes + " bytes for " + smbPath);

            // 更新缓存映射
            String localPath = cacheFile.getAbsolutePath();
            cacheMap.put(smbPath, localPath);

            // 检查并清理缓存
            trimCacheIfNeeded();

            return localPath;

        } catch (Exception e) {
            Log.e(TAG, "Error downloading image: " + e.getMessage(), e);
            return null;
        } finally {
            try {
                if (is != null) is.close();
            } catch (Exception ignored) {}
            try {
                if (fos != null) fos.close();
            } catch (Exception ignored) {}
            if (client != null) {
                client.disconnect();
            }
        }
    }

    /**
     * 生成缓存文件名（使用 MD5 哈希）
     */
    private String generateCacheFileName(String smbPath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(smbPath.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            // 添加扩展名（如果有）
            String ext = "";
            int dotIndex = smbPath.lastIndexOf('.');
            if (dotIndex > 0) {
                ext = smbPath.substring(dotIndex).toLowerCase();
                if (ext.length() > 5) ext = ""; // 扩展名太长可能是路径的一部分
            }
            return hexString.toString() + ext;
        } catch (Exception e) {
            // 如果 MD5 失败，使用路径的 hashCode
            return String.valueOf(smbPath.hashCode());
        }
    }

    /**
     * 检查并清理缓存
     */
    private void trimCacheIfNeeded() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        if (files == null) return;

        long totalSize = 0;
        for (File file : files) {
            totalSize += file.length();
        }

        // 如果超过最大缓存大小，删除最旧的文件
        if (totalSize > MAX_CACHE_SIZE) {
            Log.d(TAG, "Cache size " + totalSize + " exceeds limit, trimming...");

            // 按最后修改时间排序
            java.util.Arrays.sort(files, (f1, f2) -> {
                return Long.compare(f1.lastModified(), f2.lastModified());
            });

            // 删除最旧的文件直到缓存大小低于限制
            long targetSize = MAX_CACHE_SIZE * 3 / 4; // 保留到 75%
            for (File file : files) {
                if (totalSize <= targetSize) break;
                long fileSize = file.length();
                if (file.delete()) {
                    totalSize -= fileSize;
                    Log.d(TAG, "Deleted old cache file: " + file.getName());
                }
            }
        }
    }

    /**
     * 清空缓存
     */
    public void clearCache() {
        File cacheDir = getCacheDir();
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        cacheMap.clear();
        Log.d(TAG, "Cache cleared");
    }

    /**
     * 下载回调接口
     */
    public interface DownloadCallback {
        void onSuccess(String localPath);
        void onError(String error);
    }

    /**
     * 释放资源
     */
    public void release() {
        executorService.shutdown();
        try {
            // 等待任务完成，最多等待5秒
            if (!executorService.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
