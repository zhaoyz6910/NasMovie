package com.example.nasmovie.util

import android.content.Context
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.data.smb.SmbImageCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * SMB 图片加载器
 * 封装 Glide 加载 SMB 图片的逻辑
 */
object SmbImageLoader {

    private const val TAG = "SmbImageLoader"
    private var imageCache: SmbImageCache? = null

    /**
     * 初始化（在 Application 中调用）
     */
    @JvmStatic
    fun init(context: Context) {
        if (imageCache == null) {
            imageCache = SmbImageCache(context)
        }
    }

    /**
     * 获取缓存实例
     */
    @JvmStatic
    fun getCache(): SmbImageCache {
        if (imageCache == null) {
            imageCache = NASMovieApp.getInstance().imageCache
        }
        return imageCache!!
    }

    /**
     * 加载电影海报（用于首页列表，优先使用 poster.jpg）
     * @param context 上下文
     * @param movie 电影对象
     * @param imageView 目标 ImageView
     */
    @JvmStatic
    fun loadPoster(context: Context, movie: Movie?, imageView: ImageView) {
        if (movie == null) {
            loadPlaceholder(context, imageView)
            return
        }

        // 确保缓存已初始化
        if (imageCache == null) {
            imageCache = NASMovieApp.getInstance().imageCache
        }

        Log.d(TAG, "Loading poster for: ${movie.title}")
        Log.d(TAG, "  posterPath: ${movie.posterPath}")
        Log.d(TAG, "  thumbPath: ${movie.thumbPath}")
        Log.d(TAG, "  localPosterPath: ${movie.localPosterPath}")
        Log.d(TAG, "  localThumbPath: ${movie.localThumbPath}")

        // 首页优先使用 localPosterPath（对应 poster.jpg）
        val localPath = movie.localPosterPath
        if (!localPath.isNullOrEmpty() && File(localPath).exists()) {
            Log.d(TAG, "  Loading from localPosterPath: $localPath")
            Glide.with(context)
                .load(File(localPath))
                .placeholder(R.drawable.bg_poster_placeholder)
                .error(R.drawable.bg_poster_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(imageView)
            return
        }

        // 其次使用 SMB posterPath
        val posterPath = movie.posterPath
        val serverId = movie.serverId

        Log.d(TAG, "  Loading from SMB posterPath: $posterPath")

        if (posterPath.isNullOrEmpty()) {
            loadPlaceholder(context, imageView)
            return
        }

        // 从数据库获取服务器配置
        loadPoster(context, posterPath, serverId, imageView)
    }

    /**
     * 加载电影详情海报（用于详情页，优先使用 thumb.jpg）
     * @param context 上下文
     * @param movie 电影对象
     * @param imageView 目标 ImageView
     */
    @JvmStatic
    fun loadDetailPoster(context: Context, movie: Movie?, imageView: ImageView) {
        if (movie == null) {
            loadPlaceholder(context, imageView)
            return
        }

        // 确保缓存已初始化
        getCache()

        // 详情页优先使用 localThumbPath（对应 thumb.jpg）
        val localPath = movie.localThumbPath
        if (!localPath.isNullOrEmpty() && File(localPath).exists()) {
            Glide.with(context)
                .load(File(localPath))
                .placeholder(R.drawable.bg_poster_placeholder)
                .error(R.drawable.bg_poster_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(imageView)
            return
        }

        // 其次使用 SMB thumbPath
        val thumbPath = movie.thumbPath
        val serverId = movie.serverId

        if (thumbPath.isNullOrEmpty()) {
            loadPlaceholder(context, imageView)
            return
        }

        // 从数据库获取服务器配置
        loadPoster(context, thumbPath, serverId, imageView)
    }

    /**
     * 加载电影海报（带服务器ID）
     * @param context 上下文
     * @param posterPath SMB海报路径
     * @param serverId 服务器ID
     * @param imageView 目标 ImageView
     */
    @JvmStatic
    fun loadPoster(context: Context, posterPath: String?, serverId: String?, imageView: ImageView) {
        if (posterPath.isNullOrEmpty()) {
            loadPlaceholder(context, imageView)
            return
        }

        // 确保缓存已初始化
        getCache()

        // 检查本地缓存
        val cachedPath = imageCache?.getCachedImagePath(posterPath)
        if (cachedPath != null) {
            // 本地有缓存，直接加载
            Glide.with(context)
                .load(File(cachedPath))
                .placeholder(R.drawable.bg_poster_placeholder)
                .error(R.drawable.bg_poster_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // 不使用 Glide 的磁盘缓存，使用我们自己的
                .into(imageView)
            return
        }

        // 本地无缓存，先显示占位图，然后异步下载
        loadPlaceholder(context, imageView)

        // 获取服务器配置并下载
        downloadAndLoad(context, posterPath, serverId, imageView)
    }

    /**
     * 加载占位图
     */
    private fun loadPlaceholder(context: Context, imageView: ImageView) {
        Glide.with(context)
            .load(R.drawable.bg_poster_placeholder)
            .into(imageView)
    }

    /**
     * 下载并加载图片
     */
    private fun downloadAndLoad(context: Context, posterPath: String, serverId: String?, imageView: ImageView) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 从数据库获取服务器配置
                val database = NASMovieApp.getInstance().database
                val dao = database.smbConfigDao()

                var config: SmbConfig? = null
                if (!serverId.isNullOrEmpty()) {
                    try {
                        config = dao.getById(serverId.toLong())
                    } catch (e: NumberFormatException) {
                        // serverId 不是数字，忽略
                    }
                }

                if (config == null) {
                    // 没有指定服务器，获取第一个
                    val allServers = dao.getAll()
                    if (allServers.isNotEmpty()) {
                        config = allServers[0]
                    }
                }

                if (config == null) {
                    Log.e(TAG, "No SMB config found for poster: $posterPath")
                    return@launch
                }

                // 异步下载图片
                imageCache?.downloadImageAsync(posterPath, config, object : SmbImageCache.DownloadCallback {
                    override fun onSuccess(localPath: String) {
                        // 下载成功，使用 post 确保在 UI 线程且 ImageView 未被回收时加载
                        imageView.post {
                            Glide.with(imageView.context)
                                .load(File(localPath))
                                .placeholder(R.drawable.bg_poster_placeholder)
                                .error(R.drawable.bg_poster_placeholder)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .into(imageView)
                        }
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Download failed for $posterPath: $error")
                    }
                })

            } catch (e: Exception) {
                Log.e(TAG, "Error in downloadAndLoad: ${e.message}", e)
            }
        }
    }

    /**
     * 预加载图片（用于列表滑动优化）
     */
    @JvmStatic
    fun preloadPoster(context: Context, posterPath: String?, serverId: String?) {
        if (posterPath == null || imageCache == null) return

        // 如果已缓存，跳过
        if (imageCache?.getCachedImagePath(posterPath) != null) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = NASMovieApp.getInstance().database
                val dao = database.smbConfigDao()

                var config: SmbConfig? = null
                if (!serverId.isNullOrEmpty()) {
                    try {
                        config = dao.getById(serverId.toLong())
                    } catch (e: NumberFormatException) {
                        // ignore
                    }
                }

                if (config == null) {
                    val allServers = dao.getAll()
                    if (allServers.isNotEmpty()) {
                        config = allServers[0]
                    }
                }

                if (config != null) {
                    // downloadImage 会在内部处理连接和下载
                    imageCache?.downloadImage(posterPath, config)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Preload error: ${e.message}")
            }
        }
    }
}