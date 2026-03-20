package com.example.nasmovie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.ui.adapter.FavoriteAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 收藏 Fragment
 */
public class FavoritesFragment extends Fragment implements
        FavoriteAdapter.OnMovieClickListener,
        FavoriteAdapter.OnMovieLongClickListener {

    private com.example.nasmovie.view.NasToolbar toolbar;
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private View bottomActions;
    private MaterialButton btnDelete;
    private EditText etSearch;

    private FavoriteAdapter adapter;
    private MovieRepository repository;
    private List<Movie> allFavorites = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        initData();
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recycler_view_favorites);
        emptyView = view.findViewById(R.id.empty_view);
        bottomActions = view.findViewById(R.id.bottom_actions);
        btnDelete = view.findViewById(R.id.btn_delete);
        etSearch = view.findViewById(R.id.et_search);

        // 设置 Toolbar
        if (toolbar != null) {
            toolbar.setTitle("我的收藏");
            toolbar.setShowBack(false);
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity != null) {
                activity.setSupportActionBar(toolbar.getToolbar());
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
                }
            }
        }

        // 根据屏幕宽度计算列数，卡片宽度约 120dp
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int maxWidth = (int) (600 * metrics.density);
        int desiredCardWidth;
        if (screenWidth > maxWidth) {
            // 平板设备：卡片宽度 240dp
            desiredCardWidth = (int) (240 * metrics.density);
        } else {
            // 手机设备：卡片宽度约 120dp
            desiredCardWidth = (int) (120 * metrics.density);
        }
        int spanCount = Math.max(2, screenWidth / desiredCardWidth);
        
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));
        adapter = new FavoriteAdapter();
        adapter.setOnMovieClickListener(this);
        adapter.setOnMovieLongClickListener(this);
        recyclerView.setAdapter(adapter);

        btnDelete.setOnClickListener(v -> deleteSelected());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFavorites(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void initData() {
        repository = new MovieRepository(requireContext());
        loadFavorites();
    }

    private void loadFavorites() {
        new Thread(() -> {
            allFavorites = repository.getFavoriteMovies();
            requireActivity().runOnUiThread(() -> {
                adapter.setMovies(allFavorites);
                updateEmptyView();
            });
        }).start();
    }

    private void filterFavorites(String query) {
        if (query == null || query.trim().isEmpty()) {
            adapter.setMovies(allFavorites);
        } else {
            List<Movie> filtered = new ArrayList<>();
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            for (Movie movie : allFavorites) {
                if (movie.getTitle().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    filtered.add(movie);
                }
            }
            adapter.setMovies(filtered);
        }
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (adapter.getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void deleteSelected() {
        Set<String> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) {
            Toast.makeText(getContext(), "请选择要删除的收藏", Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle("删除收藏")
                .setMessage("确定要删除选中的 " + selectedIds.size() + " 部电影吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    new Thread(() -> {
                        for (String movieId : selectedIds) {
                            repository.removeFavorite(movieId);
                        }
                        requireActivity().runOnUiThread(() -> {
                            allFavorites.removeIf(m -> selectedIds.contains(m.getId()));
                            adapter.setSelectionMode(false);
                            adapter.setMovies(allFavorites);
                            bottomActions.setVisibility(View.GONE);
                            updateEmptyView();
                            Toast.makeText(getContext(), "已删除 " + selectedIds.size() + " 部收藏", Toast.LENGTH_SHORT).show();
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    public void onMovieClick(Movie movie, int position) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openDetail(movie.getId());
        }
    }

    @Override
    public boolean onMovieLongClick(Movie movie, int position) {
        if (!adapter.isSelectionMode()) {
            adapter.setSelectionMode(true);
            adapter.toggleSelection(movie.getId());
            bottomActions.setVisibility(View.VISIBLE);
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFavorites();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadFavorites();
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
