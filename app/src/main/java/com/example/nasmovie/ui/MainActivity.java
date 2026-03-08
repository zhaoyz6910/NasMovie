package com.example.nasmovie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.R;
import com.example.nasmovie.data.db.SmbConfigDao;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.repository.MovieRepository;
import com.example.nasmovie.service.ScanService;
import com.example.nasmovie.ui.adapter.MovieAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 主界面 - 电影列表
 */
public class MainActivity extends AppCompatActivity implements
    MovieAdapter.OnItemClickListener {

    private Toolbar toolbar;
    private RecyclerView recyclerView;
    private View emptyView;
    private MaterialButton btnScan;
    private ProgressBar progressBar;

    private MovieAdapter adapter;
    private MovieRepository repository;
    private SmbConfigDao smbConfigDao;
    private ScanService scanService;

    private List<Movie> allMovies = new ArrayList<>();
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initData();
        loadMovies();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty_view);
        btnScan = findViewById(R.id.btn_scan);
        progressBar = findViewById(R.id.progress_bar);

        // 设置RecyclerView
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new MovieAdapter();
        adapter.setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);

        // 扫描按钮
        btnScan.setOnClickListener(v -> startScan());
    }

    private void initData() {
        repository = new MovieRepository(this);
        smbConfigDao = NASMovieApp.getInstance().getDatabase().smbConfigDao();
        scanService = new ScanService(this);
    }

    private void loadMovies() {
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        new Thread(() -> {
            allMovies = repository.getAllMovies();
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                updateMovieList();
            });
        }).start();
    }

    private void updateMovieList() {
        new Thread(() -> {
            List<Movie> movies;
            if (TextUtils.isEmpty(searchQuery)) {
                movies = allMovies;
            } else {
                movies = repository.searchMovies(searchQuery);
            }

            final List<Movie> finalMovies = movies;
            runOnUiThread(() -> {
                adapter.setMovies(finalMovies);

                if (finalMovies.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            });
        }).start();
    }

    @Override
    public void onItemClick(Movie movie) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_MOVIE_ID, movie.getId());
        startActivity(intent);
    }

    private void startScan() {
        new Thread(() -> {
            List<SmbConfig> allServers = smbConfigDao.getAll();
            runOnUiThread(() -> {
                if (allServers.isEmpty()) {
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.error_no_server)
                        .setMessage("请先在设置中添加NAS服务器")
                        .setPositiveButton(R.string.settings, (dialog, which) -> {
                            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                    return;
                }

                startScanAllServers(allServers);
            });
        }).start();
    }

    private void startScanAllServers(List<SmbConfig> servers) {
        progressBar.setVisibility(View.VISIBLE);

        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger totalAdded = new AtomicInteger(0);
        AtomicInteger totalScanned = new AtomicInteger(0);
        StringBuilder errorBuilder = new StringBuilder();
        int serverCount = servers.size();

        // 为每个服务器创建独立的 ScanService 实例，实现并行扫描
        for (SmbConfig server : servers) {
            ScanService serverScanService = new ScanService(this);
            serverScanService.scanLibrary(server, new ScanService.ScanCallback() {
                @Override
                public void onStart() {
                    // 多个服务器同时开始，不单独处理
                }

                @Override
                public void onProgress(int current, int total, String currentPath) {
                    // 可以显示进度
                }

                @Override
                public void onComplete(int addedCount, int totalCount) {
                    totalAdded.addAndGet(addedCount);
                    totalScanned.addAndGet(totalCount);

                    if (completedCount.incrementAndGet() == serverCount) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            String message = errorBuilder.length() > 0
                                ? String.format("扫描完成，发现 %d 部电影\n%s", totalAdded.get(), errorBuilder.toString())
                                : getString(R.string.scan_complete, totalAdded.get());
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            loadMovies();
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    errorBuilder.append(server.getName()).append(": ").append(error).append("\n");

                    if (completedCount.incrementAndGet() == serverCount) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            String message = String.format("扫描完成，发现 %d 部电影\n%s", totalAdded.get(), errorBuilder.toString());
                            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                            loadMovies();
                        });
                    }
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // 搜索功能
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_hint));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                updateMovieList();
                return true;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_scan) {
            startScan();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 刷新列表
        loadMovies();
    }
}