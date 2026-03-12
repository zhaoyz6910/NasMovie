package com.example.nasmovie.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;

import java.io.File;

/**
 * 通用电影卡片组件
 * 支持多种显示模式：网格、列表、横向、收藏、搜索
 */
public class MovieCard extends FrameLayout {

    // 卡片模式
    public enum CardMode {
        GRID,      // 网格模式（带评分徽章、年份时长、进度条）
        LIST,      // 列表模式（简洁）
        HORIZONTAL,// 横向滚动（带选中状态）
        FAVORITE,  // 收藏模式
        SEARCH     // 搜索结果（完整信息）
    }

    private ImageView ivPoster;
    private TextView tvTitle;
    private TextView tvRating;
    private TextView tvYear;
    private TextView tvDuration;
    private ProgressBar progressWatch;
    private View ratingBadge;
    private View selectionOverlay;
    private View divider;
    private LinearLayout infoContainer;

    private CardMode currentMode = CardMode.GRID;

    public MovieCard(Context context) {
        super(context);
        init(context);
    }

    public MovieCard(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MovieCard(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_movie_card, this, true);

        ivPoster = findViewById(R.id.iv_poster);
        tvTitle = findViewById(R.id.tv_title);
        tvRating = findViewById(R.id.tv_rating);
        tvYear = findViewById(R.id.tv_year);
        tvDuration = findViewById(R.id.tv_duration);
        progressWatch = findViewById(R.id.progress_watch);
        ratingBadge = findViewById(R.id.rating_badge);
        selectionOverlay = findViewById(R.id.selection_overlay);
        divider = findViewById(R.id.divider);
        infoContainer = findViewById(R.id.info_container);
    }

    /**
     * 加载电影数据（不带进度）
     */
    public void loadMovie(Movie movie) {
        loadMovie(movie, -1);
    }

    /**
     * 加载电影数据（带进度百分比，0-100，-1 表示不显示进度）
     */
    public void loadMovie(Movie movie, int progress) {
        if (movie == null) return;

        // 使用 SmbImageLoader 加载海报，它会自动处理本地路径、SMB 下载 and 缓存
        com.example.nasmovie.util.SmbImageLoader.loadPoster(getContext(), movie, ivPoster);

        // 设置标题
        tvTitle.setText(movie.getTitle());

        // 设置评分
        if (movie.getRating() > 0) {
            tvRating.setText(String.format("%.1f", movie.getRating()));
        } else {
            tvRating.setText("N/A");
        }

        // 设置年份
        if (movie.getYear() > 0) {
            tvYear.setText(String.valueOf(movie.getYear()));
            tvYear.setVisibility(View.VISIBLE);
        } else {
            tvYear.setVisibility(View.GONE);
        }

        // 设置时长
        if (movie.getDuration() > 0) {
            tvDuration.setText(getDurationText(movie.getDuration()));
            tvDuration.setVisibility(View.VISIBLE);
            if (divider != null) divider.setVisibility(View.VISIBLE);
        } else {
            tvDuration.setVisibility(View.GONE);
            if (divider != null) divider.setVisibility(View.GONE);
        }

        applyMode();

        // 最后根据进度数据决定进度条显隐，这会覆盖 applyMode 中的默认设置
        if (progressWatch != null) {
            if (progress >= 0 && progress < 100) {
                progressWatch.setProgress(progress);
                progressWatch.setVisibility(View.VISIBLE);
            } else {
                progressWatch.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 设置观看进度
     */
    public void setWatchProgress(int progress) {
        if (progressWatch != null) {
            if (progress >= 0 && progress < 100) {
                progressWatch.setProgress(progress);
                progressWatch.setVisibility(View.VISIBLE);
            } else {
                progressWatch.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 设置卡片模式
     */
    public void setMode(CardMode mode) {
        this.currentMode = mode;
        applyMode();
    }

    private void applyMode() {
        switch (currentMode) {
            case GRID:
                // 网格模式：显示评分徽章、年份、时长
                if (ratingBadge != null) ratingBadge.setVisibility(View.VISIBLE);
                if (tvYear != null) tvYear.setVisibility(tvYear.getText().toString().isEmpty() ? View.GONE : View.VISIBLE);
                if (tvDuration != null) tvDuration.setVisibility(tvDuration.getText().toString().isEmpty() ? View.GONE : View.VISIBLE);
                if (divider != null) divider.setVisibility(View.VISIBLE);
                if (selectionOverlay != null) selectionOverlay.setVisibility(View.GONE);
                break;

            case LIST:
                // 列表模式：简洁显示
                if (ratingBadge != null) ratingBadge.setVisibility(View.GONE);
                if (tvDuration != null) tvDuration.setVisibility(View.GONE);
                if (divider != null) divider.setVisibility(View.GONE);
                if (selectionOverlay != null) selectionOverlay.setVisibility(View.GONE);
                break;

            case HORIZONTAL:
                // 横向模式：显示评分徽章、标题
                if (ratingBadge != null) ratingBadge.setVisibility(View.VISIBLE);
                if (tvYear != null) tvYear.setVisibility(View.GONE);
                if (tvDuration != null) tvDuration.setVisibility(View.GONE);
                if (divider != null) divider.setVisibility(View.GONE);
                if (selectionOverlay != null) selectionOverlay.setVisibility(View.GONE);
                break;

            case FAVORITE:
                // 收藏模式：显示评分、标题、选中状态
                if (ratingBadge != null) ratingBadge.setVisibility(View.GONE);
                if (tvYear != null) tvYear.setVisibility(View.GONE);
                if (tvDuration != null) tvDuration.setVisibility(View.GONE);
                if (divider != null) divider.setVisibility(View.GONE);
                if (selectionOverlay != null) selectionOverlay.setVisibility(View.GONE);
                break;

            case SEARCH:
                // 搜索模式：完整信息
                if (ratingBadge != null) ratingBadge.setVisibility(View.VISIBLE);
                if (tvYear != null) tvYear.setVisibility(tvYear.getText().toString().isEmpty() ? View.GONE : View.VISIBLE);
                if (tvDuration != null) tvDuration.setVisibility(tvDuration.getText().toString().isEmpty() ? View.GONE : View.VISIBLE);
                if (divider != null) divider.setVisibility(View.VISIBLE);
                if (selectionOverlay != null) selectionOverlay.setVisibility(View.GONE);
                break;
        }
    }

    /**
     * 设置选中状态（用于横向滚动多选）
     */
    public void setSelected(boolean selected) {
        if (selectionOverlay != null) {
            selectionOverlay.setVisibility(selected ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 获取标题 TextView（可用于自定义）
     */
    public TextView getTitleTextView() {
        return tvTitle;
    }

    /**
     * 获取海报 ImageView（可用于自定义）
     */
    public ImageView getPosterImageView() {
        return ivPoster;
    }

    /**
     * 格式化时长
     */
    private String getDurationText(int minutes) {
        return minutes + " 分钟";
    }
}
