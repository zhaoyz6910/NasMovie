package com.example.nasmovie.service;

import android.content.Context;
import android.util.Log;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.data.db.AppDatabase;
import com.example.nasmovie.data.db.SmbConfigDao;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.NfoMetadata;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.parser.NfoParser;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.data.smb.SmbClient;
import com.example.nasmovie.data.smb.SmbFileInfo;
import com.example.nasmovie.data.smb.SmbImageCache;
import com.example.nasmovie.util.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 扫描服务
 * 负责扫描NAS上的电影资源
 */
public class ScanService {

    private static final String TAG = "ScanService";

    private final Context context;
    private final AppDatabase database;
    private final SmbConfigDao smbConfigDao;
    private final MovieRepository repository;
    private final ExecutorService executor;

    private boolean isScanning = false;
    private SmbClient currentClient;

    public ScanService(Context context) {
        this.context = context;
        this.database = NASMovieApp.getInstance().getDatabase();
        this.smbConfigDao = database.smbConfigDao();
        this.repository = new MovieRepository(context);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 是否正在扫描
     */
    public boolean isScanning() {
        return isScanning;
    }

    /**
     * 停止扫描
     */
    public void stopScan() {
        if (isScanning && currentClient != null) {
            currentClient.disconnect();
        }
    }

    /**
     * 扫描指定服务器的媒体库
     */
    public void scanServer(long serverId, ScanCallback callback) {
        if (isScanning) {
            callback.onError("正在扫描中，请稍候");
            return;
        }

        SmbConfig config = smbConfigDao.getById(serverId);
        if (config == null) {
            callback.onError("服务器配置不存在");
            return;
        }

        scanLibrary(config, callback);
    }

    /**
     * 扫描所有服务器的媒体库
     */
    public void scanAllServers(ScanCallback callback) {
        List<SmbConfig> configs = smbConfigDao.getAll();
        if (configs == null || configs.isEmpty()) {
            callback.onError("未配置服务器");
            return;
        }

        // 扫描所有服务器
        for (SmbConfig config : configs) {
            scanLibrary(config, callback);
        }
    }

    /**
     * 扫描媒体库
     */
    public void scanLibrary(SmbConfig config, ScanCallback callback) {
        isScanning = true;

        executor.execute(() -> {
            currentClient = new SmbClient();
            List<Movie> movies = new ArrayList<>();
            int scannedCount = 0;
            int addedCount = 0;

            try {
                // 通知开始
                callback.onStart();

                Log.i(TAG, "开始连接服务器: " + config.getHost());

                // 连接服务器
                if (!currentClient.connect(config)) {
                    Log.e(TAG, "连接服务器失败");
                    callback.onError("无法连接到服务器");
                    isScanning = false;
                    return;
                }

                Log.i(TAG, "连接服务器成功");

                // 获取电影目录
                String moviePath = config.getMoviePath() != null ? config.getMoviePath() : "";
                Log.i(TAG, "扫描路径: " + moviePath);

                List<SmbFileInfo> items = currentClient.listFiles(moviePath);
                Log.i(TAG, "找到文件/文件夹数量: " + items.size());

                // 过滤出目录
                List<SmbFileInfo> folders = new ArrayList<>();
                for (SmbFileInfo item : items) {
                    Log.d(TAG, "发现: " + item.getName() + " (目录: " + item.isDirectory() + ")");
                    if (item.isDirectory()) {
                        folders.add(item);
                    }
                }

                Log.i(TAG, "文件夹数量: " + folders.size());

                int total = folders.size();

                // 如果没有子文件夹，尝试扫描根目录下的视频文件
                if (folders.isEmpty()) {
                    Log.i(TAG, "没有子文件夹，检查根目录下的视频文件");
                    List<Movie> rootMovies = scanMovieFolder(moviePath);
                    if (!rootMovies.isEmpty()) {
                        movies.addAll(rootMovies);
                        addedCount += rootMovies.size();
                    }
                }

                // 扫描每个目录
                for (SmbFileInfo folder : folders) {
                    if (!isScanning) {
                        callback.onError("扫描已取消");
                        break;
                    }

                    scannedCount++;
                    callback.onProgress(scannedCount, total, folder.getName());

                    List<Movie> folderMovies = scanMovieFolder(folder.getPath());
                    if (!folderMovies.isEmpty()) {
                        movies.addAll(folderMovies);
                        addedCount += folderMovies.size();
                        for (Movie movie : folderMovies) {
                            Log.i(TAG, "添加电影: " + movie.getTitle());
                        }
                    }
                }

                // 保存到数据库
                if (!movies.isEmpty()) {
                    // 删除旧数据
                    repository.deleteMoviesByServer(String.valueOf(config.getId()));
                    // 保存新数据
                    repository.saveMovies(movies);
                }

                Log.i(TAG, "扫描完成，添加电影数: " + addedCount);

                // 完成
                callback.onComplete(addedCount, total);

            } catch (Exception e) {
                Log.e(TAG, "Scan error", e);
                callback.onError("扫描失败: " + e.getMessage());
            } finally {
                if (currentClient != null) {
                    currentClient.disconnect();
                }
                isScanning = false;
            }
        });
    }

    /**
     * 扫描电影文件夹（支持嵌套扫描）
     * 扫描到影片为止：如果当前目录包含视频文件，则停止继续深入扫描子目录
     * 返回该目录及其子目录中找到的所有电影
     */
    private List<Movie> scanMovieFolder(String folderPath) {
        List<Movie> movies = new ArrayList<>();
        scanMovieFolderRecursive(folderPath, 0, movies);
        return movies;
    }

    /**
     * 递归扫描电影文件夹
     * @param folderPath 文件夹路径
     * @param depth 当前深度（用于防止无限递归）
     * @param movies 收集电影的列表
     * @return 该目录是否包含视频文件（用于决定是否停止深入）
     */
    private boolean scanMovieFolderRecursive(String folderPath, int depth, List<Movie> movies) {
        // 防止无限递归，限制最大深度为 5 层
        if (depth > 5) {
            Log.w(TAG, "达到最大扫描深度，停止扫描: " + folderPath);
            return false;
        }

        try {
            Log.d(TAG, "扫描文件夹: " + folderPath + " (深度: " + depth + ")");
            List<SmbFileInfo> files = currentClient.listFiles(folderPath);
            Log.d(TAG, "文件夹内文件数量: " + files.size());

            String videoPath = null;
            String videoName = null;
            String nfoPath = null;
            String posterPath = null;  // 用于首页：优先 poster.jpg/folder.jpg
            String thumbPath = null;   // 用于详情页：优先 thumb.jpg
            List<String> subtitlePaths = new ArrayList<>();
            long videoSize = 0;

            // 用于收集子目录
            List<SmbFileInfo> subDirs = new ArrayList<>();

            // 按优先级收集海报文件
            String posterPathPriority1 = null; // poster.jpg/poster.png
            String posterPathPriority2 = null; // folder.jpg/folder.png
            String posterPathPriority3 = null; // cover.jpg/cover.png
            String posterPathPriority4 = null; // fanart.jpg/fanart.png
            String posterPathPriority5 = null; // backdrop.jpg/backdrop.png
            String otherImagePath = null; // 其他图片

            // 第一步：遍历文件，收集视频、NFO、海报、字幕，同时收集子目录
            for (SmbFileInfo file : files) {
                String fileName = file.getName();
                String lowerName = fileName.toLowerCase();
                String ext = file.getExtension();
                Log.v(TAG, "文件: " + fileName + " 扩展名: " + ext + " 是目录: " + file.isDirectory());

                if (file.isDirectory()) {
                    // 收集子目录，稍后处理
                    subDirs.add(file);
                    continue;
                }

                if (file.isVideoFile()) {
                    Log.i(TAG, "发现视频文件: " + fileName);
                    if (videoPath == null) {
                        videoPath = file.getPath();
                        videoName = FileUtils.getNameWithoutExtension(fileName);
                        videoSize = file.getFileSize();
                    }
                } else if (file.isNfoFile()) {
                    nfoPath = file.getPath();
                } else if (file.isThumbPoster()) {
                    // thumb.jpg 优先级最高，用于详情页
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
                } else if (file.isImageFile()) {
                    // 其他图片，如果没有 poster 则使用
                    if (otherImagePath == null) {
                        otherImagePath = file.getPath();
                    }
                } else if (file.isSubtitleFile()) {
                    // 匹配与视频同名的字幕
                    String subName = FileUtils.getNameWithoutExtension(fileName);
                    if (videoName != null && videoName.equals(subName)) {
                        subtitlePaths.add(0, file.getPath()); // 同名字幕放前面
                    } else {
                        subtitlePaths.add(file.getPath());
                    }
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
            } else if (otherImagePath != null) {
                posterPath = otherImagePath;
            }

            // 第二步：如果当前目录有视频文件，创建电影并添加到列表
            boolean hasVideoInCurrentDir = false;
            if (videoPath != null) {
                hasVideoInCurrentDir = true;
                Log.i(TAG, "文件夹 " + folderPath + " 中找到视频，停止深入扫描该分支的子目录");
                Movie movie = createMovie(folderPath, videoPath, videoName, videoSize, nfoPath, posterPath, thumbPath, subtitlePaths);
                if (movie != null) {
                    movies.add(movie);
                    Log.i(TAG, "添加电影: " + movie.getTitle());
                }
            }

            // 第三步：如果当前目录没有视频文件，递归扫描子目录
            if (!hasVideoInCurrentDir && !subDirs.isEmpty()) {
                Log.d(TAG, "文件夹 " + folderPath + " 中没有视频，扫描 " + subDirs.size() + " 个子目录");

                for (SmbFileInfo subDir : subDirs) {
                    if (!isScanning) {
                        Log.d(TAG, "扫描已取消，停止递归");
                        return false;
                    }
                    Log.d(TAG, "进入子目录: " + subDir.getName());
                    // 递归扫描子目录，继续收集电影
                    scanMovieFolderRecursive(subDir.getPath(), depth + 1, movies);
                }
            }

            return hasVideoInCurrentDir;

        } catch (Exception e) {
            Log.e(TAG, "Error scanning folder: " + folderPath, e);
            return false;
        }
    }

    /**
     * 创建电影实体
     */
    private Movie createMovie(String folderPath, String videoPath, String videoName,
                              long videoSize, String nfoPath, String posterPath, String thumbPath,
                              List<String> subtitlePaths) {
        // 创建电影实体
        Movie movie = new Movie();
        movie.setId(FileUtils.generateMovieId(videoPath));
        movie.setVideoPath(videoPath);
        movie.setFileSize(videoSize);
        movie.setFolderPath(folderPath);
        movie.setServerId(String.valueOf(currentClient.getConfig().getId()));
        movie.setNfoPath(nfoPath);
        // 首页优先 poster.jpg，没有则使用 thumb.jpg
        movie.setPosterPath(posterPath != null ? posterPath : thumbPath);
        // 详情页优先 thumb.jpg，没有则使用 poster.jpg
        movie.setThumbPath(thumbPath != null ? thumbPath : posterPath);
        movie.setSubtitlePathList(subtitlePaths);

        // 从文件夹名获取标题
        String folderName = getFolderName(folderPath);
        movie.setTitle(folderName);

        // 解析NFO
        if (nfoPath != null) {
            parseNfo(movie, nfoPath);
        }

        // 下载并缓存海报到本地
        downloadAndCachePosters(movie, currentClient.getConfig());

        return movie;
    }

    /**
     * 下载并缓存海报图片
     */
    private void downloadAndCachePosters(Movie movie, SmbConfig config) {
        SmbImageCache imageCache = NASMovieApp.getInstance().getImageCache();
        if (imageCache == null) {
            Log.w(TAG, "SmbImageCache is null, skipping poster caching");
            return;
        }

        // 下载 poster.jpg（用于首页）
        String posterPath = movie.getPosterPath();
        if (posterPath != null && !posterPath.isEmpty()) {
            try {
                String localPosterPath = imageCache.downloadImage(posterPath, config);
                if (localPosterPath != null) {
                    movie.setLocalPosterPath(localPosterPath);
                    Log.d(TAG, "Cached poster: " + posterPath + " -> " + localPosterPath);
                } else {
                    Log.w(TAG, "Failed to cache poster: " + posterPath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error caching poster: " + posterPath, e);
            }
        }

        // 下载 thumb.jpg（用于详情页）
        String thumbPath = movie.getThumbPath();
        if (thumbPath != null && !thumbPath.isEmpty() && !thumbPath.equals(posterPath)) {
            try {
                String localThumbPath = imageCache.downloadImage(thumbPath, config);
                if (localThumbPath != null) {
                    movie.setLocalThumbPath(localThumbPath);
                    Log.d(TAG, "Cached thumb: " + thumbPath + " -> " + localThumbPath);
                } else {
                    Log.w(TAG, "Failed to cache thumb: " + thumbPath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error caching thumb: " + thumbPath, e);
            }
        } else if (thumbPath != null && thumbPath.equals(posterPath)) {
            // thumb 和 poster 是同一张图片，复用本地路径
            movie.setLocalThumbPath(movie.getLocalPosterPath());
        }
    }

    /**
     * 解析NFO文件
     */
    private void parseNfo(Movie movie, String nfoPath) {
        try {
            byte[] data = currentClient.readFileBytes(nfoPath);
            if (data == null) return;

            NfoMetadata metadata = NfoParser.parse(data);
            if (metadata == null) return;

            // 填充元数据
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

        } catch (Exception e) {
            Log.e(TAG, "Error parsing NFO: " + nfoPath, e);
        }
    }

    /**
     * 获取文件夹名称
     */
    private String getFolderName(String path) {
        if (path == null) return "";
        String normalizedPath = path.replace("/", "\\");
        int lastSep = normalizedPath.lastIndexOf("\\");
        return lastSep >= 0 ? normalizedPath.substring(lastSep + 1) : path;
    }

    /**
     * 扫描回调接口
     */
    public interface ScanCallback {
        void onStart();
        void onProgress(int current, int total, String currentPath);
        void onComplete(int addedCount, int totalCount);
        void onError(String error);
    }
}