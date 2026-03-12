package com.example.nasmovie.ui;

import android.content.Intent;
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
import androidx.viewpager2.widget.ViewPager2;

import com.example.nasmovie.R;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.WatchProgress;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.ui.adapter.FeaturedMovieAdapter;
import com.example.nasmovie.ui.adapter.MainContentAdapter;

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
    private ProgressBar progressBar;
    private View emptyView;
    private View cardSearch;
    private Toolbar toolbar;

    private FeaturedMovieAdapter featuredAdapter;
    private MainContentAdapter mainContentAdapter;

    private MovieRepository repository;
    private List<Movie> allMovies = new ArrayList<>();

    // 自动轮播相关
    private static final long AUTO_SLIDE_INTERVAL = 4000; // 4秒轮播一次
    private final android.os.Handler autoSlideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable autoSlideRunnable = new Runnable() {
        @Override
        public void run() {
            if (viewPagerFeatured != null && featuredAdapter != null && featuredAdapter.getItemCount() > 0) {
                int currentItem = viewPagerFeatured.getCurrentItem();
                viewPagerFeatured.setCurrentItem(currentItem + 1, true);
                autoSlideHandler.postDelayed(this, AUTO_SLIDE_INTERVAL);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    private void startAutoSlide() {
        stopAutoSlide();
        autoSlideHandler.postDelayed(autoSlideRunnable, AUTO_SLIDE_INTERVAL);
    }

    private void stopAutoSlide() {
        autoSlideHandler.removeCallbacks(autoSlideRunnable);
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
        progressBar = view.findViewById(R.id.progress_bar);
        emptyView = view.findViewById(R.id.empty_view);
        cardSearch = view.findViewById(R.id.card_search);

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity != null) {
            activity.setSupportActionBar(toolbar);
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
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int viewType = mainContentAdapter.getItemViewType(position);
                return (viewType == MainContentAdapter.TYPE_HEADER ||
                        viewType == MainContentAdapter.TYPE_SECTION ||
                        viewType == MainContentAdapter.TYPE_GRID_HEADER) ? 3 : 1;
            }
        });
        recyclerViewMain.setLayoutManager(gridLayoutManager);
        recyclerViewMain.setAdapter(mainContentAdapter);
        recyclerViewMain.setHasFixedSize(true);

        view.findViewById(R.id.btn_scan).setOnClickListener(v -> {
            // 切换到设置 Fragment
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToSettings();
            }
        });

        cardSearch.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSearch();
            }
        });
    }

    private void initData() {
        repository = new MovieRepository(requireContext());
    }

    private void loadMovies() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        new Thread(() -> {
            allMovies = repository.getAllMovies();

            List<Movie> featuredMovies = getRandomMovies(5);
            List<Movie> recentMovies = getRecentWatchedMovies(10);
            List<Movie> highRatedMovies = getHighRatedMovies(10);
            List<Movie> newestMovies = getNewestMovies(10);

            requireActivity().runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);

                if (allMovies.isEmpty()) {
                    showEmptyView();
                } else {
                    showContent(featuredMovies, recentMovies, highRatedMovies, newestMovies);
                }
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

            // 强制触发一次布局刷新，以确保 PageTransformer 立即生效
            viewPagerFeatured.post(() -> {
                if (isAdded() && viewPagerFeatured != null) {
                    viewPagerFeatured.requestLayout();
                }
            });

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
    public void onResume() {
        super.onResume();
        loadMovies();
        startAutoSlide();
        
        // 当从其他页面切回时，强制 ViewPager2 重新布局以触发 PageTransformer
        if (viewPagerFeatured != null) {
            viewPagerFeatured.post(() -> {
                if (isAdded() && viewPagerFeatured != null) {
                    viewPagerFeatured.requestLayout();
                    // 额外触发一次无效化，确保绘制流程完整
                    viewPagerFeatured.invalidate();
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoSlide();
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
}
