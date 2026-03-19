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
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.data.smb.SmbClient;
import com.example.nasmovie.data.smb.SmbDataSource;
import com.example.nasmovie.player.PlayerGestureHandler;
import com.example.nasmovie.player.SubtitleManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * ExoPlayer 视频播放器界面
 * 使用 ExoPlayer (Media3) 播放 SMB 上的视频
 */
@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerActivity extends AppCompatActivity implements
    PlayerGestureHandler.GestureCallback {

    public static final String EXTRA_MOVIE_ID = "movie_id";
    private static final String TAG = "ExoPlayerActivity";
    private static final int HIDE_CONTROLS_DELAY = 3000;

    // ExoPlayer
    private ExoPlayer player;
    private PlayerView playerView;

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

    // Subtitle
    private TextView tvSubtitle;
    private SubtitleManager subtitleManager;

    // Data
    private MovieRepository repository;
    private Movie movie;
    private String movieId;
    private long startPosition = 0;
    private boolean isControlsVisible = true;
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
        setContentView(R.layout.activity_exo_player);

        movieId = getIntent().getStringExtra(EXTRA_MOVIE_ID);
        if (movieId == null) {
            finish();
            return;
        }

        initViews();
        initData();
        initPlayer();
        loadMovie();

        hideSystemUI();
        keepScreenOn(true);
    }

    private void initViews() {
        rootLayout = findViewById(R.id.root_layout);
        playerView = findViewById(R.id.player_view);
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
                if (fromUser && player != null) {
                    long duration = player.getDuration();
                    if (duration != C.TIME_UNSET) {
                        long time = (long) (progress / 100.0 * duration);
                        tvCurrentTime.setText(formatTime(time));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (player != null) {
                    long duration = player.getDuration();
                    if (duration != C.TIME_UNSET) {
                        long time = (long) (seekBar.getProgress() / 100.0 * duration);
                        player.seekTo(time);
                    }
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
                toggleControls();
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

    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        // Event listener
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                runOnUiThread(() -> {
                    switch (playbackState) {
                        case Player.STATE_BUFFERING:
                            Log.d(TAG, "Buffering");
                            loadingView.setVisibility(View.VISIBLE);
                            break;
                        case Player.STATE_READY:
                            Log.d(TAG, "Ready");
                            loadingView.setVisibility(View.GONE);
                            startProgressUpdate();
                            // Seek to start position if exists
                            if (startPosition > 0) {
                                player.seekTo(startPosition);
                                startPosition = 0;
                            }
                            break;
                        case Player.STATE_ENDED:
                            Log.d(TAG, "Ended");
                            saveProgress();
                            finish();
                            break;
                        case Player.STATE_IDLE:
                            Log.d(TAG, "Idle");
                            break;
                    }
                });
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                runOnUiThread(() -> {
                    if (isPlaying) {
                        btnPlayPause.setImageResource(R.drawable.ic_pause);
                    } else {
                        btnPlayPause.setImageResource(R.drawable.ic_play);
                    }
                });
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.e(TAG, "Player error: " + error.getMessage(), error);
                runOnUiThread(() -> loadingView.setVisibility(View.GONE));
            }
        });
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

                // Build SMB URI
                String smbUri = buildSmbUri(config, movie.getVideoPath());
                Log.d(TAG, "Playing SMB video: " + smbUri);

                // Load subtitles
                loadSubtitles(config);

                runOnUiThread(() -> {
                    // Create SMB DataSource Factory
                    SmbDataSource.Factory smbDataSourceFactory = new SmbDataSource.Factory(config);

                    // Create MediaSource
                    ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(smbDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(smbUri)));

                    player.setMediaSource(mediaSource);
                    player.prepare();
                    player.setPlayWhenReady(true);
                });

            } catch (Exception e) {
                Log.e(TAG, "Error playing movie", e);
                runOnUiThread(() -> loadingView.setVisibility(View.GONE));
            }
        }).start();
    }

    private String buildSmbUri(SmbConfig config, String videoPath) {
        StringBuilder uri = new StringBuilder();
        uri.append("smb://");
        uri.append(config.getHost());

        // Add port if not default
        if (config.getPort() != 445 && config.getPort() > 0) {
            uri.append(":").append(config.getPort());
        }

        // Add share and path
        uri.append("/").append(config.getShareName());

        // Add video path
        String normalizedPath = videoPath.replace('\\', '/');
        if (!normalizedPath.startsWith("/")) {
            uri.append("/");
        }
        uri.append(normalizedPath);

        return uri.toString();
    }

    private void loadSubtitles(SmbConfig config) {
        List<String> subtitlePaths = movie.getSubtitlePathList();
        if (subtitlePaths != null && !subtitlePaths.isEmpty()) {
            for (String subtitlePath : subtitlePaths) {
                String localPath = downloadSubtitle(subtitlePath, config);
                if (localPath != null) {
                    subtitleManager.setSubtitleView(tvSubtitle);
                    subtitleManager.loadSubtitle(localPath);
                    break;
                }
            }
        }
    }

    private String downloadSubtitle(String smbPath, SmbConfig config) {
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
        if (player == null) return;

        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
        startHideControlsTimer();
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit);
        } else {
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
        isControlsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        controlsLayout.setVisibility(View.VISIBLE);
        startHideControlsTimer();
        adjustSubtitlePosition(true);
    }

    private void hideControls() {
        isControlsVisible = false;
        topBar.setVisibility(View.GONE);
        controlsLayout.setVisibility(View.GONE);
        handler.removeCallbacks(hideControlsRunnable);
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
        if (player == null) return;

        long currentTime = player.getCurrentPosition();
        long totalTime = player.getDuration();

        if (totalTime != C.TIME_UNSET && totalTime > 0) {
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
        if (player == null || movieId == null) return;

        long position = player.getCurrentPosition();
        long durationMs = player.getDuration();

        if (position > 0 && durationMs != C.TIME_UNSET && durationMs > 0) {
            new Thread(() -> {
                repository.saveWatchProgress(movieId, position, durationMs);

                if (movie != null && (movie.getDuration() <= 0)) {
                    int durationMinutes = (int) (durationMs / (1000 * 60));
                    if (durationMinutes > 0) {
                        movie.setDuration(durationMinutes);
                        repository.saveMovie(movie);
                        Log.d(TAG, "Updated movie duration to: " + durationMinutes + " mins");
                    }
                }
            }).start();
        }
    }

    private void applyScale() {
        playerView.setScaleX(currentScale);
        playerView.setScaleY(currentScale);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureHandler != null) {
            boolean handled = gestureHandler.onTouchEvent(event);
            if (handled) {
                return true;
            }
        }

        if (scaleGestureDetector != null) {
            scaleGestureDetector.onTouchEvent(event);
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (player != null) {
            player.play();
        }
        startProgressUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        stopProgressUpdate();
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        keepScreenOn(false);
        stopProgressUpdate();
        handler.removeCallbacks(hideControlsRunnable);

        if (player != null) {
            player.release();
            player = null;
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
        return player != null ? player.getCurrentPosition() : 0;
    }

    @Override
    public long getDuration() {
        return player != null ? player.getDuration() : 0;
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
        String targetTime = formatTime(position);
        String offset = formatTime(Math.abs(delta));
        String sign = delta >= 0 ? "+" : "-";
        String hint = targetTime + " (" + sign + offset + ")";
        showGestureHint(hint);
    }

    @Override
    public void onSeek(long position) {
        if (player != null) {
            player.seekTo(position);
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

    private void adjustSubtitlePosition(boolean controlsVisible) {
        if (tvSubtitle == null) return;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) tvSubtitle.getLayoutParams();
        int targetMargin = controlsVisible
            ? (int) (95 * getResources().getDisplayMetrics().density)
            : (int) (32 * getResources().getDisplayMetrics().density);

        ValueAnimator animator = ValueAnimator.ofInt(params.bottomMargin, targetMargin);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            params.bottomMargin = (int) animation.getAnimatedValue();
            tvSubtitle.setLayoutParams(params);
        });
        animator.start();
    }
}
