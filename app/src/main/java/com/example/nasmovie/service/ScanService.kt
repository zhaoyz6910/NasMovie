package com.example.nasmovie.service

import android.util.Log
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.data.db.AppDatabase
import com.example.nasmovie.data.db.SmbConfigDao
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.data.parser.NfoParser
import com.example.nasmovie.data.repository.MovieRepository
import com.example.nasmovie.data.smb.SmbClient
import com.example.nasmovie.data.smb.SmbFileInfo
import com.example.nasmovie.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 扫描服务 (Kotlin 协程重构版)
 * 负责扫描NAS上的电影资源
 */
class ScanService {

    companion object {
        private const val TAG = "ScanService"
        private const val MAX_RECURSION_DEPTH = 5
    }

    private val database: AppDatabase = NASMovieApp.getInstance().database
    private val smbConfigDao: SmbConfigDao = database.smbConfigDao()
    private val repository = MovieRepository

    // 使用 CoroutineScope 管理后台任务
    private val scanJob = Job()
    private val scanScope = CoroutineScope(Dispatchers.Main + scanJob)

    @Volatile
    var isScanning = false
        private set

    @Volatile
    private var isCancelRequested = false
    private var currentClient: SmbClient? = null

    fun stopScan() {
        isCancelRequested = true
        // 在后台线程断开连接
        CoroutineScope(Dispatchers.IO).launch {
            try {
                currentClient?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error during scan stop disconnect", e)
            }
        }
    }

    /**
     * 扫描指定服务器
     */
    fun scanServer(serverId: Long, callback: ScanCallback) {
        if (isScanning) {
            callback.onError("正在扫描中，请稍候")
            return
        }

        scanScope.launch {
            isScanning = true
            isCancelRequested = false
            try {
                val config = withContext(Dispatchers.IO) { smbConfigDao.getById(serverId) }
                if (config == null) {
                    callback.onError("服务器配置不存在")
                    return@launch
                }

                callback.onStart()
                val added = withContext(Dispatchers.IO) { performScanInternal(config, callback) }

                if (isCancelRequested) {
                    callback.onError("扫描已取消")
                } else {
                    callback.onComplete(added, 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scan error", e)
                callback.onError("扫描失败: ${e.message}")
            } finally {
                isScanning = false
            }
        }
    }

    /**
     * 扫描所有服务器
     */
    fun scanAllServers(callback: ScanCallback) {
        if (isScanning) {
            callback.onError("正在扫描中，请稍候")
            return
        }

        scanScope.launch {
            isScanning = true
            isCancelRequested = false
            var totalAdded = 0
            try {
                val configs = withContext(Dispatchers.IO) { smbConfigDao.getAll() }
                if (configs.isNullOrEmpty()) {
                    callback.onError("未配置服务器")
                    return@launch
                }

                callback.onStart()

                withContext(Dispatchers.IO) {
                    for (config in configs) {
                        if (isCancelRequested) break
                        totalAdded += performScanInternal(config, callback)
                    }
                }

                if (isCancelRequested) {
                    callback.onError("扫描已取消")
                } else {
                    callback.onComplete(totalAdded, configs.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "All scan error", e)
                callback.onError("批量扫描失败: ${e.message}")
            } finally {
                isScanning = false
            }
        }
    }

    private suspend fun performScanInternal(config: SmbConfig, callback: ScanCallback): Int {
        val movies = mutableListOf<Movie>()
        var scannedCount = 0
        var addedCount = 0

        val client = SmbClient().also { currentClient = it }
        try {
            withContext(Dispatchers.Main) { callback.onServerStart(config) }
            Log.i(TAG, "开始连接服务器")

            if (!client.connect(config)) {
                withContext(Dispatchers.Main) { callback.onError("无法连接服务器: ${config.name}") }
                return 0
            }

            val moviePath = config.moviePath ?: ""
            val items = client.listFiles(moviePath)
            val folders = items.filter { it.isDirectory }

            val total = folders.size

            if (folders.isEmpty()) {
                val rootMovies = scanMovieFolder(moviePath)
                movies.addAll(rootMovies)
                addedCount += rootMovies.size
            }

            for (folder in folders) {
                if (isCancelRequested) break

                scannedCount++
                val progress = scannedCount
                val folderName = folder.name ?: ""
                val serverName = config.name ?: ""

                withContext(Dispatchers.Main) {
                    callback.onProgress(progress, total, "[$serverName] $folderName")
                }

                val folderPath = folder.path ?: continue
                val folderMovies = scanMovieFolder(folderPath)
                movies.addAll(folderMovies)
                addedCount += folderMovies.size
            }

            if (movies.isNotEmpty() && !isCancelRequested) {
                repository.deleteMoviesByServer(config.id)
                repository.saveMovies(movies)
            }

            return addedCount

        } catch (e: Exception) {
            Log.e(TAG, "Scan internal error", e)
            return 0
        } finally {
            client.disconnect()
            currentClient = null
        }
    }

    private suspend fun scanMovieFolder(folderPath: String): List<Movie> {
        val movies = mutableListOf<Movie>()
        val client = currentClient ?: return movies
        scanMovieFolderRecursive(client, folderPath, 0, movies)
        return movies
    }

    private suspend fun scanMovieFolderRecursive(client: SmbClient, folderPath: String, depth: Int, movies: MutableList<Movie>): Boolean {
        if (depth > MAX_RECURSION_DEPTH || isCancelRequested) return false

        try {
            val files = client.listFiles(folderPath)
            var videoPath: String? = null
            var videoName: String? = null
            var nfoPath: String? = null
            var thumbPath: String? = null
            val subtitlePaths = mutableListOf<String>()
            var videoSize = 0L

            val subDirs = mutableListOf<SmbFileInfo>()
            var p1: String? = null
            var p2: String? = null
            var p3: String? = null
            var p4: String? = null
            var p5: String? = null
            var other: String? = null

            for (file in files) {
                val fileName = file.name ?: ""
                val lowerName = fileName.lowercase(Locale.ROOT)
                if (file.isDirectory) {
                    subDirs.add(file)
                    continue
                }

                val filePath = file.path ?: ""
                if (file.isVideoFile()) {
                    if (videoPath == null) {
                        videoPath = filePath
                        videoName = FileUtils.getNameWithoutExtension(fileName)
                        videoSize = file.fileSize
                    }
                } else if (file.isNfoFile()) {
                    nfoPath = filePath
                } else if (file.isThumbPoster()) {
                    thumbPath = filePath
                } else if (lowerName == "poster.jpg" || lowerName == "poster.png") {
                    p1 = filePath
                } else if (lowerName == "folder.jpg" || lowerName == "folder.png") {
                    p2 = filePath
                } else if (lowerName == "cover.jpg" || lowerName == "cover.png") {
                    p3 = filePath
                } else if (lowerName == "fanart.jpg" || lowerName == "fanart.png") {
                    p4 = filePath
                } else if (lowerName == "backdrop.jpg" || lowerName == "backdrop.png") {
                    p5 = filePath
                } else if (file.isImageFile()) {
                    if (other == null) other = filePath
                } else if (file.isSubtitleFile()) {
                    val subName = FileUtils.getNameWithoutExtension(fileName)
                    if (videoName != null && videoName == subName) {
                        subtitlePaths.add(0, filePath)
                    } else {
                        subtitlePaths.add(filePath)
                    }
                }
            }

            val posterPath = p1 ?: p2 ?: p3 ?: p4 ?: p5 ?: other

            var hasVideo = false
            if (videoPath != null) {
                hasVideo = true
                val movie = createMovie(client, folderPath, videoPath, videoName, videoSize, nfoPath, posterPath, thumbPath, subtitlePaths)
                if (movie != null) movies.add(movie)
            }

            if (!hasVideo && subDirs.isNotEmpty()) {
                for (subDir in subDirs) {
                    if (isCancelRequested) break
                    val subDirPath = subDir.path ?: continue
                    scanMovieFolderRecursive(client, subDirPath, depth + 1, movies)
                }
            }
            return hasVideo
        } catch (e: Exception) {
            Log.e(TAG, "Error recursive scanning: $folderPath", e)
            return false
        }
    }

    private suspend fun createMovie(
        client: SmbClient,
        folderPath: String, videoPath: String, @Suppress("UNUSED_PARAMETER") videoName: String?,
        videoSize: Long, nfoPath: String?, posterPath: String?, thumbPath: String?,
        subtitlePaths: List<String>
    ): Movie? {
        val config = client.config ?: return null

        val movie = Movie().apply {
            id = FileUtils.generateMovieId(videoPath)
            this.videoPath = videoPath
            fileSize = videoSize
            this.folderPath = folderPath
            serverId = config.id  // 现在直接使用 Long 类型
            this.nfoPath = nfoPath
            this.posterPath = posterPath ?: thumbPath
            this.thumbPath = thumbPath ?: posterPath
            setSubtitlePathList(subtitlePaths)
            title = getFolderName(folderPath)
        }

        if (nfoPath != null) parseNfo(client, movie, nfoPath)
        downloadAndCachePosters(movie, config)
        return movie
    }

    private suspend fun downloadAndCachePosters(movie: Movie, config: SmbConfig) {
        val imageCache = NASMovieApp.getInstance().imageCache

        val poster = movie.posterPath
        if (!poster.isNullOrEmpty()) {
            val local = imageCache.downloadImage(poster, config)
            if (local != null) movie.localPosterPath = local
        }

        val thumb = movie.thumbPath
        if (!thumb.isNullOrEmpty()) {
            if (thumb == poster) {
                movie.localThumbPath = movie.localPosterPath
            } else {
                val local = imageCache.downloadImage(thumb, config)
                if (local != null) movie.localThumbPath = local
            }
        }
    }

    private suspend fun parseNfo(client: SmbClient, movie: Movie, nfoPath: String) {
        try {
            val data = client.readFileBytes(nfoPath) ?: return
            val metadata = NfoParser.parse(data) ?: return

            if (metadata.title != null) movie.title = metadata.title
            movie.originalTitle = metadata.originalTitle
            movie.plot = metadata.plot
            movie.director = metadata.director
            movie.year = metadata.year
            movie.rating = metadata.rating
            movie.duration = metadata.runtime
            movie.setActorList(metadata.actors)
            movie.setGenreList(metadata.genres)
        } catch (e: Exception) {
            Log.e(TAG, "NFO Parse error", e)
        }
    }

    private fun getFolderName(path: String?): String {
        if (path == null) return ""
        val normalized = path.replace("/", "\\")
        val lastSep = normalized.lastIndexOf("\\")
        return if (lastSep >= 0) normalized.substring(lastSep + 1) else path
    }

    interface ScanCallback {
        fun onStart()
        fun onServerStart(config: SmbConfig)
        fun onProgress(current: Int, total: Int, currentPath: String)
        fun onComplete(addedCount: Int, totalServers: Int)
        fun onError(error: String)
    }
}