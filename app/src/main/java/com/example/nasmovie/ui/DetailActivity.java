package com.example.nasmovie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.util.FileUtils;
import com.example.nasmovie.util.StringUtils;
import com.example.nasmovie.util.SmbImageLoader;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 电影详情页
 */
public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_MOVIE_ID = "movie_id";

    private ImageView ivPoster;
    private TextView tvTitle;
    private TextView tvOriginalTitle;
    private TextView tvYear;
    private TextView tvDuration;
    private TextView tvRating;
    private TextView tvDirector;
    private TextView tvActors;
    private TextView tvPlot;
    private MaterialButton btnPlay;
    private MaterialButton btnFavorite;
    private View progressContainer;
    private TextView tvProgress;
    private ProgressBar progressBar;

    private MovieRepository repository;
    private Movie movie;
    private String movieId;
    private boolean isFavorite = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        movieId = getIntent().getStringExtra(EXTRA_MOVIE_ID);
        if (movieId == null) {
            finish();
            return;
        }

        initViews();
        initData();
        loadMovie();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ivPoster = findViewById(R.id.iv_poster);
        tvTitle = findViewById(R.id.tv_title);
        tvOriginalTitle = findViewById(R.id.tv_original_title);
        tvYear = findViewById(R.id.tv_year);
        tvDuration = findViewById(R.id.tv_duration);
        tvRating = findViewById(R.id.tv_rating);
        tvDirector = findViewById(R.id.tv_director);
        tvActors = findViewById(R.id.tv_actors);
        tvPlot = findViewById(R.id.tv_plot);
        btnPlay = findViewById(R.id.btn_play);
        btnFavorite = findViewById(R.id.btn_favorite);
        progressContainer = findViewById(R.id.progress_container);
        tvProgress = findViewById(R.id.tv_progress);
        progressBar = findViewById(R.id.progress_bar);

        btnPlay.setOnClickListener(v -> playMovie());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    private void initData() {
        repository = new MovieRepository(this);
    }

    private void loadMovie() {
        new Thread(() -> {
            movie = repository.getMovieById(movieId);
            isFavorite = repository.isFavorite(movieId);
            WatchProgress progress = repository.getWatchProgress(movieId);

            runOnUiThread(() -> {
                if (movie != null) {
                    displayMovie();
                    displayProgress(progress);
                    updateFavoriteButton();
                } else {
                    finish();
                }
            });
        }).start();
    }

    private void displayMovie() {
        // 标题
        tvTitle.setText(movie.getTitle());

        // 原标题
        if (StringUtils.isNotEmpty(movie.getOriginalTitle())) {
            tvOriginalTitle.setVisibility(View.VISIBLE);
            tvOriginalTitle.setText(movie.getOriginalTitle());
        } else {
            tvOriginalTitle.setVisibility(View.GONE);
        }

        // 年份
        if (movie.getYear() > 0) {
            tvYear.setText(String.valueOf(movie.getYear()));
        } else {
            tvYear.setText("未知年份");
        }

        // 时长
        if (movie.getDuration() > 0) {
            tvDuration.setText(FileUtils.formatDurationMinutes(movie.getDuration()));
        } else {
            tvDuration.setText("未知时长");
        }

        // 评分
        if (movie.getRating() > 0) {
            tvRating.setText(StringUtils.formatRating(movie.getRating()));
        } else {
            tvRating.setText("暂无评分");
        }

        // 导演
        if (StringUtils.isNotEmpty(movie.getDirector())) {
            findViewById(R.id.director_container).setVisibility(View.VISIBLE);
            tvDirector.setText(movie.getDirector());
        } else {
            findViewById(R.id.director_container).setVisibility(View.GONE);
        }

        // 演员
        List<String> actors = movie.getActorList();
        if (!actors.isEmpty()) {
            findViewById(R.id.actors_container).setVisibility(View.VISIBLE);
            tvActors.setText(StringUtils.join(actors, ", "));
        } else {
            findViewById(R.id.actors_container).setVisibility(View.GONE);
        }

        // 简介
        if (StringUtils.isNotEmpty(movie.getPlot())) {
            tvPlot.setText(movie.getPlot());
        } else {
            tvPlot.setText(R.string.movie_no_plot);
        }

        // 海报 - 详情页优先使用 thumb.jpg
        String localThumb = movie.getLocalThumbPath();
        String localPoster = movie.getLocalPosterPath();
        boolean thumbExists = localThumb != null && !localThumb.isEmpty() && new java.io.File(localThumb).exists();
        boolean posterExists = localPoster != null && !localPoster.isEmpty() && new java.io.File(localPoster).exists();

        if (thumbExists) {
            // 使用本地缓存的 thumb.jpg
            Glide.with(this)
                .load(new java.io.File(localThumb))
                .placeholder(R.drawable.bg_poster_placeholder)
                .error(R.drawable.bg_poster_placeholder)
                .into(ivPoster);
        } else if (movie.getThumbPath() != null && !movie.getThumbPath().isEmpty()) {
            // 从 SMB 加载 thumb.jpg
            SmbImageLoader.loadDetailPoster(this, movie, ivPoster);
        } else if (posterExists) {
            // 没有 thumb.jpg，使用 poster.jpg
            Glide.with(this)
                .load(new java.io.File(localPoster))
                .placeholder(R.drawable.bg_poster_placeholder)
                .error(R.drawable.bg_poster_placeholder)
                .into(ivPoster);
        } else if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
            SmbImageLoader.loadPoster(this, movie, ivPoster);
        } else {
            Glide.with(this)
                .load(R.drawable.bg_poster_placeholder)
                .into(ivPoster);
        }
    }

    private void displayProgress(WatchProgress progress) {
        if (progress != null && progress.getPercentage() > 0 && !progress.isCompleted()) {
            progressContainer.setVisibility(View.VISIBLE);
            tvProgress.setText(String.format("已观看 %d%%", progress.getPercentage()));
            progressBar.setProgress(progress.getPercentage());
            btnPlay.setText(R.string.resume_play);
        } else {
            progressContainer.setVisibility(View.GONE);
            btnPlay.setText(R.string.play);
        }
    }

    private void updateFavoriteButton() {
        if (isFavorite) {
            btnFavorite.setIconResource(R.drawable.ic_favorite);
            btnFavorite.setText(R.string.remove_favorite);
        } else {
            btnFavorite.setIconResource(R.drawable.ic_favorite_border);
            btnFavorite.setText(R.string.add_favorite);
        }
    }

    private void playMovie() {
        if (movie == null) return;

        Intent intent = new Intent(this, VlcPlayerActivity.class);
        intent.putExtra(VlcPlayerActivity.EXTRA_MOVIE_ID, movie.getId());
        startActivity(intent);
    }

    private void toggleFavorite() {
        new Thread(() -> {
            if (isFavorite) {
                repository.removeFavorite(movieId);
            } else {
                repository.addFavorite(movieId);
            }
            isFavorite = !isFavorite;
            runOnUiThread(this::updateFavoriteButton);
        }).start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新观看进度
        if (movieId != null) {
            new Thread(() -> {
                WatchProgress progress = repository.getWatchProgress(movieId);
                runOnUiThread(() -> displayProgress(progress));
            }).start();
        }
    }
}