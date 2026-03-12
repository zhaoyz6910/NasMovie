package com.example.nasmovie.util;

import android.content.Context;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.smb.SmbImageCache;

import java.util.List;

/**
 * SMB 图片加载器
 * 封装 Glide 加载 SMB 图片的逻辑
 */
public class SmbImageLoader {

    private static final String TAG = "SmbImageLoader";
    private static SmbImageCache imageCache;

    /**
     * 初始化（在 Application 中调用）
     */
    public static void init(Context context) {
        if (imageCache == null) {
            imageCache = new SmbImageCache(context);
        }
    }

    /**
     * 获取缓存实例
     */
    public static SmbImageCache getCache() {
        if (imageCache == null) {
            imageCache = com.example.nasmovie.NASMovieApp.getInstance().getImageCache();
        }
        return imageCache;
    }

    /**
     * 加载电影海报（用于首页列表，优先使用 poster.jpg）
     * @param context 上下文
     * @param movie 电影对象
     * @param imageView 目标 ImageView
     */
    public static void loadPoster(Context context, Movie movie, ImageView imageView) {
        if (movie == null) {
            loadPlaceholder(context, imageView);
            return;
        }

        // 确保缓存已初始化
        if (imageCache == null) {
            imageCache = com.example.nasmovie.NASMovieApp.getInstance().getImageCache();
        }

        Log.d(TAG, "Loading poster for: " + movie.getTitle());
        Log.d(TAG, "  posterPath: " + movie.getPosterPath());
        Log.d(TAG, "  thumbPath: " + movie.getThumbPath());
        Log.d(TAG, "  localPosterPath: " + movie.getLocalPosterPath());
        Log.d(TAG, "  localThumbPath: " + movie.getLocalThumbPath());

        // 首页优先使用 localPosterPath（对应 poster.jpg）
        String localPath = movie.getLocalPosterPath();
        if (localPath != null && !localPath.isEmpty() && new java.io.File(localPath).exists()) {
            Log.d(TAG, "  Loading from localPosterPath: " + localPath);
            Glide.with(context)
                .load(new java.io.File(localPath))
                .placeholder(R.drawable.bg_poster_placeholder)
                .error(R.drawable.bg_poster_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(imageView);
            return;
        }

        // 其次使用 SMB posterPath
        String posterPath = movie.getPosterPath();
        String serverId = movie.getServerId();

        Log.d(TAG, "  Loading from SMB posterPath: " + posterPath);

        if (posterPath == null || posterPath.isEmpty()) {
            loadPlaceholder(context, imageView);
            return;
        }

        // 从数据库获取服务器配置
        loadPoster(context, posterPath, serverId, imageView);
    }

    /**
     * 加载电影详情海报（用于详情页，优先使用 thumb.jpg）
     * @param context 上下文
     * @param movie 电影对象
     * @param imageView 目标 ImageView
     */
    public static void loadDetailPoster(Context context, Movie movie, ImageView imageView) {
        if (movie == null) {
            loadPlaceholder(context, imageView);
            return;
        }

        // 确保缓存已初始化
        getCache();

        // 详情页优先使用 localThumbPath（对应 thumb.jpg）
        String localPath = movie.getLocalThumbPath();
        if (localPath != null && !localPath.isEmpty() && new java.io.File(localPath).exists()) {
            Glide.with(context)
                .load(new java.io.File(localPath))
                .placeholder(R.drawable.bg_poster_placeholder)
                .error(R.drawable.bg_poster_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(imageView);
            return;
        }

        // 其次使用 SMB thumbPath
        String thumbPath = movie.getThumbPath();
        String serverId = movie.getServerId();

        if (thumbPath == null || thumbPath.isEmpty()) {
            loadPlaceholder(context, imageView);
            return;
        }

        // 从数据库获取服务器配置
        loadPoster(context, thumbPath, serverId, imageView);
    }

    /**
     * 加载电影海报（带服务器ID）
     * @param context 上下文
     * @param posterPath SMB海报路径
     * @param serverId 服务器ID
     * @param imageView 目标 ImageView
     */
    public static void loadPoster(Context context, String posterPath, String serverId, ImageView imageView) {
        if (posterPath == null || posterPath.isEmpty()) {
            loadPlaceholder(context, imageView);
            return;
        }

        // 确保缓存已初始化
        getCache();

        // 检查本地缓存
        String cachedPath = imageCache.getCachedImagePath(posterPath);
        if (cachedPath != null) {
            // 本地有缓存，直接加载
            Glide.with(context)
                .load(new java.io.File(cachedPath))
                .placeholder(R.drawable.bg_poster_placeholder)
                .error(R.drawable.bg_poster_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.NONE) // 不使用 Glide 的磁盘缓存，使用我们自己的
                .into(imageView);
            return;
        }

        // 本地无缓存，先显示占位图，然后异步下载
        loadPlaceholder(context, imageView);

        // 获取服务器配置并下载
        downloadAndLoad(context, posterPath, serverId, imageView);
    }

    /**
     * 加载占位图
     */
    private static void loadPlaceholder(Context context, ImageView imageView) {
        Glide.with(context)
            .load(R.drawable.bg_poster_placeholder)
            .into(imageView);
    }

    private static java.util.concurrent.ExecutorService dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    /**
     * 下载并加载图片
     */
    private static void downloadAndLoad(Context context, String posterPath, String serverId, ImageView imageView) {
        // 在后台线程获取服务器配置，避免阻塞 UI 线程
        dbExecutor.execute(() -> {
            try {
                // 从数据库获取服务器配置
                com.example.nasmovie.data.db.AppDatabase database =
                    com.example.nasmovie.NASMovieApp.getInstance().getDatabase();
                com.example.nasmovie.data.db.SmbConfigDao dao = database.smbConfigDao();

                com.example.nasmovie.data.model.SmbConfig config = null;
                if (serverId != null && !serverId.isEmpty()) {
                    try {
                        config = dao.getById(Long.parseLong(serverId));
                    } catch (NumberFormatException e) {
                        // serverId 不是数字，忽略
                    }
                }

                if (config == null) {
                    // 没有指定服务器，获取第一个
                    java.util.List<com.example.nasmovie.data.model.SmbConfig> allServers = dao.getAll();
                    if (!allServers.isEmpty()) {
                        config = allServers.get(0);
                    }
                }

                if (config == null) {
                    android.util.Log.e("SmbImageLoader", "No SMB config found for poster: " + posterPath);
                    return;
                }

                // 异步下载图片
                final com.example.nasmovie.data.model.SmbConfig finalConfig = config;
                imageCache.downloadImageAsync(posterPath, config, new SmbImageCache.DownloadCallback() {
                    @Override
                    public void onSuccess(String localPath) {
                        // 下载成功，使用 post 确保在 UI 线程且 ImageView 未被回收时加载
                        imageView.post(() -> {
                            Glide.with(imageView.getContext())
                                .load(new java.io.File(localPath))
                                .placeholder(R.drawable.bg_poster_placeholder)
                                .error(R.drawable.bg_poster_placeholder)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .into(imageView);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        android.util.Log.e("SmbImageLoader", "Download failed for " + posterPath + ": " + error);
                    }
                });

            } catch (Exception e) {
                android.util.Log.e("SmbImageLoader", "Error in downloadAndLoad: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 预加载图片（用于列表滑动优化）
     */
    public static void preloadPoster(Context context, String posterPath, String serverId) {
        if (posterPath == null || imageCache == null) return;

        // 如果已缓存，跳过
        if (imageCache.getCachedImagePath(posterPath) != null) {
            return;
        }

        // 使用背景执行器查询数据库
        dbExecutor.execute(() -> {
            try {
                com.example.nasmovie.data.db.AppDatabase database =
                    com.example.nasmovie.NASMovieApp.getInstance().getDatabase();
                com.example.nasmovie.data.db.SmbConfigDao dao = database.smbConfigDao();

                com.example.nasmovie.data.model.SmbConfig config = null;
                if (serverId != null && !serverId.isEmpty()) {
                    try {
                        config = dao.getById(Long.parseLong(serverId));
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }

                if (config == null) {
                    java.util.List<com.example.nasmovie.data.model.SmbConfig> allServers = dao.getAll();
                    if (!allServers.isEmpty()) {
                        config = allServers.get(0);
                    }
                }

                if (config != null) {
                    // downloadImage 会在内部处理连接和下载
                    imageCache.downloadImage(posterPath, config);
                }
            } catch (Exception e) {
                android.util.Log.e("SmbImageLoader", "Preload error: " + e.getMessage());
            }
        });
    }
}
