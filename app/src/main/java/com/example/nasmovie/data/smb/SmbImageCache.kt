package com.example.nasmovie.data.smb

import android.content.Context
import android.util.Log
import com.example.nasmovie.data.model.SmbConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * SMB 图片缓存管理器 (Kotlin 协程重构版)
 * 负责将 SMB 图片下载到本地缓存，并提供缓存路径
 */
class SmbImageCache(context: Context) {

    companion object {
        private const val TAG = "SmbImageCache"
        private const val CACHE_DIR = "poster_cache"
        // 最大缓存大小（100MB）
        private const val MAX_CACHE_SIZE = 100L * 1024 * 1024
    }

    private val applicationContext = context.applicationContext
    private val downloadingTasks = ConcurrentHashMap<String, Boolean>()
    private val cacheMap = ConcurrentHashMap<String, String>()

    private val job = Job()
    private val ioScope = CoroutineScope(Dispatchers.IO + job)

    init {
        initCacheDir()
    }

    private fun initCacheDir() {
        val cacheDir = getCacheDir()
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    private fun getCacheDir(): File {
        return File(applicationContext.cacheDir, CACHE_DIR)
    }

    fun getCachedImagePath(smbPath: String): String? {
        // 先检查内存缓存
        if (cacheMap.containsKey(smbPath)) {
            val localPath = cacheMap[smbPath]
            if (localPath != null && File(localPath).exists()) {
                return localPath
            }
            cacheMap.remove(smbPath)
        }

        // 检查磁盘缓存
        val cacheFile = File(getCacheDir(), generateCacheFileName(smbPath))
        if (cacheFile.exists() && cacheFile.length() > 0) {
            val path = cacheFile.absolutePath
            cacheMap[smbPath] = path
            return path
        }

        return null
    }

    fun downloadImageAsync(smbPath: String, config: SmbConfig, callback: DownloadCallback?) {
        val cachedPath = getCachedImagePath(smbPath)
        if (cachedPath != null) {
            callback?.onSuccess(cachedPath)
            return
        }

        if (downloadingTasks.putIfAbsent(smbPath, true) != null) {
            Log.d(TAG, "Image already downloading: $smbPath")
            return
        }

        ioScope.launch {
            try {
                val localPath = downloadImage(smbPath, config)
                downloadingTasks.remove(smbPath)

                withContext(Dispatchers.Main) {
                    if (localPath != null) {
                        callback?.onSuccess(localPath)
                    } else {
                        callback?.onError("Download failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                downloadingTasks.remove(smbPath)
                withContext(Dispatchers.Main) {
                    callback?.onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    suspend fun downloadImage(smbPath: String, config: SmbConfig): String? = withContext(Dispatchers.IO) {
        val cachedPath = getCachedImagePath(smbPath)
        if (cachedPath != null) {
            return@withContext cachedPath
        }

        val cacheFile = File(getCacheDir(), generateCacheFileName(smbPath))
        val dir = cacheFile.parentFile
        if (dir != null && !dir.exists()) {
            dir.mkdirs()
        }

        try {
            SmbClient().use { client ->
                if (!client.connect(config)) {
                    Log.e(TAG, "Failed to connect to SMB server for image: $smbPath")
                    return@withContext null
                }

                client.readFile(smbPath)?.use { inputStream ->
                    FileOutputStream(cacheFile).use { fos ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                        fos.flush()
                        Log.d(TAG, "Downloaded $totalBytes bytes for $smbPath")
                    }
                } ?: run {
                    Log.e(TAG, "Failed to read SMB file: $smbPath")
                    return@withContext null
                }
            }

            val localPath = cacheFile.absolutePath
            cacheMap[smbPath] = localPath
            trimCacheIfNeeded()
            return@withContext localPath

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image: ${e.message}", e)
            return@withContext null
        }
    }

    private fun generateCacheFileName(smbPath: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val hash = md.digest(smbPath.toByteArray(Charsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hash) {
                val hex = Integer.toHexString(0xff and b.toInt())
                if (hex.length == 1) hexString.append('0')
                hexString.append(hex)
            }
            var ext = ""
            val dotIndex = smbPath.lastIndexOf('.')
            if (dotIndex > 0) {
                ext = smbPath.substring(dotIndex).lowercase(Locale.ROOT)
                if (ext.length > 5) ext = ""
            }
            hexString.toString() + ext
        } catch (e: Exception) {
            smbPath.hashCode().toString()
        }
    }

    private fun trimCacheIfNeeded() {
        val cacheDir = getCacheDir()
        val files = cacheDir.listFiles() ?: return

        var totalSize = 0L
        for (file in files) {
            totalSize += file.length()
        }

        if (totalSize > MAX_CACHE_SIZE) {
            Log.d(TAG, "Cache size $totalSize exceeds limit, trimming...")
            files.sortBy { it.lastModified() }

            val targetSize = MAX_CACHE_SIZE * 3 / 4
            for (file in files) {
                if (totalSize <= targetSize) break
                val fileSize = file.length()
                if (file.delete()) {
                    totalSize -= fileSize
                    Log.d(TAG, "Deleted old cache file: ${file.name}")
                }
            }
        }
    }

    fun clearCache() {
        val cacheDir = getCacheDir()
        cacheDir.listFiles()?.forEach { it.delete() }
        cacheMap.clear()
        Log.d(TAG, "Cache cleared")
    }

    fun release() {
        job.cancel()
    }

    interface DownloadCallback {
        fun onSuccess(localPath: String)
        fun onError(error: String)
    }
}
