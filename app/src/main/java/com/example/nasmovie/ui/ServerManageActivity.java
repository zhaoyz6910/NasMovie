package com.example.nasmovie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.R;
import com.example.nasmovie.data.db.SmbConfigDao;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.ui.adapter.ServerAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * 服务器管理页面
 */
public class ServerManageActivity extends AppCompatActivity implements
    ServerAdapter.OnItemClickListener,
    ServerAdapter.OnItemLongClickListener {

    private RecyclerView recyclerView;
    private View emptyView;
    private FloatingActionButton fabAdd;

    private SmbConfigDao smbConfigDao;
    private ServerAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_manage);

        initViews();
        initData();
        loadServers();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerView = findViewById(R.id.recycler_view);
        emptyView = findViewById(R.id.empty_view);
        fabAdd = findViewById(R.id.fab_add);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ServerAdapter();
        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            startActivity(new Intent(this, ServerEditActivity.class));
        });
    }

    private void initData() {
        smbConfigDao = NASMovieApp.getInstance().getDatabase().smbConfigDao();
    }

    private void loadServers() {
        new Thread(() -> {
            List<SmbConfig> servers = smbConfigDao.getAll();
            runOnUiThread(() -> {
                adapter.setServers(servers);
                updateEmptyView();
            });
        }).start();
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

    @Override
    public void onItemClick(SmbConfig config) {
        // 编辑服务器
        Intent intent = new Intent(this, ServerEditActivity.class);
        intent.putExtra(ServerEditActivity.EXTRA_SERVER_ID, config.getId());
        startActivity(intent);
    }

    @Override
    public void onItemLongClick(SmbConfig config) {
        // 显示操作菜单
        String[] items = {"删除"};

        new AlertDialog.Builder(this)
            .setTitle(config.getName())
            .setItems(items, (dialog, which) -> {
                if (which == 0) {
                    deleteServer(config);
                }
            })
            .show();
    }

    private void deleteServer(SmbConfig config) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm)
            .setMessage("确定要删除服务器 \"" + config.getName() + "\" 吗？")
            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                new Thread(() -> {
                    smbConfigDao.delete(config);
                    runOnUiThread(() -> loadServers());
                }).start();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadServers();
    }
}