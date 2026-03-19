package com.example.nasmovie.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.util.SmbImageLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * 特色电影轮播适配器
 */
public class FeaturedMovieAdapter extends RecyclerView.Adapter<FeaturedMovieAdapter.ViewHolder> {

    private List<Movie> movies = new ArrayList<>();
    private OnFeaturedClickListener listener;

    public interface OnFeaturedClickListener {
        void onFeaturedClick(Movie movie);
    }

    public void setOnFeaturedClickListener(OnFeaturedClickListener listener) {
        this.listener = listener;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies != null ? movies : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_featured_movie, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // 使用取模运算实现循环
        int actualPosition = movies.isEmpty() ? 0 : position % movies.size();
        Movie movie = movies.get(actualPosition);
        holder.bind(movie, actualPosition);
    }

    // 虚拟项目数量，用于实现无限循环效果（使用较小的值避免潜在问题）
    private static final int VIRTUAL_COUNT = 10000;

    @Override
    public int getItemCount() {
        // 返回虚拟数量来实现无限循环效果
        return movies.isEmpty() ? 0 : VIRTUAL_COUNT;
    }

    /**
     * 获取实际的电影数量
     */
    public int getActualItemCount() {
        return movies.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imagePoster;
        private final TextView textRating;
        private final TextView textTitle;
        private final TextView textYearGenres;
        private Movie currentMovie;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imagePoster = itemView.findViewById(R.id.image_poster);
            textRating = itemView.findViewById(R.id.text_rating);
            textTitle = itemView.findViewById(R.id.text_title);
            textYearGenres = itemView.findViewById(R.id.text_year_genres);

            itemView.setOnClickListener(v -> {
                if (currentMovie != null && listener != null) {
                    listener.onFeaturedClick(currentMovie);
                }
            });
        }

        void bind(Movie movie, int actualPosition) {
            this.currentMovie = movie;
            // 加载详情海报（使用localThumbPath）
            SmbImageLoader.loadDetailPoster(itemView.getContext(), movie, imagePoster);

            // 设置评分
            if (movie.getRating() > 0) {
                textRating.setText(String.format("%.1f", movie.getRating()));
                textRating.setVisibility(View.VISIBLE);
            } else {
                textRating.setVisibility(View.GONE);
            }

            // 设置标题
            textTitle.setText(movie.getTitle());

            // 设置年份和类型
            StringBuilder info = new StringBuilder();
            if (movie.getYear() > 0) {
                info.append(movie.getYear());
            }
            List<String> genres = movie.getGenreList();
            if (!genres.isEmpty()) {
                if (info.length() > 0) info.append(" • ");
                info.append(String.join(" / ", genres.subList(0, Math.min(2, genres.size()))));
            }
            textYearGenres.setText(info.toString());
        }
    }
}
