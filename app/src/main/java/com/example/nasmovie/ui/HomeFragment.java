package com.example.nasmovie.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.viewpager2.widget.ViewPager2;

import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.ui.adapter.FeaturedMovieAdapter;
import com.example.nasmovie.ui.adapter.MainContentAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 首页 Fragment
 */
public class HomeFragment extends Fragment implements
        FeaturedMovieAdapter.OnFeaturedClickListener,
        MainContentAdapter.OnMovieClickListener {

    private ViewPager2 viewPagerFeatured;
    private RecyclerView recyclerViewMain;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private View emptyView;
    private View cardSearch;
    private com.example.nasmovie.view.NasToolbar toolbar;

    private FeaturedMovieAdapter featuredAdapter;
    private MainContentAdapter mainContentAdapter;

    private MovieRepository repository;
    private List<Movie> allMovies = new ArrayList<>();
    private boolean isDataLoaded = false;
    private MovieRepository.SortType currentSortType = MovieRepository.SortType.ADD_TIME_DESC;

    // 自动轮播相关
    private static final long AUTO_SLIDE_INTERVAL = 4000; // 4秒轮播一次
    private final android.os.Handler autoSlideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private AutoSlideRunnable autoSlideRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    private void startAutoSlide() {
        stopAutoSlide();
        autoSlideRunnable = new AutoSlideRunnable(this);
        autoSlideHandler.postDelayed(autoSlideRunnable, AUTO_SLIDE_INTERVAL);
    }

    private void stopAutoSlide() {
        if (autoSlideRunnable != null) {
            autoSlideHandler.removeCallbacks(autoSlideRunnable);
            autoSlideRunnable = null;
        }
    }

    /**
     * 静态内部类 Runnable，避免持有外部类引用导致内存泄漏
     */
    private static class AutoSlideRunnable implements Runnable {
        private final WeakReference<HomeFragment> fragmentRef;

        AutoSlideRunnable(HomeFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }

        @Override
        public void run() {
            HomeFragment fragment = fragmentRef.get();
            if (fragment == null || !fragment.isAdded()) return;

            if (fragment.viewPagerFeatured != null && fragment.featuredAdapter != null 
                    && fragment.featuredAdapter.getItemCount() > 0) {
                int currentItem = fragment.viewPagerFeatured.getCurrentItem();
                fragment.viewPagerFeatured.setCurrentItem(currentItem + 1, true);
                fragment.autoSlideHandler.postDelayed(this, AUTO_SLIDE_INTERVAL);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        initData();
        loadMovies();
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        viewPagerFeatured = view.findViewById(R.id.view_pager_featured);
        recyclerViewMain = view.findViewById(R.id.recycler_view_main);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.empty_view);
        cardSearch = view.findViewById(R.id.card_search);

        if (toolbar != null) {
            toolbar.setTitle(R.string.app_name);
            toolbar.setShowBack(false);
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity != null) {
                activity.setSupportActionBar(toolbar.getToolbar());
                if (activity.getSupportActionBar() != null) {
                    activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
                }
            }
        }

        featuredAdapter = new FeaturedMovieAdapter();
        featuredAdapter.setOnFeaturedClickListener(this);
        viewPagerFeatured.setAdapter(featuredAdapter);
        viewPagerFeatured.setOffscreenPageLimit(3);

        // 处理触摸事件，用户触摸时停止轮播
        viewPagerFeatured.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    stopAutoSlide();
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    startAutoSlide();
                }
            }
        });

        viewPagerFeatured.setPageTransformer(new ViewPager2.PageTransformer() {
            @Override
            public void transformPage(@NonNull View page, float position) {
                if (position == 0f) {
                    page.setScaleX(1f);
                    page.setScaleY(1f);
                    page.setAlpha(1f);
                    return;
                }

                float absPosition = Math.abs(position);
                if (absPosition <= 1f) {
                    float scale = 1.0f - 0.05f * absPosition;
                    page.setScaleX(scale);
                    page.setScaleY(scale);
                    page.setAlpha(1.0f - 0.2f * absPosition);
                } else {
                    page.setScaleX(0.95f);
                    page.setScaleY(0.95f);
                    page.setAlpha(0.3f);
                }
            }
        });

        mainContentAdapter = new MainContentAdapter();
        mainContentAdapter.setOnMovieClickListener(this);
        mainContentAdapter.setRepository(repository);
        
        // 根据屏幕宽度计算列数，平板设备卡片宽度 240dp
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
        
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), spanCount);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int viewType = mainContentAdapter.getItemViewType(position);
                return (viewType == MainContentAdapter.TYPE_HEADER ||
                        viewType == MainContentAdapter.TYPE_SECTION ||
                        viewType == MainContentAdapter.TYPE_GRID_HEADER) ? spanCount : 1;
            }
        });
        recyclerViewMain.setLayoutManager(gridLayoutManager);
        recyclerViewMain.setAdapter(mainContentAdapter);
        recyclerViewMain.setHasFixedSize(true);

        view.findViewById(R.id.btn_scan).setOnClickListener(v -> {
            // 跳转到服务器管理页面
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openServerManage();
            }
        });

        cardSearch.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSearch();
            }
        });

        // 下拉刷新
        swipeRefreshLayout.setColorSchemeResources(
                R.color.colorPrimary,
                R.color.colorAccent);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // 重新加载数据，刷新 ViewPager 的随机影片
            isDataLoaded = false;
            loadMovies();
        });
    }

    private void initData() {
        repository = new MovieRepository(requireContext());
    }

    private void loadMovies() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        new Thread(() -> {
            allMovies = repository.getAllMovies(currentSortType);

            List<Movie> featuredMovies = getRandomMovies(5);
            List<Movie> recentMovies = getRecentWatchedMovies(10);
            List<Movie> highRatedMovies = getHighRatedMovies(10);
            List<Movie> newestMovies = getNewestMovies(10);

            if (!isAdded()) return;

            androidx.fragment.app.FragmentActivity activity = getActivity();
            if (activity == null || activity.isFinishing()) return;

            activity.runOnUiThread(() -> {
                if (!isAdded()) return;

                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);

                if (allMovies.isEmpty()) {
                    showEmptyView();
                } else {
                    showContent(featuredMovies, recentMovies, highRatedMovies, newestMovies);
                    updateSortText();
                }
                // 标记数据已加载
                isDataLoaded = true;
            });
        }).start();
    }

    private void showEmptyView() {
        emptyView.setVisibility(View.VISIBLE);
        recyclerViewMain.setVisibility(View.GONE);
    }

    private void showContent(List<Movie> featuredMovies, List<Movie> recentMovies,
                            List<Movie> highRatedMovies, List<Movie> newestMovies) {
        emptyView.setVisibility(View.GONE);
        recyclerViewMain.setVisibility(View.VISIBLE);

        featuredAdapter.setMovies(featuredMovies);

        if (!featuredMovies.isEmpty()) {
            int startPosition = (1000 / featuredMovies.size()) * featuredMovies.size();
            viewPagerFeatured.setCurrentItem(startPosition, false);
            viewPagerFeatured.setVisibility(View.VISIBLE);

            updateViewPagerSize();

            if (mainContentAdapter.getHeaderView() == null) {
                LinearLayout headerContainer = new LinearLayout(getContext());
                headerContainer.setOrientation(LinearLayout.VERTICAL);
                headerContainer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));

                ViewGroup searchParent = (ViewGroup) cardSearch.getParent();
                if (searchParent != null) {
                    searchParent.removeView(cardSearch);
                }
                cardSearch.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (int) (48 * getResources().getDisplayMetrics().density));
                int margin16 = (int) (16 * getResources().getDisplayMetrics().density);
                int margin8 = (int) (8 * getResources().getDisplayMetrics().density);
                searchParams.setMargins(margin16, margin8, margin16, margin8);
                cardSearch.setLayoutParams(searchParams);
                headerContainer.addView(cardSearch);

                ViewGroup pagerParent = (ViewGroup) viewPagerFeatured.getParent();
                if (pagerParent != null) {
                    pagerParent.removeView(viewPagerFeatured);
                }
                viewPagerFeatured.setVisibility(View.VISIBLE);
                LinearLayout.LayoutParams pagerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (int) (180 * getResources().getDisplayMetrics().density));
                pagerParams.setMargins(0, margin8, 0, (int) (16 * getResources().getDisplayMetrics().density));
                viewPagerFeatured.setLayoutParams(pagerParams);
                headerContainer.addView(viewPagerFeatured);

                mainContentAdapter.setHeaderView(headerContainer);
            }
        } else {
            mainContentAdapter.setHeaderView(null);
        }

        List<MainContentAdapter.SectionData> sections = new ArrayList<>();

        if (!recentMovies.isEmpty()) {
            sections.add(new MainContentAdapter.SectionData(
                    getString(R.string.section_recent), recentMovies));
        }
        if (!highRatedMovies.isEmpty()) {
            sections.add(new MainContentAdapter.SectionData(
                    getString(R.string.section_high_rated), highRatedMovies));
        }
        if (!newestMovies.isEmpty()) {
            sections.add(new MainContentAdapter.SectionData(
                    getString(R.string.section_newest), newestMovies));
        }

        mainContentAdapter.setData(sections, allMovies);
        mainContentAdapter.setOnSortClickListener(() -> showSortDialog());
    }

    private List<Movie> getRandomMovies(int limit) {
        if (allMovies.isEmpty()) {
            return new ArrayList<>();
        }
        List<Movie> result = new ArrayList<>(allMovies);
        Collections.shuffle(result, new Random());
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    private List<Movie> getRecentWatchedMovies(int limit) {
        List<WatchProgress> recentProgress = repository.getRecentWatchProgress(limit);
        List<Movie> result = new ArrayList<>();
        for (WatchProgress progress : recentProgress) {
            Movie movie = repository.getMovieById(progress.getMovieId());
            if (movie != null) {
                movie.setProgress(progress.getPercentage());
                result.add(movie);
            }
        }
        return result;
    }

    private List<Movie> getHighRatedMovies(int limit) {
        List<Movie> result = new ArrayList<>();
        for (Movie movie : allMovies) {
            if (movie.getRating() >= 8.0f) {
                result.add(movie);
            }
        }
        Collections.sort(result, (m1, m2) -> Float.compare(m2.getRating(), m1.getRating()));
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    private List<Movie> getNewestMovies(int limit) {
        List<Movie> result = new ArrayList<>(allMovies);
        Collections.sort(result, (m1, m2) -> Long.compare(m2.getAddTime(), m1.getAddTime()));
        return result.size() > limit ? result.subList(0, limit) : result;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadMovies();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isDataLoaded) {
            loadMovies();
        }
        startAutoSlide();
        updateViewPagerSize();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoSlide();
    }
    
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateViewPagerSize();
        updateGridSpanCount();
    }
    
    private void updateViewPagerSize() {
        if (viewPagerFeatured == null || !isAdded()) return;
        
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int maxWidth = (int) (600 * getResources().getDisplayMetrics().density);
        float density = getResources().getDisplayMetrics().density;
        
        if (screenWidth > maxWidth) {
            int pagerHeight = (int) (maxWidth * 9f / 16f);
            LinearLayout.LayoutParams pagerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    pagerHeight);
            pagerParams.setMargins(0, (int) (8 * density), 0, (int) (16 * density));
            viewPagerFeatured.setLayoutParams(pagerParams);
            
            int padding = (screenWidth - maxWidth) / 2;
            viewPagerFeatured.setPadding(padding, 0, padding, 0);
        } else {
            viewPagerFeatured.setPadding((int) (32 * density), 0, (int) (32 * density), 0);
        }
        
        viewPagerFeatured.post(() -> {
            if (isAdded() && viewPagerFeatured != null) {
                viewPagerFeatured.requestLayout();
                viewPagerFeatured.invalidate();
            }
        });
    }
    
    private void updateGridSpanCount() {
        if (recyclerViewMain == null || !isAdded()) return;
        
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int maxWidth = (int) (600 * metrics.density);
        int desiredCardWidth;
        if (screenWidth > maxWidth) {
            desiredCardWidth = (int) (240 * metrics.density);
        } else {
            desiredCardWidth = (int) (120 * metrics.density);
        }
        int spanCount = Math.max(2, screenWidth / desiredCardWidth);
        
        GridLayoutManager layoutManager = (GridLayoutManager) recyclerViewMain.getLayoutManager();
        if (layoutManager != null && layoutManager.getSpanCount() != spanCount) {
            layoutManager.setSpanCount(spanCount);
            mainContentAdapter.notifyItemRangeChanged(0, mainContentAdapter.getItemCount());
        }
    }

    @Override
    public void onFeaturedClick(Movie movie) {
        openMovieDetail(movie);
    }

    @Override
    public void onMovieClick(Movie movie) {
        openMovieDetail(movie);
    }

    private void openMovieDetail(Movie movie) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openDetail(movie.getId());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (repository != null) {
            repository.close();
        }
    }

    // ==================== 排序相关 ====================

    private void showSortDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_sort, null);

        view.findViewById(R.id.sort_title_asc).setOnClickListener(v -> {
            changeSort(MovieRepository.SortType.TITLE_ASC);
            dialog.dismiss();
        });
        view.findViewById(R.id.sort_add_time).setOnClickListener(v -> {
            changeSort(MovieRepository.SortType.ADD_TIME_DESC);
            dialog.dismiss();
        });
        view.findViewById(R.id.sort_year).setOnClickListener(v -> {
            changeSort(MovieRepository.SortType.YEAR_DESC);
            dialog.dismiss();
        });
        view.findViewById(R.id.sort_rating).setOnClickListener(v -> {
            changeSort(MovieRepository.SortType.RATING_DESC);
            dialog.dismiss();
        });
        view.findViewById(R.id.sort_duration).setOnClickListener(v -> {
            changeSort(MovieRepository.SortType.DURATION_DESC);
            dialog.dismiss();
        });
        view.findViewById(R.id.sort_file_size).setOnClickListener(v -> {
            changeSort(MovieRepository.SortType.FILE_SIZE_DESC);
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void changeSort(MovieRepository.SortType sortType) {
        if (currentSortType != sortType) {
            currentSortType = sortType;
            loadMovies();
        }
    }

    private void updateSortText() {
        String sortText;
        switch (currentSortType) {
            case TITLE_ASC:
                sortText = getString(R.string.sort_title);
                break;
            case YEAR_DESC:
                sortText = getString(R.string.sort_year);
                break;
            case RATING_DESC:
                sortText = getString(R.string.sort_rating);
                break;
            case DURATION_DESC:
                sortText = getString(R.string.sort_duration);
                break;
            case FILE_SIZE_DESC:
                sortText = getString(R.string.sort_file_size);
                break;
            case ADD_TIME_DESC:
            default:
                sortText = getString(R.string.sort_add_time);
                break;
        }
        mainContentAdapter.setSortText(sortText);
    }
}
