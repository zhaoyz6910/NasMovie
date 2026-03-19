package com.example.nasmovie.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.view.MovieCard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 收藏电影适配器
 */
public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.ViewHolder> {

    private List<Movie> movies = new ArrayList<>();
    private Set<String> selectedIds = new HashSet<>();
    private boolean selectionMode = false;

    private OnMovieClickListener clickListener;
    private OnMovieLongClickListener longClickListener;

    public interface OnMovieClickListener {
        void onMovieClick(Movie movie, int position);
    }

    public interface OnMovieLongClickListener {
        boolean onMovieLongClick(Movie movie, int position);
    }

    public void setOnMovieClickListener(OnMovieClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnMovieLongClickListener(OnMovieLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setMovies(List<Movie> movies) {
        this.movies = movies;
        notifyDataSetChanged();
    }

    public List<Movie> getMovies() {
        return movies;
    }

    public void setSelectionMode(boolean enabled) {
        this.selectionMode = enabled;
        if (!enabled) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void toggleSelection(String movieId) {
        if (selectedIds.contains(movieId)) {
            selectedIds.remove(movieId);
        } else {
            selectedIds.add(movieId);
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_movie, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Movie movie = movies.get(position);
        holder.bind(movie, selectedIds.contains(movie.getId()), selectionMode);
    }

    @Override
    public int getItemCount() {
        return movies.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final MovieCard movieCard;

        ViewHolder(View itemView) {
            super(itemView);
            movieCard = itemView.findViewById(R.id.movie_card);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Movie movie = movies.get(position);
                    if (selectionMode) {
                        toggleSelection(movie.getId());
                    } else if (clickListener != null) {
                        clickListener.onMovieClick(movie, position);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    Movie movie = movies.get(position);
                    if (longClickListener != null) {
                        return longClickListener.onMovieLongClick(movie, position);
                    }
                }
                return false;
            });
        }

        void bind(Movie movie, boolean isSelected, boolean selectionMode) {
            movieCard.setMode(MovieCard.CardMode.FAVORITE);
            movieCard.loadMovie(movie);
            movieCard.setSelected(isSelected && selectionMode);
        }
    }
}
