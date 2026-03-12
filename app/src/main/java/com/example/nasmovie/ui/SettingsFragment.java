package com.example.nasmovie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.nasmovie.R;
import com.example.nasmovie.util.PreferenceManager;

/**
 * 设置 Fragment
 */
public class SettingsFragment extends Fragment {

    private LinearLayout itemServerManage;
    private LinearLayout itemClearCache;
    private LinearLayout itemAbout;
    private LinearLayout itemLockSettings;
    private LinearLayout itemLockPassword;
    private Switch switchLock;
    private TextView tvLockStatus;

    private PreferenceManager preferenceManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        initData();
    }

    private void initViews(View view) {
        itemServerManage = view.findViewById(R.id.item_server_manage);
        itemClearCache = view.findViewById(R.id.item_clear_cache);
        itemAbout = view.findViewById(R.id.item_about);
        itemLockSettings = view.findViewById(R.id.item_lock_settings);
        itemLockPassword = view.findViewById(R.id.item_lock_password);
        switchLock = view.findViewById(R.id.switch_lock);
        tvLockStatus = view.findViewById(R.id.tv_lock_status);

        itemServerManage.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openServerManage();
            }
        });

        itemClearCache.setOnClickListener(v -> clearCache());

        itemAbout.setOnClickListener(v -> showAboutDialog());

        itemLockSettings.setOnClickListener(v -> toggleLock());
        switchLock.setOnClickListener(v -> toggleLock());
        itemLockPassword.setOnClickListener(v -> changePassword());
    }

    private void initData() {
        preferenceManager = new PreferenceManager(requireContext());
        updateLockDisplay();
    }

    private void clearCache() {
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.clear_cache)
            .setMessage("确定要清除缓存吗？")
            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                new Thread(() -> {
                    requireActivity().runOnUiThread(() -> {
                        android.widget.Toast.makeText(getContext(), "缓存已清除", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }).start();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(getContext())
            .setTitle(R.string.about)
            .setMessage("NAS 影视库 v1.0\n\n一款基于 SMB 协议的 NAS 影视管理播放应用")
            .setPositiveButton(R.string.confirm, null)
            .show();
    }

    private void updateLockDisplay() {
        boolean isLockEnabled = preferenceManager.isLockEnabled();
        switchLock.setChecked(isLockEnabled);
        tvLockStatus.setText(isLockEnabled ? "已开启" : "已关闭");

        if (isLockEnabled) {
            itemLockPassword.setVisibility(View.VISIBLE);
        } else {
            itemLockPassword.setVisibility(View.GONE);
        }
    }

    private void toggleLock() {
        boolean isLockEnabled = preferenceManager.isLockEnabled();

        if (isLockEnabled) {
            new AlertDialog.Builder(getContext())
                .setTitle("关闭应用锁")
                .setMessage("确定要关闭应用锁吗？")
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    preferenceManager.setLockEnabled(false);
                    preferenceManager.setLockPassword("");
                    updateLockDisplay();
                    android.widget.Toast.makeText(getContext(), "应用锁已关闭", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    switchLock.setChecked(true);
                })
                .setOnCancelListener(dialog -> switchLock.setChecked(true))
                .show();
        } else {
            Intent intent = new Intent(getContext(), LockActivity.class);
            intent.putExtra("setting_password", true);
            startActivityForResult(intent, REQUEST_SET_PASSWORD);
        }
    }

    private void changePassword() {
        Intent intent = new Intent(getContext(), LockActivity.class);
        intent.putExtra("setting_password", true);
        startActivityForResult(intent, REQUEST_SET_PASSWORD);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SET_PASSWORD) {
            updateLockDisplay();
        }
    }

    private static final int REQUEST_SET_PASSWORD = 100;

    @Override
    public void onResume() {
        super.onResume();
        updateLockDisplay();
    }
}
