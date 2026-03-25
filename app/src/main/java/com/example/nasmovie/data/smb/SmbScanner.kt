package com.example.nasmovie.data.smb

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.nasmovie.data.model.SmbConfig
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * SMB 文件扫描器 (Kotlin 协程重构版)
 * 支持递归扫描、视频文件识别、NFO 和海报匹配
 */
class SmbScanner {

    companion object {
        private const val TAG = "SmbScanner"

        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp",
            "mpg", "mpeg", "m2ts", "ts", "mts", "divx", "xvid", "rm", "rmvb"
        )

        private val POSTER_NAMES = listOf(
            "poster.jpg", "poster.png", "folder.jpg", "folder.png",
            "cover.jpg", "cover.png", "fanart.jpg", "fanart.png",
            "backdrop.jpg", "backdrop.png"
        )
    }

    @Volatile
    var isScanning = false
        private set

    @Volatile
    private var isCancelled = false

    private val smbClient = SmbClient()
    private var callback: SmbScannerCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val scanJob = Job()
    private val scanScope = CoroutineScope(Dispatchers.IO + scanJob)

    private var totalFiles = 0
    private var videoFiles = 0
    private var nfoFiles = 0
    private var posterFiles = 0
    private var scanStartTime = 0L

    private val allFiles = mutableListOf<SmbFileInfo>()
    private val movies = mutableListOf<ScanResult.ScannedMovie>()
    private val movieMap = mutableMapOf<String, ScanResult.ScannedMovie>()

    fun setCallback(callback: SmbScannerCallback?) {
        this.callback = callback
    }

    fun startScan(config: SmbConfig, startPath: String, recursive: Boolean) {
        if (isScanning) {
            Log.w(TAG, "Scan already in progress")
            return
        }

        isScanning = true
        isCancelled = false
        totalFiles = 0
        videoFiles = 0
        nfoFiles = 0
        posterFiles = 0
        scanStartTime = System.currentTimeMillis()
        allFiles.clear()
        movies.clear()
        movieMap.clear()

        notifyScanStart()

        scanScope.launch {
            try {
                performScan(config, startPath, recursive)
            } catch (e: Exception) {
                Log.e(TAG, "Scan error: ${e.message}", e)
                notifyScanError(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun performScan(config: SmbConfig, startPath: String, recursive: Boolean) {
        Log.d(TAG, "Starting scan, path: $startPath")

        val connected = smbClient.connect(config)
        if (!connected) {
            throw Exception("Failed to connect to SMB server: ${config.host}")
        }

        try {
            val diskShare = smbClient.diskShare ?: throw Exception("Failed to get disk share")

            var smbPath = convertToSmbPath(startPath)
            if (smbPath.isEmpty()) {
                smbPath = "\\"
            }

            scanDirectory(diskShare, smbPath, recursive)
            processScanResults()

            val result = ScanResult().apply {
                status = if (isCancelled) ScanResult.Status.CANCELLED else ScanResult.Status.SUCCESS
                this.totalFiles = this@SmbScanner.totalFiles
                this.videoFiles = this@SmbScanner.videoFiles
                this.nfoFiles = this@SmbScanner.nfoFiles
                this.posterFiles = this@SmbScanner.posterFiles
                scanTime = System.currentTimeMillis() - scanStartTime
                files = allFiles
                this.movies = this@SmbScanner.movies
            }

            if (isCancelled) {
                notifyScanCancelled()
            } else {
                notifyScanComplete(result)
            }

        } finally {
            smbClient.disconnect()
            isScanning = false
        }
    }

    private fun scanDirectory(share: DiskShare, path: String, recursive: Boolean): Boolean {
        if (isCancelled) return false

        try {
            val fileList = share.list(path)
            val filesInDir = mutableListOf<FileIdBothDirectoryInformation>()
            val subDirs = mutableListOf<FileIdBothDirectoryInformation>()

            for (fileInfo in fileList) {
                val fileName = fileInfo.fileName
                if (fileName == "." || fileName == "..") continue

                val isDirectory = (fileInfo.fileAttributes and 0x10.toLong()) != 0L
                if (isDirectory) {
                    subDirs.add(fileInfo)
                } else {
                    filesInDir.add(fileInfo)
                }
            }

            var hasVideoInCurrentDir = false
            for (fileInfo in filesInDir) {
                if (isCancelled) return false

                val fileName = fileInfo.fileName
                val fullPath = if (path == "\\") "\\$fileName" else "$path\\$fileName"

                val smbFile = SmbFileInfo().apply {
                    name = fileName
                    this.path = fullPath
                    isDirectory = false
                    fileSize = fileInfo.endOfFile
                    lastModified = fileInfo.lastWriteTime.toEpoch(java.util.concurrent.TimeUnit.MILLISECONDS)
                }

                allFiles.add(smbFile)
                totalFiles++
                notifyScanProgress(totalFiles, -1, fullPath)

                if (processFile(share, smbFile, path)) {
                    hasVideoInCurrentDir = true
                }
            }

            if (hasVideoInCurrentDir) return true

            if (recursive && subDirs.isNotEmpty()) {
                var hasVideoInSubDirs = false
                for (dirInfo in subDirs) {
                    if (isCancelled) return hasVideoInSubDirs

                    val dirName = dirInfo.fileName
                    val fullPath = if (path == "\\") "\\$dirName" else "$path\\$dirName"

                    try {
                        val subResult = scanDirectory(share, fullPath, recursive)
                        if (subResult) {
                            hasVideoInSubDirs = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error scanning subdirectory $dirName: ${e.message}")
                    }
                }
                return hasVideoInSubDirs
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning directory $path: ${e.message}")
            return false
        }
    }

    private fun processFile(share: DiskShare, fileInfo: SmbFileInfo, parentPath: String): Boolean {
        val fileName = fileInfo.name ?: return false
        val lowerName = fileName.lowercase(Locale.ROOT)
        val baseName = getBaseName(fileName)

        if (isVideoFile(fileName)) {
            videoFiles++
            val movie = ScanResult.ScannedMovie(baseName, fileInfo.path).apply {
                videoSize = fileInfo.fileSize
                videoFormat = fileInfo.extension
            }

            val nfoPath = findNfoFile(share, parentPath, baseName)
            if (nfoPath != null) {
                movie.nfoPath = nfoPath
                nfoFiles++
            }

            val posterPath = findPosterFile(share, parentPath)
            if (posterPath != null) {
                movie.posterPath = posterPath
                posterFiles++
            }

            movies.add(movie)
            movieMap[baseName.lowercase(Locale.ROOT)] = movie
            notifyVideoFound(fileInfo, movie)
            return true
        }

        if (lowerName.endsWith(".nfo")) {
            val nfoBaseName = getBaseName(fileName)
            val movie = movieMap[nfoBaseName.lowercase(Locale.ROOT)]
            if (movie != null && movie.nfoPath == null) {
                movie.nfoPath = fileInfo.path
                nfoFiles++
                notifyNfoFound(fileInfo)
            }
        } else if (isPosterFile(fileName)) {
            posterFiles++
            notifyPosterFound(fileInfo)
        }
        return false
    }

    private fun findNfoFile(share: DiskShare, dirPath: String, baseName: String): String? {
        try {
            val files = share.list(dirPath)
            val nfoName = "$baseName.nfo"
            for (file in files) {
                if (file.fileName.equals(nfoName, ignoreCase = true)) {
                    return if (dirPath == "\\") "\\${file.fileName}" else "$dirPath\\${file.fileName}"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not find NFO file in $dirPath")
        }
        return null
    }

    private fun findPosterFile(share: DiskShare, dirPath: String): String? {
        try {
            val files = share.list(dirPath)
            for (posterName in POSTER_NAMES) {
                for (file in files) {
                    if (file.fileName.equals(posterName, ignoreCase = true)) {
                        return if (dirPath == "\\") "\\${file.fileName}" else "$dirPath\\${file.fileName}"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not find poster in $dirPath")
        }
        return null
    }

    private fun processScanResults() {
        var withNfo = 0
        var withPoster = 0
        for (movie in movies) {
            if (movie.hasNfo()) withNfo++
            if (movie.hasPoster()) withPoster++
        }
        Log.d(TAG, "Movies with NFO: $withNfo, with poster: $withPoster")
    }

    private fun isVideoFile(fileName: String?): Boolean {
        if (fileName == null || !fileName.contains(".")) return false
        val ext = fileName.substring(fileName.lastIndexOf(".") + 1).lowercase(Locale.ROOT)
        return VIDEO_EXTENSIONS.contains(ext)
    }

    private fun isPosterFile(fileName: String?): Boolean {
        if (fileName == null) return false
        val lowerName = fileName.lowercase(Locale.ROOT)
        return POSTER_NAMES.contains(lowerName)
    }

    private fun getBaseName(fileName: String?): String {
        if (fileName == null) return ""
        val dotIndex = fileName.lastIndexOf(".")
        return if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
    }

    private fun convertToSmbPath(path: String?): String {
        if (path.isNullOrEmpty() || path == "/") return ""
        return path.replace("/", "\\")
    }

    fun cancelScan() {
        isCancelled = true
    }

    fun release() {
        cancelScan()
        scanJob.cancel()
        smbClient.disconnect()
    }

    // ==================== 回调通知方法 ====================

    private fun notifyScanStart() {
        callback?.let { cb -> mainHandler.post { cb.onScanStart() } }
    }

    private fun notifyScanProgress(current: Int, total: Int, currentPath: String) {
        callback?.let { cb -> mainHandler.post { cb.onScanProgress(current, total, currentPath) } }
    }

    private fun notifyVideoFound(fileInfo: SmbFileInfo, movie: ScanResult.ScannedMovie) {
        callback?.let { cb -> mainHandler.post { cb.onVideoFound(fileInfo, movie) } }
    }

    private fun notifyNfoFound(fileInfo: SmbFileInfo) {
        callback?.let { cb -> mainHandler.post { cb.onNfoFound(fileInfo) } }
    }

    private fun notifyPosterFound(fileInfo: SmbFileInfo) {
        callback?.let { cb -> mainHandler.post { cb.onPosterFound(fileInfo) } }
    }

    private fun notifyScanComplete(result: ScanResult) {
        callback?.let { cb -> mainHandler.post { cb.onScanComplete(result) } }
    }

    private fun notifyScanError(error: String) {
        isScanning = false
        callback?.let { cb -> mainHandler.post { cb.onScanError(error) } }
    }

    private fun notifyScanCancelled() {
        isScanning = false
        callback?.let { cb -> mainHandler.post { cb.onScanCancelled() } }
    }
}
