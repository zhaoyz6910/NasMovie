package com.example.nasmovie.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.nasmovie.R;
import com.example.nasmovie.util.AppExecutor;
import com.example.nasmovie.util.PreferenceManager;

/**
 * 设置 Fragment - iOS 风格复刻版
 */
public class SettingsFragment extends Fragment {

    private LinearLayout itemServerManage;
    private LinearLayout itemClearCache;
    private LinearLayout itemAbout;
    private LinearLayout itemLockSettings;
    private View cardLockPassword;
    private LinearLayout itemLockPassword;
    private com.google.android.material.switchmaterial.SwitchMaterial switchLock;
    private com.example.nasmovie.view.NasToolbar toolbar;

    private PreferenceManager preferenceManager;

    // Activity Result API
    private ActivityResultLauncher<Intent> setPasswordLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        initViews(view);
        initData();
        initActivityResultLauncher();
    }

    private void initActivityResultLauncher() {
        setPasswordLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> updateLockDisplay()
        );
    }

    private void initViews(View view) {
        toolbar = view.findViewById(R.id.toolbar);
        itemServerManage = view.findViewById(R.id.item_server_manage);
        itemClearCache = view.findViewById(R.id.item_clear_cache);
        itemAbout = view.findViewById(R.id.item_about);
        itemLockSettings = view.findViewById(R.id.item_lock_settings);
        cardLockPassword = view.findViewById(R.id.card_lock_password);
        itemLockPassword = view.findViewById(R.id.item_lock_password);
        switchLock = view.findViewById(R.id.switch_lock);

        // 设置 Toolbar
        if (toolbar != null) {
            toolbar.setTitle(R.string.settings);
            toolbar.setShowBack(false);
        }

        itemServerManage.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openServerManage();
            }
        });

        itemClearCache.setOnClickListener(v -> clearCache());

        itemAbout.setOnClickListener(v -> showAboutDialog());

        itemLockSettings.setOnClickListener(v -> toggleLock());
        itemLockPassword.setOnClickListener(v -> changePassword());
    }

    private void initData() {
        preferenceManager = new PreferenceManager(requireContext());
        updateLockDisplay();
    }

    private void clearCache() {
        showCustomDialog("清除缓存", "确定要清除所有图片缓存吗？", "确定", "取消", () -> {
            AppExecutor.getInstance().runOnDiskIO(() -> {
                // 执行清理逻辑
                com.example.nasmovie.NASMovieApp.getInstance().getImageCache().clearCache();
                if (isAdded() && getContext() != null) {
                    requireActivity().runOnUiThread(() -> {
                        android.widget.Toast.makeText(getContext(), "缓存已清除", android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    private void showAboutDialog() {
        String versionName = "1.0";
        try {
            versionName = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String message = getString(R.string.about_message, versionName);
        showCustomDialog("关于", message, "确定", null, null);
    }

    /**
     * 显示通用自定义样式的对话框
     */
    private void showCustomDialog(String title, String message, String positiveText, String negativeText, Runnable onPositive) {
        if (getContext() == null) return;
        
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_confirm_exit, null);
        android.app.Dialog dialog = new android.app.Dialog(requireContext());
        dialog.setContentView(dialogView);
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            
            // 固定弹窗宽度 (320dp)
            int width = (int) (320 * getResources().getDisplayMetrics().density);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

            android.view.WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.4f;
            dialog.getWindow().setAttributes(lp);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_dialog_message);
        TextView btnPositive = dialogView.findViewById(R.id.btn_positive);
        TextView btnNegative = dialogView.findViewById(R.id.btn_negative);

        tvTitle.setText(title);
        tvMessage.setText(message);
        
        if (positiveText != null) {
            btnPositive.setText(positiveText);
            btnPositive.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPositive != null) onPositive.run();
            });
        } else {
            btnPositive.setVisibility(View.GONE);
        }

        if (negativeText != null) {
            btnNegative.setText(negativeText);
            btnNegative.setOnClickListener(v -> dialog.dismiss());
        } else {
            btnNegative.setVisibility(View.GONE);
        }

        dialog.show();
    }

    private void updateLockDisplay() {
        boolean isLockEnabled = preferenceManager.isLockEnabled();
        switchLock.setChecked(isLockEnabled);
        
        if (isLockEnabled) {
            if (cardLockPassword != null) cardLockPassword.setVisibility(View.VISIBLE);
        } else {
            if (cardLockPassword != null) cardLockPassword.setVisibility(View.GONE);
        }
    }

    private void toggleLock() {
        boolean isLockEnabled = preferenceManager.isLockEnabled();

        if (isLockEnabled) {
            showCustomDialog("关闭应用锁", "确定要关闭应用锁吗？", "确定", "取消", () -> {
                preferenceManager.setLockEnabled(false);
                preferenceManager.setLockPassword("");
                updateLockDisplay();
                android.widget.Toast.makeText(getContext(), "应用锁已关闭", android.widget.Toast.LENGTH_SHORT).show();
            });
            // 确保在弹窗出现时 Switch 状态保持开启
            switchLock.setChecked(true);
        } else {
            Intent intent = new Intent(getContext(), LockActivity.class);
            intent.putExtra("setting_password", true);
            setPasswordLauncher.launch(intent);
        }
    }

    private void changePassword() {
        Intent intent = new Intent(getContext(), LockActivity.class);
        intent.putExtra("setting_password", true);
        setPasswordLauncher.launch(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateLockDisplay();
    }
}