package com.example.nasmovie.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.view.MovieCard;

import java.util.ArrayList;
import java.util.List;

/**
 * 主内容适配器 - 合并横向分类和网格列表
 */
public class MainContentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_SECTION = 0;
    public static final int TYPE_GRID_HEADER = 1;
    public static final int TYPE_GRID_ITEM = 2;
    public static final int TYPE_HEADER = 3;

    private static final int TYPE_SECTION_PRIVATE = 0;
    private static final int TYPE_GRID_HEADER_PRIVATE = 1;
    private static final int TYPE_GRID_ITEM_PRIVATE = 2;
    private static final int TYPE_HEADER_PRIVATE = 3;

    private List<SectionData> sections = new ArrayList<>();
    private List<Movie> gridMovies = new ArrayList<>();
    private OnMovieClickListener listener;
    private MovieRepository repository;
    private View headerView;

    public void setHeaderView(View headerView) {
        this.headerView = headerView;
        notifyDataSetChanged();
    }

    public View getHeaderView() {
        return headerView;
    }

    public void setRepository(MovieRepository repository) {
        this.repository = repository;
    }

    public interface OnMovieClickListener {
        void onMovieClick(Movie movie);
    }

    public void setOnMovieClickListener(OnMovieClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<SectionData> sections, List<Movie> gridMovies) {
        this.sections = sections != null ? sections : new ArrayList<>();
        this.gridMovies = gridMovies != null ? gridMovies : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        int offset = headerView != null ? 1 : 0;
        int sectionCount = sections.size();
        int adjustedPosition = position - offset;
        if (position == 0 && headerView != null) {
            return TYPE_HEADER_PRIVATE;
        }
        if (adjustedPosition < sectionCount) {
            return TYPE_SECTION_PRIVATE;
        } else if (adjustedPosition == sectionCount) {
            return TYPE_GRID_HEADER_PRIVATE;
        } else {
            return TYPE_GRID_ITEM_PRIVATE;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER_PRIVATE) {
            return new HeaderViewHolder(headerView);
        } else if (viewType == TYPE_SECTION_PRIVATE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_main_section, parent, false);
            return new SectionViewHolder(view);
        } else if (viewType == TYPE_GRID_HEADER_PRIVATE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_section_header, parent, false);
            return new GridHeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_movie_grid, parent, false);
            return new GridItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            return;
        }
        int offset = headerView != null ? 1 : 0;
        int adjustedPosition = position - offset;
        int sectionCount = sections.size();

        if (holder instanceof SectionViewHolder) {
            ((SectionViewHolder) holder).bind(sections.get(adjustedPosition));
        } else if (holder instanceof GridHeaderViewHolder) {
            ((GridHeaderViewHolder) holder).bind(R.string.section_all_movies);
        } else if (holder instanceof GridItemViewHolder) {
            int gridIndex = adjustedPosition - sectionCount - 1;
            ((GridItemViewHolder) holder).bind(gridMovies.get(gridIndex));
        }
    }

    @Override
    public int getItemCount() {
        int offset = headerView != null ? 1 : 0;
        return offset + sections.size() + 1 + gridMovies.size();
    }

    /**
     * 分类数据
     */
    public static class SectionData {
        public final String title;
        public final List<Movie> movies;

        public SectionData(String title, List<Movie> movies) {
            this.title = title;
            this.movies = movies;
        }
    }

    // ==================== ViewHolders ====================

    /**
     * Header 区域（轮播图）
     */
    class HeaderViewHolder extends RecyclerView.ViewHolder {
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    /**
     * 横向分类区域
     */
    class SectionViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final RecyclerView recyclerView;
        private HorizontalMovieAdapter adapter;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.text_section_title);
            recyclerView = itemView.findViewById(R.id.recycler_view_horizontal);

            recyclerView.setLayoutManager(
                    new LinearLayoutManager(itemView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            adapter = new HorizontalMovieAdapter();
            adapter.setOnMovieClickListener(movie -> {
                if (listener != null) listener.onMovieClick(movie);
            });
            recyclerView.setAdapter(adapter);
        }

        void bind(SectionData section) {
            titleView.setText(section.title);
            adapter.setMovies(section.movies);
        }
    }

    /**
     * 网格标题
     */
    class GridHeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;

        GridHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.text_section_title);
            itemView.findViewById(R.id.text_view_more).setVisibility(View.GONE);
        }

        void bind(int titleRes) {
            titleView.setText(itemView.getContext().getString(titleRes));
        }
    }

    /**
     * 网格项
     */
    class GridItemViewHolder extends RecyclerView.ViewHolder {
        private final MovieCard movieCard;
        private Movie currentMovie;

        GridItemViewHolder(@NonNull View itemView) {
            super(itemView);
            movieCard = itemView.findViewById(R.id.movie_card);

            itemView.setOnClickListener(v -> {
                if (currentMovie != null && listener != null) {
                    listener.onMovieClick(currentMovie);
                }
            });
        }

        void bind(Movie movie) {
            this.currentMovie = movie;
            movieCard.setMode(MovieCard.CardMode.GRID);
            movieCard.loadMovie(movie, movie.getProgress());
        }
    }

    // ==================== 横向电影适配器 ====================

    class HorizontalMovieAdapter extends RecyclerView.Adapter<HorizontalMovieAdapter.ViewHolder> {
        private List<Movie> movies = new ArrayList<>();
        private OnMovieClickListener listener;

        void setOnMovieClickListener(OnMovieClickListener listener) {
            this.listener = listener;
        }

        void setMovies(List<Movie> movies) {
            this.movies = movies != null ? movies : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_movie_horizontal_scroll, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.bind(movies.get(position));
        }

        @Override
        public int getItemCount() {
            return movies.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final MovieCard movieCard;
            private Movie currentMovie;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                movieCard = itemView.findViewById(R.id.movie_card);

                itemView.setOnClickListener(v -> {
                    if (currentMovie != null && listener != null) {
                        listener.onMovieClick(currentMovie);
                    }
                });
            }

            void bind(Movie movie) {
                this.currentMovie = movie;
                movieCard.setMode(MovieCard.CardMode.HORIZONTAL);
                movieCard.loadMovie(movie, movie.getProgress());
            }
        }
    }
}
