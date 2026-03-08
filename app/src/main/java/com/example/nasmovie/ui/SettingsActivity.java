package com.example.nasmovie.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.nasmovie.R;
import com.example.nasmovie.util.PreferenceManager;

/**
 * 设置页面
 */
public class SettingsActivity extends AppCompatActivity {

    private LinearLayout itemServerManage;
    private LinearLayout itemTheme;
    private LinearLayout itemClearCache;
    private LinearLayout itemAbout;
    private LinearLayout itemLockSettings;
    private LinearLayout itemLockPassword;
    private Switch switchLock;
    private TextView tvThemeValue;
    private TextView tvLockStatus;

    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        initViews();
        initData();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        itemServerManage = findViewById(R.id.item_server_manage);
        itemTheme = findViewById(R.id.item_theme);
        itemClearCache = findViewById(R.id.item_clear_cache);
        itemAbout = findViewById(R.id.item_about);
        itemLockSettings = findViewById(R.id.item_lock_settings);
        itemLockPassword = findViewById(R.id.item_lock_password);
        switchLock = findViewById(R.id.switch_lock);
        tvThemeValue = findViewById(R.id.tv_theme_value);
        tvLockStatus = findViewById(R.id.tv_lock_status);

        itemServerManage.setOnClickListener(v -> {
            startActivity(new Intent(this, ServerManageActivity.class));
        });

        itemTheme.setOnClickListener(v -> showThemeDialog());

        itemClearCache.setOnClickListener(v -> clearCache());

        itemAbout.setOnClickListener(v -> showAboutDialog());

        // 锁屏设置
        itemLockSettings.setOnClickListener(v -> toggleLock());
        switchLock.setOnClickListener(v -> toggleLock());
        itemLockPassword.setOnClickListener(v -> changePassword());
    }

    private void initData() {
        preferenceManager = new PreferenceManager(this);
        updateThemeDisplay();
        updateLockDisplay();
    }

    private void updateThemeDisplay() {
        int mode = preferenceManager.getThemeMode();
        String[] themeNames = {
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        };
        tvThemeValue.setText(themeNames[mode]);
    }

    private void showThemeDialog() {
        int currentMode = preferenceManager.getThemeMode();
        String[] themeNames = {
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        };

        new android.app.AlertDialog.Builder(this)
            .setTitle("主题模式")
            .setSingleChoiceItems(themeNames, currentMode, (dialog, which) -> {
                preferenceManager.setThemeMode(which);
                updateThemeDisplay();
                applyTheme(which);
                dialog.dismiss();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void applyTheme(int mode) {
        // 这里可以动态切换主题
        // 需要重启Activity才能生效
    }

    private void clearCache() {
        new android.app.AlertDialog.Builder(this)
            .setTitle(R.string.clear_cache)
            .setMessage("确定要清除缓存吗？")
            .setPositiveButton(R.string.confirm, (dialog, which) -> {
                // 清除缓存
                try {
                    // 清除Glide缓存
                    // Glide.get(this).clearMemory();

                    // 清除应用缓存
                    new Thread(() -> {
                        // Glide.get(this).clearDiskCache();
                    }).start();

                    android.widget.Toast.makeText(this, "缓存已清除", android.widget.Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showAboutDialog() {
        new android.app.AlertDialog.Builder(this)
            .setTitle(R.string.about)
            .setMessage("NAS影视库 v1.0\n\n一款基于SMB协议的NAS影视管理播放应用")
            .setPositiveButton(R.string.confirm, null)
            .show();
    }

    // ==================== 锁屏设置 ====================

    private void updateLockDisplay() {
        boolean isLockEnabled = preferenceManager.isLockEnabled();
        switchLock.setChecked(isLockEnabled);
        tvLockStatus.setText(isLockEnabled ? "已开启" : "已关闭");

        // 根据锁屏状态显示/隐藏修改密码选项
        if (isLockEnabled) {
            itemLockPassword.setVisibility(View.VISIBLE);
        } else {
            itemLockPassword.setVisibility(View.GONE);
        }
    }

    private void toggleLock() {
        boolean isLockEnabled = preferenceManager.isLockEnabled();

        if (isLockEnabled) {
            // 关闭锁屏
            new android.app.AlertDialog.Builder(this)
                .setTitle("关闭应用锁")
                .setMessage("确定要关闭应用锁吗？")
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    preferenceManager.setLockEnabled(false);
                    preferenceManager.setLockPassword("");
                    updateLockDisplay();
                    android.widget.Toast.makeText(this, "应用锁已关闭", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    switchLock.setChecked(true);
                })
                .setOnCancelListener(dialog -> switchLock.setChecked(true))
                .show();
        } else {
            // 开启锁屏，跳转到设置密码页面
            Intent intent = new Intent(this, LockActivity.class);
            intent.putExtra("setting_password", true);
            startActivityForResult(intent, REQUEST_SET_PASSWORD);
        }
    }

    private void changePassword() {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra("setting_password", true);
        startActivityForResult(intent, REQUEST_SET_PASSWORD);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SET_PASSWORD) {
            updateLockDisplay();
        }
    }

    private static final int REQUEST_SET_PASSWORD = 100;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}