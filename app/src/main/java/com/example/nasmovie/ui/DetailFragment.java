package com.example.nasmovie.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.util.FileUtils;
import com.example.nasmovie.util.StringUtils;
import com.example.nasmovie.util.SmbImageLoader;
import com.example.nasmovie.view.NasToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 电影详情 Fragment
 */
public class DetailFragment extends Fragment {

    public static final String ARG_MOVIE_ID = "movie_id";

    private ViewGroup container;
    private View rootView;
    
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
    private View cardProgress;
    private TextView tvProgress;
    private ProgressBar progressBar;
    private NasToolbar toolbar;

    private MovieRepository repository;
    private Movie movie;
    private String movieId;
    private boolean isFavorite = false;
    private View contentContainer;
    private ProgressBar progressLoading;
    private WatchProgress currentProgress;

    public static DetailFragment newInstance(String movieId) {
        DetailFragment fragment = new DetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MOVIE_ID, movieId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        this.container = container;
        rootView = inflater.inflate(R.layout.fragment_detail, container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getArguments() != null) {
            movieId = getArguments().getString(ARG_MOVIE_ID);
        }

        initViews(view);
        initData();
        loadMovie();
    }
    
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // 重新加载布局
        if (container != null) {
            container.removeView(rootView);
            LayoutInflater inflater = LayoutInflater.from(requireContext());
            rootView = inflater.inflate(R.layout.fragment_detail, container, false);
            container.addView(rootView);
            
            // 隐藏底部导航栏
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).hideBottomNavigation();
            }
            
            // 重新绑定视图和数据
            initViews(rootView);
            if (movie != null) {
                displayMovie();
                displayProgress(currentProgress);
                updateFavoriteButton();
                progressLoading.setVisibility(View.GONE);
                contentContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void initViews(View view) {
        progressLoading = view.findViewById(R.id.progress_loading);
        contentContainer = view.findViewById(R.id.content_container);
        toolbar = view.findViewById(R.id.toolbar);
        ivPoster = view.findViewById(R.id.iv_poster);
        tvTitle = view.findViewById(R.id.tv_title);
        tvOriginalTitle = view.findViewById(R.id.tv_original_title);
        tvYear = view.findViewById(R.id.tv_year);
        tvDuration = view.findViewById(R.id.tv_duration);
        tvRating = view.findViewById(R.id.tv_rating);
        tvDirector = view.findViewById(R.id.tv_director);
        tvActors = view.findViewById(R.id.tv_actors);
        tvPlot = view.findViewById(R.id.tv_plot);
        btnPlay = view.findViewById(R.id.btn_play);
        btnFavorite = view.findViewById(R.id.btn_favorite);
        cardProgress = view.findViewById(R.id.card_progress);
        tvProgress = view.findViewById(R.id.tv_progress);
        progressBar = view.findViewById(R.id.progress_bar);

        // 设置 Toolbar
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            activity.setSupportActionBar(toolbar.getToolbar());
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }

        // 设置标题和返回按钮
        toolbar.setTitle("详情");
        toolbar.setShowBack(true);
        toolbar.setOnBackClickListener(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).performRealBack();
            }
        });

        btnPlay.setOnClickListener(v -> playMovie());
        btnFavorite.setOnClickListener(v -> toggleFavorite());
    }

    private void initData() {
        repository = new MovieRepository(requireContext());
    }

    private void loadMovie() {
        // 显示加载进度条，隐藏内容
        progressLoading.setVisibility(View.VISIBLE);
        contentContainer.setVisibility(View.GONE);

        new Thread(() -> {
            movie = repository.getMovieById(movieId);
            isFavorite = repository.isFavorite(movieId);
            currentProgress = repository.getWatchProgress(movieId);

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (!isAdded()) return;

                if (movie != null) {
                    // 隐藏加载进度条
                    progressLoading.setVisibility(View.GONE);

                    // 显示内容（一次性渲染所有数据）
                    displayMovie();
                    displayProgress(currentProgress);
                    updateFavoriteButton();

                    contentContainer.setAlpha(0f);
                    contentContainer.setVisibility(View.VISIBLE);
                    contentContainer.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start();
                } else {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).performRealBack();
                    }
                }
            });
        }).start();
    }

    private void displayMovie() {
        tvTitle.setText(movie.getTitle());

        if (StringUtils.isNotEmpty(movie.getOriginalTitle())) {
            tvOriginalTitle.setVisibility(View.VISIBLE);
            tvOriginalTitle.setText(movie.getOriginalTitle());
        } else {
            tvOriginalTitle.setVisibility(View.GONE);
        }

        if (movie.getYear() > 0) {
            tvYear.setText(String.valueOf(movie.getYear()));
        } else {
            tvYear.setText("未知年份");
        }

        if (movie.getDuration() > 0) {
            tvDuration.setText(FileUtils.formatDurationMinutes(movie.getDuration()));
        } else {
            tvDuration.setText("未知时长");
        }

        if (movie.getRating() > 0) {
            tvRating.setText(StringUtils.formatRating(movie.getRating()));
        } else {
            tvRating.setText("暂无评分");
        }

        if (StringUtils.isNotEmpty(movie.getDirector())) {
            rootView.findViewById(R.id.director_container).setVisibility(View.VISIBLE);
            tvDirector.setText(movie.getDirector());
        } else {
            rootView.findViewById(R.id.director_container).setVisibility(View.GONE);
        }

        List<String> actors = movie.getActorList();
        if (!actors.isEmpty()) {
            rootView.findViewById(R.id.actors_container).setVisibility(View.VISIBLE);
            tvActors.setText(StringUtils.join(actors, ", "));
        } else {
            rootView.findViewById(R.id.actors_container).setVisibility(View.GONE);
        }

        if (StringUtils.isNotEmpty(movie.getPlot())) {
            tvPlot.setText(movie.getPlot());
        } else {
            tvPlot.setText(R.string.movie_no_plot);
        }

        // 加载海报
        String localThumb = movie.getLocalThumbPath();
        String localPoster = movie.getLocalPosterPath();
        boolean thumbExists = localThumb != null && !localThumb.isEmpty() && new File(localThumb).exists();
        boolean posterExists = localPoster != null && !localPoster.isEmpty() && new File(localPoster).exists();
        
        // 横屏模式使用 poster，竖屏模式使用 thumb/detailPoster
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            // 横屏：优先使用 poster
            if (posterExists) {
                Glide.with(requireContext())
                    .load(new File(localPoster))
                    .placeholder(R.drawable.bg_poster_placeholder)
                    .error(R.drawable.bg_poster_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(ivPoster);
            } else if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
                SmbImageLoader.loadPoster(requireContext(), movie, ivPoster);
            } else if (thumbExists) {
                Glide.with(requireContext())
                    .load(new File(localThumb))
                    .placeholder(R.drawable.bg_poster_placeholder)
                    .error(R.drawable.bg_poster_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(ivPoster);
            } else if (movie.getThumbPath() != null && !movie.getThumbPath().isEmpty()) {
                SmbImageLoader.loadDetailPoster(requireContext(), movie, ivPoster);
            } else {
                Glide.with(requireContext())
                    .load(R.drawable.bg_poster_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(ivPoster);
            }
        } else {
            // 竖屏：优先使用 thumb/detailPoster
            if (thumbExists) {
                Glide.with(requireContext())
                    .load(new File(localThumb))
                    .placeholder(R.drawable.bg_poster_placeholder)
                    .error(R.drawable.bg_poster_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(ivPoster);
            } else if (movie.getThumbPath() != null && !movie.getThumbPath().isEmpty()) {
                SmbImageLoader.loadDetailPoster(requireContext(), movie, ivPoster);
            } else if (posterExists) {
                Glide.with(requireContext())
                    .load(new File(localPoster))
                    .placeholder(R.drawable.bg_poster_placeholder)
                    .error(R.drawable.bg_poster_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(ivPoster);
            } else if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
                SmbImageLoader.loadPoster(requireContext(), movie, ivPoster);
            } else {
                Glide.with(requireContext())
                    .load(R.drawable.bg_poster_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade(300))
                    .into(ivPoster);
            }
        }
    }

    private void displayProgress(WatchProgress progress) {
        if (progress != null && progress.getPercentage() > 0 && !progress.isCompleted()) {
            cardProgress.setVisibility(View.VISIBLE);
            tvProgress.setText(String.format(Locale.US, "已观看 %d%%", progress.getPercentage()));
            progressBar.setProgress(progress.getPercentage());
            btnPlay.setText(R.string.resume_play);
        } else {
            cardProgress.setVisibility(View.GONE);
            btnPlay.setText(R.string.play);
        }
    }

    private void updateFavoriteButton() {
        if (btnFavorite != null) {
            btnFavorite.setIconResource(R.drawable.ic_favorite);
            if (isFavorite) {
                btnFavorite.setIconTintResource(R.color.iosBlue);
            } else {
                btnFavorite.setIconTintResource(R.color.iosGray);
            }
        }
    }

    private void playMovie() {
        if (movie == null) return;
        Intent intent = new Intent(getContext(), ExoPlayerActivity.class);
        intent.putExtra(ExoPlayerActivity.EXTRA_MOVIE_ID, movie.getId());
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

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (isAdded()) {
                    updateFavoriteButton();
                }
            });
        }).start();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (movieId != null) {
            new Thread(() -> {
                currentProgress = repository.getWatchProgress(movieId);

                if (!isAdded()) return;

                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        displayProgress(currentProgress);
                    }
                });
            }).start();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (repository != null) {
            repository.close();
        }
    }
}
