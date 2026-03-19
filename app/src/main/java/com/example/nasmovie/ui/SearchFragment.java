package com.example.nasmovie.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.nasmovie.R;
import com.example.nasmovie.data.local.SearchHistoryManager;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.ui.adapter.SearchResultAdapter;
import com.example.nasmovie.view.NasToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索 Fragment
 */
public class SearchFragment extends Fragment implements
        SearchResultAdapter.OnMovieClickListener {

    private EditText editSearch;
    private ImageView btnClear;
    private ChipGroup chipGroupHistory;
    private RecyclerView recyclerViewResults;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private View emptyView;
    private View searchHistoryContainer;
    private NasToolbar toolbar;

    private SearchResultAdapter resultAdapter;
    private MovieRepository repository;
    private SearchHistoryManager historyManager;
    private List<Movie> allMovies = new ArrayList<>();
    private List<Movie> searchResults = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        initData();
        setupSearchHistory();
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        editSearch = view.findViewById(R.id.edit_search);
        btnClear = view.findViewById(R.id.btn_clear);
        chipGroupHistory = view.findViewById(R.id.chip_group_history);
        recyclerViewResults = view.findViewById(R.id.recycler_view_results);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.empty_view);
        searchHistoryContainer = view.findViewById(R.id.search_history_container);

        // 设置 Toolbar
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            activity.setSupportActionBar(toolbar.getToolbar());
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }

        // 设置标题和返回按钮
        toolbar.setTitle("搜索");
        toolbar.setShowBack(true);
        toolbar.setOnBackClickListener(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).performRealBack();
            }
        });

        // 搜索结果网格 - 3 列
        resultAdapter = new SearchResultAdapter();
        resultAdapter.setOnMovieClickListener(this);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3);
        recyclerViewResults.setLayoutManager(gridLayoutManager);
        recyclerViewResults.setAdapter(resultAdapter);

        // 搜索输入监听
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 搜索按钮监听
        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(editSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        // 清除按钮
        btnClear.setOnClickListener(v -> {
            editSearch.setText("");
            showSearchHistory();
        });

        // 清空历史按钮
        view.findViewById(R.id.btn_clear_history).setOnClickListener(v -> {
            historyManager.clearHistory();
            setupSearchHistory();
        });

        // 下拉刷新
        swipeRefreshLayout.setOnRefreshListener(() -> {
            String query = editSearch.getText().toString().trim();
            if (!query.isEmpty()) {
                performSearch(query);
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);
    }

    private void initData() {
        repository = new MovieRepository(requireContext());
        historyManager = new SearchHistoryManager(requireContext());
        loadAllMovies();
    }

    private void loadAllMovies() {
        new Thread(() -> {
            allMovies = repository.getAllMovies();
            requireActivity().runOnUiThread(() -> showSearchHistory());
        }).start();
    }

    private void setupSearchHistory() {
        chipGroupHistory.removeAllViews();
        List<String> searchHistory = historyManager.getSearchHistory();

        for (String history : searchHistory) {
            Chip chip = new Chip(requireContext());
            chip.setText(history);
            chip.setChipBackgroundColorResource(R.color.colorSurface);
            chip.setChipStrokeWidth(1f);
            chip.setChipStrokeColorResource(R.color.divider);
            chip.setTextColor(requireContext().getColor(R.color.textPrimary));
            chip.setOnClickListener(v -> {
                editSearch.setText(history);
                performSearch(history);
            });
            chipGroupHistory.addView(chip);
        }
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            showSearchHistory();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        searchHistoryContainer.setVisibility(View.GONE);
        swipeRefreshLayout.setRefreshing(false);

        new Thread(() -> {
            // 保存搜索记录
            historyManager.addSearchRecord(query);
            searchResults = repository.searchMovies(query);
            requireActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                resultAdapter.setMovies(searchResults);

                // 刷新搜索历史显示（包含刚添加的记录）
                setupSearchHistory();

                if (searchResults.isEmpty()) {
                    showEmptyState(query);
                } else {
                    showResults();
                }
            });
        }).start();
    }

    private void showSearchHistory() {
        recyclerViewResults.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        searchHistoryContainer.setVisibility(View.VISIBLE);
    }

    private void showResults() {
        recyclerViewResults.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        searchHistoryContainer.setVisibility(View.GONE);
    }

    private void showEmptyState(String query) {
        recyclerViewResults.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
        searchHistoryContainer.setVisibility(View.GONE);

        TextView tvEmptyTitle = emptyView.findViewById(R.id.tv_empty_title);
        TextView tvEmptyDesc = emptyView.findViewById(R.id.tv_empty_desc);

        tvEmptyTitle.setText(getString(R.string.search_no_result));
        tvEmptyDesc.setText("未找到包含 \"" + query + "\" 的电影");
    }

    @Override
    public void onMovieClick(Movie movie) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openDetail(movie.getId());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 刷新搜索历史
        setupSearchHistory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (repository != null) {
            repository.close();
        }
    }
}
