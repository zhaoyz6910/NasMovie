package com.example.nasmovie.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nasmovie.NASMovieApp;
import com.example.nasmovie.R;
import com.example.nasmovie.data.db.SmbConfigDao;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.service.ScanService;
import com.example.nasmovie.ui.adapter.ServerAdapter;
import com.example.nasmovie.view.BottomSheetDrawer;
import com.example.nasmovie.view.NasToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * 服务器管理 Fragment - iOS 风格新版
 */
public class ServerManageFragment extends Fragment implements
    ServerAdapter.OnItemClickListener,
    ServerAdapter.OnItemLongClickListener,
    IBackInterceptor {

    private RecyclerView recyclerView;
    private View emptyView;
    private FloatingActionButton fabAdd;
    private View btnScan;
    private NasToolbar toolbar;

    private SmbConfigDao smbConfigDao;
    private ServerAdapter adapter;
    private ScanService scanService;

    // 当前正在扫描的服务器 ID
    private long currentScanningServerId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_server_manage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        initData();
        loadServers();
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        recyclerView = view.findViewById(R.id.recycler_view);
        emptyView = view.findViewById(R.id.empty_view);
        fabAdd = view.findViewById(R.id.fab_add);
        btnScan = view.findViewById(R.id.btn_scan);

        // 设置 Toolbar
        if (getActivity() != null) {
            ((androidx.appcompat.app.AppCompatActivity) getActivity()).setSupportActionBar(toolbar.getToolbar());
            if (((androidx.appcompat.app.AppCompatActivity) getActivity()).getSupportActionBar() != null) {
                ((androidx.appcompat.app.AppCompatActivity) getActivity()).getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }

        // 设置标题和返回按钮
        toolbar.setTitle(R.string.server_manage);
        toolbar.setShowBack(true);
        toolbar.setOnBackClickListener(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).performRealBack();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        // 禁用 item 动画，避免扫描时进度更新导致闪烁
        recyclerView.setItemAnimator(null);
        adapter = new ServerAdapter();
        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        recyclerView.setAdapter(adapter);

        fabAdd.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openServerEdit(null);
            }
        });

        btnScan.setOnClickListener(v -> scanMedia());
    }

    private void initData() {
        smbConfigDao = NASMovieApp.getInstance().getDatabase().smbConfigDao();
        scanService = new ScanService(requireContext());
    }

    private void loadServers() {
        new Thread(() -> {
            List<SmbConfig> servers = smbConfigDao.getAll();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    adapter.setServers(servers);
                    updateEmptyView();
                });
            }
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
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openServerEdit(String.valueOf(config.getId()));
        }
    }

    @Override
    public void onItemLongClick(SmbConfig config) {
        BottomSheetDrawer drawer = new BottomSheetDrawer.Builder()
            .addItem("扫描媒体库", () -> scanSingleServer(config.getId()))
            .addItem("编辑服务器", () -> onItemClick(config))
            .addDestructiveItem("删除服务器", () -> deleteServer(config))
            .build();

        drawer.show(getParentFragmentManager(), "ServerOptionsDrawer");
    }

    private void setAsDefaultServer(SmbConfig config) {
        new Thread(() -> {
            // 先清除所有默认标记
            List<SmbConfig> allServers = smbConfigDao.getAll();
            for (SmbConfig server : allServers) {
                if (server.isDefault()) {
                    server.setDefault(false);
                    smbConfigDao.update(server);
                }
            }
            // 设置当前服务器为默认
            config.setDefault(true);
            smbConfigDao.update(config);
            loadServers();
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "已设为默认服务器", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void deleteServer(SmbConfig config) {
        new BottomSheetDrawer.Builder()
            .addItem("取消", null)
            .addDestructiveItem("删除", () -> {
                new Thread(() -> {
                    smbConfigDao.delete(config);
                    loadServers();
                }).start();
            })
            .build()
            .show(getParentFragmentManager(), "DeleteConfirmDrawer");
    }

    private void scanSingleServer(long id) {
        if (scanService.isScanning()) {
            Toast.makeText(getContext(), R.string.scanning, Toast.LENGTH_SHORT).show();
            return;
        }
        scanService.scanServer(id, createScanCallback());
    }

    private void scanMedia() {
        if (scanService.isScanning()) {
            Toast.makeText(getContext(), "已停止当前扫描", Toast.LENGTH_SHORT).show();
            scanService.stopScan();
            adapter.clearScanStatus();
            if (btnScan instanceof com.google.android.material.button.MaterialButton) {
                ((com.google.android.material.button.MaterialButton) btnScan).setText("扫描媒体库");
            }
            return;
        }
        scanService.scanAllServers(createScanCallback());
    }

    private ScanService.ScanCallback createScanCallback() {
        return new ScanService.ScanCallback() {
            @Override
            public void onStart() {
                // 开始批量扫描
                if (btnScan instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) btnScan).setText("取消扫描");
                }
            }

            @Override
            public void onServerStart(SmbConfig config) {
                // 已在主线程
                currentScanningServerId = config.getId();
                adapter.updateScanProgress(currentScanningServerId, 0, "准备扫描...", "");
            }

            @Override
            public void onProgress(int current, int total, String currentPath) {
                // 已在主线程
                int percentage = total > 0 ? (current * 100 / total) : 0;
                String status = "正在扫描... " + percentage + "%";
                String detail = currentPath + " (" + current + "/" + total + ")";
                
                adapter.updateScanProgress(currentScanningServerId, percentage, status, detail);
            }

            @Override
            public void onComplete(int addedCount, int totalServers) {
                // 已在主线程
                currentScanningServerId = -1;
                adapter.clearScanStatus();
                if (btnScan instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) btnScan).setText("扫描媒体库");
                }
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(),
                            getString(R.string.scan_complete, addedCount),
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                // 已在主线程
                currentScanningServerId = -1;
                adapter.clearScanStatus();
                if (btnScan instanceof com.google.android.material.button.MaterialButton) {
                    ((com.google.android.material.button.MaterialButton) btnScan).setText("扫描媒体库");
                }
                if (isAdded() && getContext() != null) {
                    Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    @Override
    public boolean onBackPressed() {
        if (scanService != null && scanService.isScanning()) {
            showConfirmExitDialog();
            return true; // 拦截事件，显示自定义对话框
        }
        return false; // 不拦截，交给 Activity 处理
    }

    private void showConfirmExitDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_exit, null);
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(dialogView);
        
        // 设置对话框背景透明，以便显示自定义圆角背景
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            
            // 固定弹窗宽度 (320dp)
            int width = (int) (320 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

            // 设置背景遮罩 alpha
            android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.4f; // 对应 bg-black/40
            dialog.getWindow().setAttributes(lp);
        }

        dialogView.findViewById(R.id.btn_negative).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_positive).setOnClickListener(v -> {
            dialog.dismiss();
            if (scanService != null) scanService.stopScan();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).performRealBack();
            }
        });

        dialog.show();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            // Fragment 从隐藏变为可见时刷新列表
            loadServers();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadServers();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 只有在页面销毁时才确保停止，作为最后一道防线
        if (scanService != null && scanService.isScanning()) {
            scanService.stopScan();
        }
    }
}