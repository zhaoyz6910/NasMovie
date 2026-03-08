package com.example.nasmovie.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.util.SmbImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 电影列表适配器
 */
public class MovieAdapter extends RecyclerView.Adapter<MovieAdapter.ViewHolder> {

    private List<Movie> movies = new ArrayList<>();
    private OnItemClickListener onItemClickListener;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_movie, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Movie movie = movies.get(position);
        holder.bind(movie);
    }

    @Override
    public int getItemCount() {
        return movies.size();
    }

    /**
     * 设置数据
     */
    public void setMovies(List<Movie> movies) {
        this.movies = movies != null ? movies : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * 获取数据
     */
    public List<Movie> getMovies() {
        return movies;
    }

    /**
     * 设置点击监听器
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    /**
     * ViewHolder
     */
    class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivPoster;
        private final TextView tvTitle;
        private final TextView tvRating;
        private final ProgressBar progressBar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster = itemView.findViewById(R.id.iv_poster);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvRating = itemView.findViewById(R.id.tv_rating);
            progressBar = itemView.findViewById(R.id.progress_bar);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onItemClickListener != null) {
                    onItemClickListener.onItemClick(movies.get(position));
                }
            });
        }

        public void bind(Movie movie) {
            // 设置标题
            tvTitle.setText(movie.getTitle());

            // 设置评分
            if (movie.getRating() > 0) {
                tvRating.setVisibility(View.VISIBLE);
                tvRating.setText(String.format("%.1f", movie.getRating()));
            } else {
                tvRating.setVisibility(View.GONE);
            }

            // 加载海报
            if (movie.getPosterPath() != null && !movie.getPosterPath().isEmpty()) {
                SmbImageLoader.loadPoster(itemView.getContext(), movie, ivPoster);
            } else {
                Glide.with(itemView.getContext())
                    .load(R.drawable.bg_poster_placeholder)
                    .into(ivPoster);
            }

            // 加载观看进度
            loadWatchProgress(movie);
        }

        private void loadWatchProgress(Movie movie) {
            // 在后台线程查询观看进度
            new Thread(() -> {
                try {
                    WatchProgress progress = NASMovieApp.getInstance().getDatabase()
                            .watchProgressDao().getByMovieId(movie.getId());

                    if (progress != null && progress.getDuration() > 0) {
                        int progressPercent = (int) ((progress.getPosition() * 100) / progress.getDuration());
                        // 确保进度在有效范围内
                        progressPercent = Math.max(0, Math.min(100, progressPercent));

                        final int finalProgress = progressPercent;
                        itemView.post(() -> {
                            progressBar.setProgress(finalProgress);
                            progressBar.setVisibility(View.VISIBLE);
                        });
                    } else {
                        itemView.post(() -> progressBar.setVisibility(View.GONE));
                    }
                } catch (Exception e) {
                    itemView.post(() -> progressBar.setVisibility(View.GONE));
                }
            }).start();
        }
    }

    /**
     * 点击监听器接口
     */
    public interface OnItemClickListener {
        void onItemClick(Movie movie);
    }
}