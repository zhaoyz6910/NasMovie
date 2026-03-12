package com.example.nasmovie.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
    private final Handler mainHandler;

    private boolean isScanning = false;
    private boolean isCancelRequested = false;
    private SmbClient currentClient;

    public ScanService(Context context) {
        this.context = context;
        this.database = NASMovieApp.getInstance().getDatabase();
        this.smbConfigDao = database.smbConfigDao();
        this.repository = new MovieRepository(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public boolean isScanning() {
        return isScanning;
    }

    public void stopScan() {
        isCancelRequested = true;
        // 在后台线程断开连接，避免 NetworkOnMainThreadException
        executor.execute(() -> {
            if (currentClient != null) {
                try {
                    currentClient.disconnect();
                } catch (Exception e) {
                    Log.e(TAG, "Error during scan stop disconnect", e);
                }
            }
        });
    }

    /**
     * 扫描指定服务器
     */
    public void scanServer(long serverId, ScanCallback callback) {
        if (isScanning) {
            safeCallback(() -> callback.onError("正在扫描中，请稍候"));
            return;
        }

        executor.execute(() -> {
            isScanning = true;
            isCancelRequested = false;
            try {
                SmbConfig config = smbConfigDao.getById(serverId);
                if (config == null) {
                    safeCallback(() -> callback.onError("服务器配置不存在"));
                    return;
                }
                
                safeCallback(callback::onStart);
                int added = performScanInternal(config, callback);
                
                if (isCancelRequested) {
                    safeCallback(() -> callback.onError("扫描已取消"));
                } else {
                    safeCallback(() -> callback.onComplete(added, 1));
                }
            } catch (Exception e) {
                Log.e(TAG, "Scan error", e);
                safeCallback(() -> callback.onError("扫描失败: " + e.getMessage()));
            } finally {
                isScanning = false;
            }
        });
    }

    /**
     * 扫描所有服务器
     */
    public void scanAllServers(ScanCallback callback) {
        if (isScanning) {
            safeCallback(() -> callback.onError("正在扫描中，请稍候"));
            return;
        }

        executor.execute(() -> {
            isScanning = true;
            isCancelRequested = false;
            int totalAdded = 0;
            try {
                List<SmbConfig> configs = smbConfigDao.getAll();
                if (configs == null || configs.isEmpty()) {
                    safeCallback(() -> callback.onError("未配置服务器"));
                    return;
                }

                safeCallback(callback::onStart);

                for (SmbConfig config : configs) {
                    if (isCancelRequested) break;
                    
                    // 执行单个服务器扫描，但不重置 isScanning
                    totalAdded += performScanInternal(config, callback);
                }

                final int finalTotalAdded = totalAdded;
                if (isCancelRequested) {
                    safeCallback(() -> callback.onError("扫描已取消"));
                } else {
                    safeCallback(() -> callback.onComplete(finalTotalAdded, configs.size()));
                }
            } catch (Exception e) {
                Log.e(TAG, "All scan error", e);
                safeCallback(() -> callback.onError("批量扫描失败: " + e.getMessage()));
            } finally {
                isScanning = false;
            }
        });
    }

    /**
     * 内部扫描逻辑（运行在后台线程，不处理 isScanning 状态位）
     * 返回该服务器添加的电影数量
     */
    private int performScanInternal(SmbConfig config, ScanCallback callback) {
        currentClient = new SmbClient();
        List<Movie> movies = new ArrayList<>();
        int scannedCount = 0;
        int addedCount = 0;

        try {
            safeCallback(() -> callback.onServerStart(config));
            Log.i(TAG, "开始连接服务器: " + config.getHost());

            if (!currentClient.connect(config)) {
                safeCallback(() -> callback.onError("无法连接服务器: " + config.getName()));
                return 0;
            }

            String moviePath = config.getMoviePath() != null ? config.getMoviePath() : "";
            List<SmbFileInfo> items = currentClient.listFiles(moviePath);
            
            List<SmbFileInfo> folders = new ArrayList<>();
            for (SmbFileInfo item : items) {
                if (item.isDirectory()) folders.add(item);
            }

            int total = folders.size();
            
            // 根目录扫描
            if (folders.isEmpty()) {
                List<Movie> rootMovies = scanMovieFolder(moviePath);
                movies.addAll(rootMovies);
                addedCount += rootMovies.size();
            }

            // 子目录扫描
            for (SmbFileInfo folder : folders) {
                if (isCancelRequested) break;

                scannedCount++;
                final int progress = scannedCount;
                final String folderName = folder.getName();
                final String serverName = config.getName();
                safeCallback(() -> callback.onProgress(progress, total, "[" + serverName + "] " + folderName));

                List<Movie> folderMovies = scanMovieFolder(folder.getPath());
                movies.addAll(folderMovies);
                addedCount += folderMovies.size();
            }

            // 保存数据库
            if (!movies.isEmpty() && !isCancelRequested) {
                repository.deleteMoviesByServer(String.valueOf(config.getId()));
                repository.saveMovies(movies);
            }

            return addedCount;

        } catch (Exception e) {
            Log.e(TAG, "Scan internal error: " + config.getName(), e);
            return 0;
        } finally {
            if (currentClient != null) {
                currentClient.disconnect();
                currentClient = null;
            }
        }
    }

    private void safeCallback(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private List<Movie> scanMovieFolder(String folderPath) {
        List<Movie> movies = new ArrayList<>();
        scanMovieFolderRecursive(folderPath, 0, movies);
        return movies;
    }

    private boolean scanMovieFolderRecursive(String folderPath, int depth, List<Movie> movies) {
        if (depth > 5 || isCancelRequested) return false;

        try {
            List<SmbFileInfo> files = currentClient.listFiles(folderPath);
            String videoPath = null;
            String videoName = null;
            String nfoPath = null;
            String posterPath = null;
            String thumbPath = null;
            List<String> subtitlePaths = new ArrayList<>();
            long videoSize = 0;

            List<SmbFileInfo> subDirs = new ArrayList<>();
            String p1 = null, p2 = null, p3 = null, p4 = null, p5 = null, other = null;

            for (SmbFileInfo file : files) {
                String lowerName = file.getName().toLowerCase();
                if (file.isDirectory()) {
                    subDirs.add(file);
                    continue;
                }

                if (file.isVideoFile()) {
                    if (videoPath == null) {
                        videoPath = file.getPath();
                        videoName = FileUtils.getNameWithoutExtension(file.getName());
                        videoSize = file.getFileSize();
                    }
                } else if (file.isNfoFile()) {
                    nfoPath = file.getPath();
                } else if (file.isThumbPoster()) {
                    thumbPath = file.getPath();
                } else if (lowerName.equals("poster.jpg") || lowerName.equals("poster.png")) {
                    p1 = file.getPath();
                } else if (lowerName.equals("folder.jpg") || lowerName.equals("folder.png")) {
                    p2 = file.getPath();
                } else if (lowerName.equals("cover.jpg") || lowerName.equals("cover.png")) {
                    p3 = file.getPath();
                } else if (lowerName.equals("fanart.jpg") || lowerName.equals("fanart.png")) {
                    p4 = file.getPath();
                } else if (lowerName.equals("backdrop.jpg") || lowerName.equals("backdrop.png")) {
                    p5 = file.getPath();
                } else if (file.isImageFile()) {
                    if (other == null) other = file.getPath();
                } else if (file.isSubtitleFile()) {
                    String subName = FileUtils.getNameWithoutExtension(file.getName());
                    if (videoName != null && videoName.equals(subName)) {
                        subtitlePaths.add(0, file.getPath());
                    } else {
                        subtitlePaths.add(file.getPath());
                    }
                }
            }

            posterPath = (p1 != null) ? p1 : (p2 != null) ? p2 : (p3 != null) ? p3 : (p4 != null) ? p4 : (p5 != null) ? p5 : other;

            boolean hasVideo = false;
            if (videoPath != null) {
                hasVideo = true;
                Movie movie = createMovie(folderPath, videoPath, videoName, videoSize, nfoPath, posterPath, thumbPath, subtitlePaths);
                if (movie != null) movies.add(movie);
            }

            if (!hasVideo && !subDirs.isEmpty()) {
                for (SmbFileInfo subDir : subDirs) {
                    if (isCancelRequested) break;
                    scanMovieFolderRecursive(subDir.getPath(), depth + 1, movies);
                }
            }
            return hasVideo;
        } catch (Exception e) {
            Log.e(TAG, "Error recursive scanning: " + folderPath, e);
            return false;
        }
    }

    private Movie createMovie(String folderPath, String videoPath, String videoName,
                              long videoSize, String nfoPath, String posterPath, String thumbPath,
                              List<String> subtitlePaths) {
        Movie movie = new Movie();
        movie.setId(FileUtils.generateMovieId(videoPath));
        movie.setVideoPath(videoPath);
        movie.setFileSize(videoSize);
        movie.setFolderPath(folderPath);
        movie.setServerId(String.valueOf(currentClient.getConfig().getId()));
        movie.setNfoPath(nfoPath);
        movie.setPosterPath(posterPath != null ? posterPath : thumbPath);
        movie.setThumbPath(thumbPath != null ? thumbPath : posterPath);
        movie.setSubtitlePathList(subtitlePaths);
        movie.setTitle(getFolderName(folderPath));

        if (nfoPath != null) parseNfo(movie, nfoPath);
        downloadAndCachePosters(movie, currentClient.getConfig());
        return movie;
    }

    private void downloadAndCachePosters(Movie movie, SmbConfig config) {
        SmbImageCache imageCache = NASMovieApp.getInstance().getImageCache();
        if (imageCache == null) return;

        String poster = movie.getPosterPath();
        if (poster != null && !poster.isEmpty()) {
            String local = imageCache.downloadImage(poster, config);
            if (local != null) movie.setLocalPosterPath(local);
        }

        String thumb = movie.getThumbPath();
        if (thumb != null && !thumb.isEmpty()) {
            if (thumb.equals(poster)) {
                movie.setLocalThumbPath(movie.getLocalPosterPath());
            } else {
                String local = imageCache.downloadImage(thumb, config);
                if (local != null) movie.setLocalThumbPath(local);
            }
        }
    }

    private void parseNfo(Movie movie, String nfoPath) {
        try {
            byte[] data = currentClient.readFileBytes(nfoPath);
            if (data == null) return;
            NfoMetadata metadata = NfoParser.parse(data);
            if (metadata == null) return;
            if (metadata.getTitle() != null) movie.setTitle(metadata.getTitle());
            movie.setOriginalTitle(metadata.getOriginalTitle());
            movie.setPlot(metadata.getPlot());
            movie.setDirector(metadata.getDirector());
            movie.setYear(metadata.getYear());
            movie.setRating(metadata.getRating());
            movie.setDuration(metadata.getRuntime());
            movie.setActorList(metadata.getActors());
            movie.setGenreList(metadata.getGenres());
        } catch (Exception e) {
            Log.e(TAG, "NFO Parse error", e);
        }
    }

    private String getFolderName(String path) {
        if (path == null) return "";
        String normalized = path.replace("/", "\\");
        int lastSep = normalized.lastIndexOf("\\");
        return lastSep >= 0 ? normalized.substring(lastSep + 1) : path;
    }

    public interface ScanCallback {
        void onStart();
        void onServerStart(SmbConfig config);
        void onProgress(int current, int total, String currentPath);
        void onComplete(int addedCount, int totalServers);
        void onError(String error);
    }
}