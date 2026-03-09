package com.example.nasmovie.ui;

import android.animation.ValueAnimator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.data.smb.SmbClient;
import com.example.nasmovie.player.PlayerGestureHandler;
import com.example.nasmovie.player.SubtitleManager;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * VLC 视频播放器界面
 * 使用 VLC 原生支持 SMB 协议播放
 */
public class VlcPlayerActivity extends AppCompatActivity implements
    PlayerGestureHandler.GestureCallback {

    public static final String EXTRA_MOVIE_ID = "movie_id";
    private static final String TAG = "VlcPlayerActivity";
    private static final int HIDE_CONTROLS_DELAY = 3000;

    // VLC
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout videoLayout;

    // UI
    private FrameLayout rootLayout;
    private LinearLayout loadingView;
    private LinearLayout controlsLayout;
    private LinearLayout topBar;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private TextView tvTitle;
    private ImageButton btnPlayPause;
    private ImageButton btnBack;
    private ImageButton btnFullscreen;
    private ProgressBar progressBar;

    // Subtitle
    private TextView tvSubtitle;
    private SubtitleManager subtitleManager;

    // Data
    private MovieRepository repository;
    private Movie movie;
    private String movieId;
    private long startPosition = 0;
    private boolean isControlsVisible = true;
    private boolean isLocked = false;
    private boolean isPlaying = false;
    private boolean isFullscreen = false;

    // Handler
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;
    private Runnable updateProgressRunnable;

    // Scale gesture detector for zoom
    private ScaleGestureDetector scaleGestureDetector;
    private float currentScale = 1.0f;

    // Gesture handler for brightness, volume, seek
    private PlayerGestureHandler gestureHandler;

    // Gesture hint views
    private View gestureHintLayout;
    private TextView tvGestureHint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vlc_player);

        movieId = getIntent().getStringExtra(EXTRA_MOVIE_ID);
        if (movieId == null) {
            finish();
            return;
        }

        initViews();
        initData();
        initVLC();
        loadMovie();

        hideSystemUI();
        keepScreenOn(true);
    }

    private void initViews() {
        rootLayout = findViewById(R.id.root_layout);
        videoLayout = findViewById(R.id.video_layout);
        loadingView = findViewById(R.id.loading_view);
        controlsLayout = findViewById(R.id.controls_layout);
        topBar = findViewById(R.id.top_bar);
        seekBar = findViewById(R.id.seek_bar);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        tvTitle = findViewById(R.id.tv_title);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnBack = findViewById(R.id.btn_back);
        btnFullscreen = findViewById(R.id.btn_fullscreen);
        progressBar = findViewById(R.id.progress_bar);
        tvSubtitle = findViewById(R.id.tv_subtitle);

        // Gesture hint views
        gestureHintLayout = findViewById(R.id.gesture_hint_layout);
        tvGestureHint = findViewById(R.id.tv_gesture_hint);

        // Play/Pause button
        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        // Back button
        btnBack.setOnClickListener(v -> onBackPressed());

        // Fullscreen button
        btnFullscreen.setOnClickListener(v -> toggleFullscreen());

        // Seek bar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    long time = (long) (progress / 100.0 * mediaPlayer.getLength());
                    tvCurrentTime.setText(formatTime(time));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null) {
                    long time = (long) (seekBar.getProgress() / 100.0 * mediaPlayer.getLength());
                    mediaPlayer.setTime(time);
                }
                startHideControlsTimer();
            }
        });

        // Root layout touch listener for gestures and click
        rootLayout.setOnTouchListener((v, event) -> {
            // Handle scale gesture
            if (scaleGestureDetector != null) {
                scaleGestureDetector.onTouchEvent(event);
            }

            // Handle gestures (brightness, volume, seek)
            boolean gestureHandled = false;
            if (gestureHandler != null) {
                gestureHandled = gestureHandler.onTouchEvent(event);
            }

            // Handle click to toggle controls (only if no gesture was handled)
            if (!gestureHandled && event.getAction() == MotionEvent.ACTION_UP) {
                if (!isLocked) {
                    toggleControls();
                }
                v.performClick();
            }

            return true;
        });

        // Initialize hide controls runnable
        hideControlsRunnable = this::hideControls;
        updateProgressRunnable = new Runnable() {
            @Override
            public void run() {
                updateProgress();
                handler.postDelayed(this, 1000);
            }
        };

        // Scale gesture detector for zoom
        scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                currentScale *= detector.getScaleFactor();
                currentScale = Math.max(1.0f, Math.min(currentScale, 3.0f));
                applyScale();
                return true;
            }
        });

        // Initialize gesture handler for brightness, volume, seek
        gestureHandler = new PlayerGestureHandler(this, rootLayout, this);
    }

    private void initData() {
        repository = new MovieRepository(this);
        subtitleManager = new SubtitleManager(this);
        subtitleManager.setSubtitleView(tvSubtitle);
    }

    private void initVLC() {
        ArrayList<String> options = new ArrayList<>();
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");
        options.add("--rtsp-tcp");
        options.add("--network-caching=3000");
        options.add("--file-caching=3000");
        // SMB options - using standard options that work with VLC 3.6.0
        options.add("--smb-user=");
        options.add("--smb-pwd=");

        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);

        // Attach video layout
        mediaPlayer.attachViews(videoLayout, null, false, false);

        // Event listener
        mediaPlayer.setEventListener(event -> {
            runOnUiThread(() -> handleMediaPlayerEvent(event));
        });
    }

    private void handleMediaPlayerEvent(MediaPlayer.Event event) {
        switch (event.type) {
            case MediaPlayer.Event.Opening:
                Log.d(TAG, "Opening media");
                break;
            case MediaPlayer.Event.Playing:
                Log.d(TAG, "Media playing");
                loadingView.setVisibility(View.GONE);
                isPlaying = true;
                btnPlayPause.setImageResource(R.drawable.ic_pause);
                startProgressUpdate();
                // Seek to start position if exists (after video is ready)
                if (startPosition > 0) {
                    mediaPlayer.setTime(startPosition);
                }
                break;
            case MediaPlayer.Event.Paused:
                Log.d(TAG, "Media paused");
                isPlaying = false;
                btnPlayPause.setImageResource(R.drawable.ic_play);
                break;
            case MediaPlayer.Event.Stopped:
                Log.d(TAG, "Media stopped");
                isPlaying = false;
                btnPlayPause.setImageResource(R.drawable.ic_play);
                break;
            case MediaPlayer.Event.EndReached:
                Log.d(TAG, "Media ended");
                saveProgress();
                finish();
                break;
            case MediaPlayer.Event.Buffering:
                int buffering = (int) event.getBuffering();
                Log.d(TAG, "Buffering: " + buffering + "%");
                if (buffering < 100) {
                    loadingView.setVisibility(View.VISIBLE);
                } else {
                    loadingView.setVisibility(View.GONE);
                }
                break;
            case MediaPlayer.Event.EncounteredError:
                Log.e(TAG, "Media error");
                loadingView.setVisibility(View.GONE);
                break;
        }
    }

    private void loadMovie() {
        loadingView.setVisibility(View.VISIBLE);

        new Thread(() -> {
            movie = repository.getMovieById(movieId);
            if (movie != null) {
                WatchProgress progress = repository.getWatchProgress(movieId);
                if (progress != null) {
                    startPosition = progress.getPosition();
                }
            }
            runOnUiThread(() -> {
                if (movie != null) {
                    tvTitle.setText(movie.getTitle());
                    playMovie();
                } else {
                    finish();
                }
            });
        }).start();
    }

    private void playMovie() {
        new Thread(() -> {
            try {
                SmbConfig config = NASMovieApp.getInstance().getDatabase()
                    .smbConfigDao().getById(Long.parseLong(movie.getServerId()));

                if (config == null) {
                    Log.e(TAG, "SMB config not found");
                    runOnUiThread(() -> loadingView.setVisibility(View.GONE));
                    return;
                }

                // Build SMB URL with authentication
                String smbUrl = buildSmbUrl(config, movie.getVideoPath());
                Log.d(TAG, "Playing SMB video from server: " + config.getHost());

                // Load subtitles
                loadSubtitles(config);

                runOnUiThread(() -> {
                    // Create media and play
                    Media media = new Media(libVLC, Uri.parse(smbUrl));
                    media.setHWDecoderEnabled(true, false);
                    media.addOption(":network-caching=3000");
                    media.addOption(":file-caching=3000");

                    mediaPlayer.setMedia(media);
                    media.release();

                    mediaPlayer.play();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error playing movie", e);
                runOnUiThread(() -> loadingView.setVisibility(View.GONE));
            }
        }).start();
    }

    private String buildSmbUrl(SmbConfig config, String videoPath) {
        StringBuilder url = new StringBuilder();
        url.append("smb://");

        // Add credentials if not anonymous
        if (!config.isAnonymous() && config.getUsername() != null && !config.getUsername().isEmpty()) {
            url.append(encodeSmbUsername(config.getUsername()));
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                url.append(":").append(encodeSmbPassword(config.getPassword()));
            }
            url.append("@");
        }

        // Add host
        url.append(config.getHost());

        // Add port if not default
        if (config.getPort() != 445 && config.getPort() > 0) {
            url.append(":").append(config.getPort());
        }

        // Add share and path
        url.append("/").append(config.getShareName());

        // Add video path
        String normalizedPath = videoPath.replace('\\', '/');
        if (!normalizedPath.startsWith("/")) {
            url.append("/");
        }
        url.append(normalizedPath);

        return url.toString();
    }

    private String encodeSmbUsername(String username) {
        // URL encode special characters
        return username.replace("@", "%40")
                       .replace(":", "%3A")
                       .replace("/", "%2F");
    }

    private String encodeSmbPassword(String password) {
        // URL encode special characters
        return password.replace("@", "%40")
                       .replace(":", "%3A")
                       .replace("/", "%2F")
                       .replace("?", "%3F")
                       .replace("#", "%23");
    }

    private void loadSubtitles(SmbConfig config) {
        List<String> subtitlePaths = movie.getSubtitlePathList();
        if (subtitlePaths != null && !subtitlePaths.isEmpty()) {
            // Download the first subtitle file
            for (String subtitlePath : subtitlePaths) {
                String localPath = downloadSubtitle(subtitlePath, config);
                if (localPath != null) {
                    subtitleManager.setSmbClient(null); // We'll load from local file
                    subtitleManager.setSubtitleView(tvSubtitle);
                    subtitleManager.loadSubtitle(localPath);
                    break;
                }
            }
        }
    }

    private String downloadSubtitle(String smbPath, SmbConfig config) {
        // Generate local cache path
        String fileName = "subtitle_" + Math.abs(smbPath.hashCode()) + "." + getExtension(smbPath);
        File cacheFile = new File(getCacheDir(), fileName);

        if (cacheFile.exists()) {
            return cacheFile.getAbsolutePath();
        }

        SmbClient client = null;
        FileOutputStream fos = null;
        InputStream is = null;

        try {
            client = new SmbClient();
            if (!client.connect(config)) {
                return null;
            }

            is = client.readFile(smbPath);
            if (is == null) {
                return null;
            }

            fos = new FileOutputStream(cacheFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.flush();

            return cacheFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "Error downloading subtitle: " + e.getMessage());
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

    private String getExtension(String path) {
        if (path == null || !path.contains(".")) {
            return "srt";
        }
        return path.substring(path.lastIndexOf(".") + 1).toLowerCase();
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        } else {
            mediaPlayer.play();
        }
        startHideControlsTimer();
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            // Switch to landscape
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit);
        } else {
            // Switch to portrait
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen);
        }
        startHideControlsTimer();
    }

    private void toggleControls() {
        if (isControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        if (isLocked) return;

        isControlsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        controlsLayout.setVisibility(View.VISIBLE);
        startHideControlsTimer();

        // 字幕上移，避免被控制栏遮挡
        adjustSubtitlePosition(true);
    }

    private void hideControls() {
        isControlsVisible = false;
        topBar.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.GONE);
        handler.removeCallbacks(hideControlsRunnable);

        // 字幕下移，靠近屏幕底部
        adjustSubtitlePosition(false);
    }

    private void startHideControlsTimer() {
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY);
    }

    private void startProgressUpdate() {
        handler.post(updateProgressRunnable);
    }

    private void stopProgressUpdate() {
        handler.removeCallbacks(updateProgressRunnable);
    }

    private void updateProgress() {
        if (mediaPlayer == null) return;

        long currentTime = mediaPlayer.getTime();
        long totalTime = mediaPlayer.getLength();

        if (totalTime > 0) {
            int progress = (int) ((currentTime * 100) / totalTime);
            seekBar.setProgress(progress);
            tvTotalTime.setText(formatTime(totalTime));
        }

        tvCurrentTime.setText(formatTime(currentTime));

        // Update subtitle
        if (subtitleManager != null && subtitleManager.hasSubtitle()) {
            subtitleManager.update(currentTime);
        }
    }

    private String formatTime(long timeMs) {
        if (timeMs <= 0) return "00:00";

        long seconds = timeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void saveProgress() {
        if (mediaPlayer == null || movieId == null) return;

        long position = mediaPlayer.getTime();
        long duration = mediaPlayer.getLength();

        if (position > 0 && duration > 0) {
            // Save to database in background thread
            new Thread(() -> {
                repository.saveWatchProgress(movieId, position, duration);
            }).start();
        }
    }

    private void applyScale() {
        videoLayout.setScaleX(currentScale);
        videoLayout.setScaleY(currentScale);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Handle brightness, volume, seek gestures first
        if (gestureHandler != null) {
            boolean handled = gestureHandler.onTouchEvent(event);
            if (handled) {
                return true;
            }
        }

        // Let scale gesture detector handle zoom
        if (scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(event);
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (mediaPlayer != null && !mediaPlayer.isPlaying() && isPlaying) {
            mediaPlayer.play();
        }
        startProgressUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
        stopProgressUpdate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        keepScreenOn(false);
        stopProgressUpdate();
        handler.removeCallbacks(hideControlsRunnable);

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
    }

    @Override
    public void onBackPressed() {
        saveProgress();
        super.onBackPressed();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }

    /**
     * 设置屏幕常亮
     * @param keepOn true 保持屏幕常亮, false 恢复系统自动锁屏
     */
    private void keepScreenOn(boolean keepOn) {
        if (keepOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // ==================== GestureCallback Implementation ====================

    @Override
    public long getCurrentPosition() {
        return mediaPlayer != null ? mediaPlayer.getTime() : 0;
    }

    @Override
    public long getDuration() {
        return mediaPlayer != null ? mediaPlayer.getLength() : 0;
    }

    @Override
    public void onBrightnessChanged(int percent) {
        showGestureHint("亮度: " + percent + "%");
    }

    @Override
    public void onVolumeChanged(int percent) {
        showGestureHint("音量: " + percent + "%");
    }

    @Override
    public void onSeekPreview(long position, long delta) {
        // 显示目标时间和偏移量，例如：05:30 (+00:30)
        String targetTime = formatTime(position);
        String offset = formatTime(Math.abs(delta));
        String sign = delta >= 0 ? "+" : "-";
        String hint = targetTime + " (" + sign + offset + ")";
        showGestureHint(hint);
    }

    @Override
    public void onSeek(long position) {
        if (mediaPlayer != null) {
            mediaPlayer.setTime(position);
            showGestureHint("跳转到: " + formatTime(position));
        }
    }

    @Override
    public void onGestureEnd() {
        hideGestureHint();
    }

    private void showGestureHint(String text) {
        if (tvGestureHint != null) {
            tvGestureHint.setText(text);
            gestureHintLayout.setVisibility(View.VISIBLE);
        }
    }

    private void hideGestureHint() {
        if (gestureHintLayout != null) {
            gestureHintLayout.setVisibility(View.GONE);
        }
    }

    /**
     * 调整字幕位置
     * @param controlsVisible 控制栏是否显示
     */
    private void adjustSubtitlePosition(boolean controlsVisible) {
        if (tvSubtitle == null) return;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tvSubtitle.getLayoutParams();
        int targetMargin = controlsVisible
            ? (int) (95 * getResources().getDisplayMetrics().density)  // 控制栏显示时
            : (int) (32 * getResources().getDisplayMetrics().density);  // 控制栏隐藏时

        // 使用动画过渡
        ValueAnimator animator = ValueAnimator.ofInt(params.bottomMargin, targetMargin);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            params.bottomMargin = (int) animation.getAnimatedValue();
            tvSubtitle.setLayoutParams(params);
        });
        animator.start();
    }
}
