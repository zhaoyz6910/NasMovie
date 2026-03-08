package com.example.nasmovie.data.repository;

import android.content.Context;
import android.util.Log;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.data.db.AppDatabase;
import com.example.nasmovie.data.db.FavoriteDao;
import com.example.nasmovie.data.db.MovieDao;
import com.example.nasmovie.data.db.WatchProgressDao;
import com.example.nasmovie.data.model.Favorite;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.NfoMetadata;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.data.parser.NfoParser;
import com.example.nasmovie.data.smb.SmbClient;
import com.example.nasmovie.data.smb.SmbFileInfo;
import com.example.nasmovie.util.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 电影数据仓库
 * 协调SMB和本地数据库操作
 */
public class MovieRepository {

    private static final String TAG = "MovieRepository";

    private final AppDatabase database;
    private final MovieDao movieDao;
    private final WatchProgressDao watchProgressDao;
    private final FavoriteDao favoriteDao;
    private final ExecutorService executor;

    public MovieRepository(Context context) {
        database = NASMovieApp.getInstance().getDatabase();
        movieDao = database.movieDao();
        watchProgressDao = database.watchProgressDao();
        favoriteDao = database.favoriteDao();
        executor = Executors.newFixedThreadPool(4);
    }

    // ==================== 电影操作 ====================

    /**
     * 获取所有电影
     */
    public List<Movie> getAllMovies() {
        return movieDao.getAll();
    }

    /**
     * 根据ID获取电影
     */
    public Movie getMovieById(String id) {
        return movieDao.getById(id);
    }

    /**
     * 搜索电影
     */
    public List<Movie> searchMovies(String keyword) {
        return movieDao.search(keyword);
    }

    /**
     * 保存电影
     */
    public void saveMovie(Movie movie) {
        movieDao.insert(movie);
    }

    /**
     * 保存电影列表
     */
    public void saveMovies(List<Movie> movies) {
        movieDao.insertAll(movies);
    }

    /**
     * 删除电影
     */
    public void deleteMovie(String id) {
        movieDao.deleteById(id);
    }

    /**
     * 删除指定服务器的所有电影
     */
    public void deleteMoviesByServer(String serverId) {
        movieDao.deleteByServerId(serverId);
    }

    /**
     * 获取电影数量
     */
    public int getMovieCount() {
        return movieDao.getCount();
    }

    // ==================== 观看进度操作 ====================

    /**
     * 获取观看进度
     */
    public WatchProgress getWatchProgress(String movieId) {
        return watchProgressDao.getByMovieId(movieId);
    }

    /**
     * 保存观看进度
     */
    public void saveWatchProgress(String movieId, long position, long duration) {
        WatchProgress progress = new WatchProgress(movieId, position, duration);
        watchProgressDao.insert(progress);
    }

    /**
     * 删除观看进度
     */
    public void deleteWatchProgress(String movieId) {
        watchProgressDao.deleteByMovieId(movieId);
    }

    /**
     * 获取最近观看记录
     */
    public List<WatchProgress> getRecentWatchProgress(int limit) {
        return watchProgressDao.getRecent(limit);
    }

    // ==================== 收藏操作 ====================

    /**
     * 判断是否已收藏
     */
    public boolean isFavorite(String movieId) {
        return favoriteDao.isFavorite(movieId);
    }

    /**
     * 添加收藏
     */
    public void addFavorite(String movieId) {
        Favorite favorite = new Favorite(movieId);
        favoriteDao.insert(favorite);
    }

    /**
     * 取消收藏
     */
    public void removeFavorite(String movieId) {
        favoriteDao.deleteByMovieId(movieId);
    }

    /**
     * 获取所有收藏的电影ID
     */
    public List<String> getAllFavoriteIds() {
        return favoriteDao.getAllMovieIds();
    }

    /**
     * 获取收藏列表
     */
    public List<Movie> getFavoriteMovies() {
        List<String> ids = favoriteDao.getAllMovieIds();
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        return movieDao.getByIds(ids);
    }

    // ==================== 扫描操作 ====================

    /**
     * 扫描媒体库回调接口
     */
    public interface ScanCallback {
        void onProgress(int current, int total, String currentPath);
        void onComplete(List<Movie> movies);
        void onError(String error);
    }

    /**
     * 扫描NAS媒体库
     */
    public void scanLibrary(SmbConfig config, ScanCallback callback) {
        executor.execute(() -> {
            SmbClient smbClient = new SmbClient();
            List<Movie> movies = new ArrayList<>();

            try {
                // 连接服务器
                if (!smbClient.connect(config)) {
                    callback.onError("无法连接到服务器");
                    return;
                }

                // 获取电影目录路径
                String moviePath = config.getMoviePath() != null ? config.getMoviePath() : "";
                List<SmbFileInfo> folders = smbClient.listFiles(moviePath);

                int total = folders.size();
                int current = 0;

                for (SmbFileInfo folder : folders) {
                    if (!folder.isDirectory()) continue;

                    current++;
                    String folderPath = folder.getPath();
                    callback.onProgress(current, total, folder.getName());

                    // 扫描文件夹内容
                    Movie movie = scanMovieFolder(smbClient, folderPath, config);
                    if (movie != null) {
                        movies.add(movie);
                    }
                }

                // 保存到数据库
                if (!movies.isEmpty()) {
                    // 先删除该服务器的旧数据
                    deleteMoviesByServer(String.valueOf(config.getId()));
                    saveMovies(movies);
                }

                callback.onComplete(movies);

            } catch (Exception e) {
                Log.e(TAG, "Scan error: " + e.getMessage(), e);
                callback.onError("扫描失败: " + e.getMessage());
            } finally {
                smbClient.disconnect();
            }
        });
    }

    /**
     * 扫描电影文件夹
     */
    private Movie scanMovieFolder(SmbClient smbClient, String folderPath, SmbConfig config) {
        try {
            List<SmbFileInfo> files = smbClient.listFiles(folderPath);

            String videoPath = null;
            String nfoPath = null;
            String posterPath = null;  // 用于首页：优先 poster.jpg/folder.jpg
            String thumbPath = null;   // 用于详情页：优先 thumb.jpg
            List<String> subtitlePaths = new ArrayList<>();
            long videoSize = 0;

            // 遍历文件 - 按优先级收集海报文件
            String posterPathPriority1 = null; // poster.jpg/poster.png
            String posterPathPriority2 = null; // folder.jpg/folder.png
            String posterPathPriority3 = null; // cover.jpg/cover.png
            String posterPathPriority4 = null; // fanart.jpg/fanart.png
            String posterPathPriority5 = null; // backdrop.jpg/backdrop.png

            for (SmbFileInfo file : files) {
                if (file.isDirectory()) continue;

                String fileName = file.getName();
                String lowerName = fileName.toLowerCase();

                if (file.isVideoFile()) {
                    if (videoPath == null) {
                        videoPath = file.getPath();
                        videoSize = file.getFileSize();
                    }
                } else if (file.isNfoFile()) {
                    nfoPath = file.getPath();
                } else if (file.isThumbPoster()) {
                    thumbPath = file.getPath();
                } else if (lowerName.equals("poster.jpg") || lowerName.equals("poster.png")) {
                    posterPathPriority1 = file.getPath();
                } else if (lowerName.equals("folder.jpg") || lowerName.equals("folder.png")) {
                    posterPathPriority2 = file.getPath();
                } else if (lowerName.equals("cover.jpg") || lowerName.equals("cover.png")) {
                    posterPathPriority3 = file.getPath();
                } else if (lowerName.equals("fanart.jpg") || lowerName.equals("fanart.png")) {
                    posterPathPriority4 = file.getPath();
                } else if (lowerName.equals("backdrop.jpg") || lowerName.equals("backdrop.png")) {
                    posterPathPriority5 = file.getPath();
                } else if (file.isSubtitleFile()) {
                    subtitlePaths.add(file.getPath());
                }
            }

            // 按优先级选择海报
            if (posterPathPriority1 != null) {
                posterPath = posterPathPriority1;
            } else if (posterPathPriority2 != null) {
                posterPath = posterPathPriority2;
            } else if (posterPathPriority3 != null) {
                posterPath = posterPathPriority3;
            } else if (posterPathPriority4 != null) {
                posterPath = posterPathPriority4;
            } else if (posterPathPriority5 != null) {
                posterPath = posterPathPriority5;
            }

            // 如果没有视频文件，跳过
            if (videoPath == null) {
                return null;
            }

            // 创建电影实体
            Movie movie = new Movie();
            movie.setId(FileUtils.generateMovieId(videoPath));
            movie.setVideoPath(videoPath);
            movie.setFileSize(videoSize);
            movie.setFolderPath(folderPath);
            movie.setServerId(String.valueOf(config.getId()));
            movie.setNfoPath(nfoPath);
            // 首页优先 poster.jpg，没有则使用 thumb.jpg
            movie.setPosterPath(posterPath != null ? posterPath : thumbPath);
            // 详情页优先 thumb.jpg，没有则使用 poster.jpg
            movie.setThumbPath(thumbPath != null ? thumbPath : posterPath);
            movie.setSubtitlePathList(subtitlePaths);

            // 从文件夹名获取标题
            String folderName = folderPath.contains("\\")
                ? folderPath.substring(folderPath.lastIndexOf("\\") + 1)
                : folderPath.substring(folderPath.lastIndexOf("/") + 1);
            movie.setTitle(folderName);

            // 解析NFO文件
            if (nfoPath != null) {
                parseNfo(smbClient, nfoPath, movie);
            }

            // 下载并缓存海报到本地
            if (movie.getPosterPath() != null) {
                String localPosterPath = downloadAndCachePoster(smbClient, movie.getPosterPath(), config);
                movie.setLocalPosterPath(localPosterPath);
            }
            if (movie.getThumbPath() != null) {
                String localThumbPath = downloadAndCachePoster(smbClient, movie.getThumbPath(), config);
                movie.setLocalThumbPath(localThumbPath);
            }

            return movie;

        } catch (Exception e) {
            Log.e(TAG, "Error scanning folder: " + folderPath, e);
            return null;
        }
    }

    /**
     * 下载并缓存海报到本地
     */
    private String downloadAndCachePoster(SmbClient smbClient, String posterPath, SmbConfig config) {
        try {
            // 使用 SmbImageCache 下载并缓存
            com.example.nasmovie.data.smb.SmbImageCache imageCache =
                com.example.nasmovie.NASMovieApp.getInstance().getImageCache();

            if (imageCache != null) {
                return imageCache.downloadImage(posterPath, config);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error caching poster: " + posterPath, e);
        }
        return null;
    }

    /**
     * 解析NFO文件
     */
    private void parseNfo(SmbClient smbClient, String nfoPath, Movie movie) {
        try {
            byte[] data = smbClient.readFileBytes(nfoPath);
            if (data != null) {
                NfoMetadata metadata = NfoParser.parse(data);
                if (metadata != null) {
                    if (metadata.getTitle() != null) {
                        movie.setTitle(metadata.getTitle());
                    }
                    movie.setOriginalTitle(metadata.getOriginalTitle());
                    movie.setPlot(metadata.getPlot());
                    movie.setDirector(metadata.getDirector());
                    movie.setYear(metadata.getYear());
                    movie.setRating(metadata.getRating());
                    movie.setDuration(metadata.getRuntime());
                    movie.setActorList(metadata.getActors());
                    movie.setGenreList(metadata.getGenres());

                    // 如果 NFO 中有 thumb URL，保存到 thumbPath（如果本地没有 thumb.jpg）
                    if (metadata.getThumb() != null && movie.getThumbPath() == null) {
                        // 这里可以处理 NFO 中的 thumb URL
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing NFO: " + nfoPath, e);
        }
    }

    /**
     * 关闭资源
     */
    public void close() {
        executor.shutdown();
    }
}