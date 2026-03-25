package com.example.nasmovie.ui

import android.animation.ValueAnimator
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.example.nasmovie.NASMovieApp
import com.example.nasmovie.R
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.data.repository.MovieRepository
import com.example.nasmovie.data.smb.SmbClient
import com.example.nasmovie.data.smb.SmbDataSource
import com.example.nasmovie.player.PlayerGestureHandler
import com.example.nasmovie.player.SubtitleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(UnstableApi::class)
class ExoPlayerActivity : AppCompatActivity(), PlayerGestureHandler.GestureCallback {

    companion object {
        const val EXTRA_MOVIE_ID = "movie_id"
        private const val TAG = "ExoPlayerActivity"
        private const val HIDE_CONTROLS_DELAY = 3000L
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    private lateinit var rootLayout: FrameLayout
    private lateinit var loadingView: LinearLayout
    private lateinit var controlsLayout: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvTitle: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var tvSubtitle: TextView

    private lateinit var subtitleManager: SubtitleManager
    private lateinit var repository: MovieRepository

    private var movie: Movie? = null
    private var movieId: String? = null
    private var startPosition = 0L
    private var isControlsVisible = true
    private var isFullscreen = false

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val hideControlsRunnable = Runnable { hideControls() }
    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            mainHandler.postDelayed(this, 1000)
        }
    }

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentScale = 1.0f
    private var gestureHandler: PlayerGestureHandler? = null

    private lateinit var gestureHintLayout: View
    private lateinit var tvGestureHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exo_player)

        movieId = intent.getStringExtra(EXTRA_MOVIE_ID)
        if (movieId == null) {
            finish()
            return
        }

        initViews()
        initData()
        initPlayer()
        setupBackPressedHandler()
        loadMovie()

        hideSystemUI()
        keepScreenOn(true)
    }

    private fun setupBackPressedHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveProgress()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.root_layout)
        playerView = findViewById(R.id.player_view)
        loadingView = findViewById(R.id.loading_view)
        controlsLayout = findViewById(R.id.controls_layout)
        topBar = findViewById(R.id.top_bar)
        seekBar = findViewById(R.id.seek_bar)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        tvTitle = findViewById(R.id.tv_title)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnBack = findViewById(R.id.btn_back)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        tvSubtitle = findViewById(R.id.tv_subtitle)

        gestureHintLayout = findViewById(R.id.gesture_hint_layout)
        tvGestureHint = findViewById(R.id.tv_gesture_hint)

        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnBack.setOnClickListener {
            saveProgress()
            finish()
        }
        btnFullscreen.setOnClickListener { toggleFullscreen() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser && player != null) {
                    val duration = player!!.duration
                    if (duration != C.TIME_UNSET) {
                        val time = (progress / 100.0 * duration).toLong()
                        tvCurrentTime.text = formatTime(time)
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                mainHandler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                player?.let {
                    val duration = it.duration
                    if (duration != C.TIME_UNSET) {
                        val time = (seekBar.progress / 100.0 * duration).toLong()
                        it.seekTo(time)
                    }
                }
                startHideControlsTimer()
            }
        })

        rootLayout.setOnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            var gestureHandled = false
            if (gestureHandler != null) {
                gestureHandled = gestureHandler!!.onTouchEvent(event)
            }

            if (!gestureHandled && event.action == MotionEvent.ACTION_UP) {
                toggleControls()
                v.performClick()
            }
            true
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = Math.max(1.0f, Math.min(currentScale, 3.0f))
                applyScale()
                return true
            }
        })

        gestureHandler = PlayerGestureHandler(this, rootLayout, this)
    }

    private fun initData() {
        repository = MovieRepository(this)
        subtitleManager = SubtitleManager(this)
        subtitleManager.setSubtitleView(tvSubtitle)
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        loadingView.visibility = View.VISIBLE
                    }
                    Player.STATE_READY -> {
                        loadingView.visibility = View.GONE
                        startProgressUpdate()
                        if (startPosition > 0) {
                            player?.seekTo(startPosition)
                            startPosition = 0
                        }
                    }
                    Player.STATE_ENDED -> {
                        saveProgress()
                        finish()
                    }
                    Player.STATE_IDLE -> { }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    btnPlayPause.setImageResource(R.drawable.ic_pause)
                } else {
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error: ${error.message}", error)
                loadingView.visibility = View.GONE
            }
        })
    }

    private fun loadMovie() {
        val id = movieId ?: return
        loadingView.visibility = View.VISIBLE

        scope.launch {
            try {
                movie = repository.getMovieById(id)
                if (movie != null) {
                    val progress = repository.getWatchProgress(id)
                    if (progress != null) {
                        startPosition = progress.position
                    }
                    tvTitle.text = movie?.title
                    playMovie()
                } else {
                    Toast.makeText(this@ExoPlayerActivity, "找不到电影信息", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading movie", e)
            }
        }
    }

    private fun playMovie() {
        val currentMovie = movie ?: return

        scope.launch(Dispatchers.IO) {
            try {
                val serverId = currentMovie.serverId?.toLongOrNull() ?: return@launch
                val config = NASMovieApp.getInstance().database.smbConfigDao().getById(serverId)
                
                if (config == null) {
                    withContext(Dispatchers.Main) { loadingView.visibility = View.GONE }
                    return@launch
                }

                val smbUri = buildSmbUri(config, currentMovie.videoPath)
                loadSubtitles(config)

                withContext(Dispatchers.Main) {
                    val smbDataSourceFactory = SmbDataSource.Factory(config)
                    val mediaSource = ProgressiveMediaSource.Factory(smbDataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(smbUri)))

                    player?.setMediaSource(mediaSource)
                    player?.prepare()
                    player?.playWhenReady = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing movie", e)
                withContext(Dispatchers.Main) { loadingView.visibility = View.GONE }
            }
        }
    }

    private fun buildSmbUri(config: SmbConfig, videoPath: String?): String {
        val uri = java.lang.StringBuilder()
        uri.append("smb://")
        uri.append(config.host)

        if (config.port != 445 && config.port > 0) {
            uri.append(":").append(config.port)
        }

        uri.append("/").append(config.shareName)

        if (videoPath != null) {
            val normalizedPath = videoPath.replace('\\', '/')
            if (!normalizedPath.startsWith("/")) {
                uri.append("/")
            }
            uri.append(normalizedPath)
        }

        return uri.toString()
    }

    private suspend fun loadSubtitles(config: SmbConfig) {
        val subtitlePaths = movie?.subtitlePathList
        if (!subtitlePaths.isNullOrEmpty()) {
            for (subtitlePath in subtitlePaths) {
                val localPath = downloadSubtitle(subtitlePath, config)
                if (localPath != null) {
                    withContext(Dispatchers.Main) {
                        subtitleManager.setSubtitleView(tvSubtitle)
                        subtitleManager.loadSubtitle(localPath)
                    }
                    break
                }
            }
        }
    }

    private suspend fun downloadSubtitle(smbPath: String, config: SmbConfig): String? {
        val fileName = "subtitle_${Math.abs(smbPath.hashCode())}.${getExtension(smbPath)}"
        val cacheFile = File(cacheDir, fileName)

        if (cacheFile.exists()) {
            return cacheFile.absolutePath
        }

        return try {
            withContext(Dispatchers.IO) {
                SmbClient().use { client ->
                    if (!client.connect(config)) {
                        return@use null
                    }

                    client.readFile(smbPath)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                            output.flush()
                            cacheFile.absolutePath
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading subtitle", e)
            null
        }
    }

    private fun getExtension(path: String?): String {
        if (path == null || !path.contains(".")) {
            return "srt"
        }
        return path.substring(path.lastIndexOf(".") + 1).lowercase(Locale.ROOT)
    }

    private fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
        startHideControlsTimer()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            btnFullscreen.setImageResource(R.drawable.ic_fullscreen)
        }
        startHideControlsTimer()
    }

    private fun toggleControls() {
        if (isControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun showControls() {
        isControlsVisible = true
        controlsLayout.visibility = View.VISIBLE
        topBar.visibility = View.VISIBLE
        hideSystemUI()
        startHideControlsTimer()
    }

    private fun hideControls() {
        isControlsVisible = false
        controlsLayout.visibility = View.GONE
        topBar.visibility = View.GONE
        hideSystemUI()
    }

    private fun startHideControlsTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, HIDE_CONTROLS_DELAY)
    }

    private fun updateProgress() {
        val p = player ?: return
        if (!p.isPlaying) return

        val position = p.currentPosition
        val duration = p.duration

        if (duration != C.TIME_UNSET && duration > 0) {
            val progress = (position * 100 / duration).toInt()
            seekBar.progress = progress
            tvCurrentTime.text = formatTime(position)
            tvTotalTime.text = formatTime(duration)
        }
    }

    private fun startProgressUpdate() {
        mainHandler.removeCallbacks(updateProgressRunnable)
        mainHandler.post(updateProgressRunnable)
        updateProgress()
    }

    private fun stopProgressUpdate() {
        mainHandler.removeCallbacks(updateProgressRunnable)
    }

    private fun saveProgress() {
        val p = player ?: return
        val currentMovie = movie ?: return

        val position = p.currentPosition
        val duration = p.duration

        if (position > 0 && duration > 0 && duration != C.TIME_UNSET) {
            scope.launch {
                try {
                    repository.saveWatchProgress(currentMovie.id, position, duration)
                    if (currentMovie.duration <= 0) {
                        val durationMinutes = (duration / (1000 * 60)).toInt()
                        if (durationMinutes > 0) {
                            currentMovie.duration = durationMinutes
                            repository.saveMovie(currentMovie)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving progress", e)
                }
            }
        }
    }

    private fun formatTime(timeMs: Long): String {
        if (timeMs < 0) return "00:00"
        val totalSeconds = timeMs / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun keepScreenOn(keep: Boolean) {
        if (keep) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyScale() {
        val videoSurface = playerView.videoSurfaceView
        if (videoSurface != null) {
            videoSurface.scaleX = currentScale
            videoSurface.scaleY = currentScale
        }
    }

    // --- GestureCallback ---

    override fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    override fun getDuration(): Long {
        return player?.duration ?: 0L
    }

    override fun onBrightnessChanged(percent: Int) {
        showGestureHint("亮度: $percent%")
    }

    override fun onVolumeChanged(percent: Int) {
        showGestureHint("音量: $percent%")
    }

    override fun onSeekPreview(position: Long, delta: Long) {
        val duration = player?.duration ?: return
        if (duration == C.TIME_UNSET) return

        val deltaStr = if (delta > 0) "+${delta / 1000}s" else "${delta / 1000}s"
        showGestureHint("${formatTime(position)} / ${formatTime(duration)}\n$deltaStr")
    }

    override fun onSeek(position: Long) {
        player?.seekTo(position)
        hideGestureHint()
        startHideControlsTimer()
    }

    override fun onGestureEnd() {
        hideGestureHint()
        startHideControlsTimer()
    }

    private fun showGestureHint(text: String) {
        tvGestureHint.text = text
        if (gestureHintLayout.visibility != View.VISIBLE) {
            gestureHintLayout.visibility = View.VISIBLE
            gestureHintLayout.alpha = 0f
            gestureHintLayout.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun hideGestureHint() {
        if (gestureHintLayout.visibility == View.VISIBLE) {
            gestureHintLayout.animate().alpha(0f).setDuration(200).withEndAction {
                gestureHintLayout.visibility = View.GONE
            }.start()
        }
    }

    override fun onStart() {
        super.onStart()
        if (com.example.nasmovie.util.PreferenceManager(this).isLockEnabled) {
            val intent = android.content.Intent(this, LockActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        saveProgress()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel() // 取消所有协程
        stopProgressUpdate()
        mainHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        repository.close()
    }
}
