package com.example.nasmovie;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.nasmovie.data.db.AppDatabase;
import com.example.nasmovie.data.smb.SmbImageCache;
import com.example.nasmovie.ui.LockActivity;
import com.example.nasmovie.util.PreferenceManager;

/**
 * 应用程序入口类
 */
public class NASMovieApp extends Application implements Application.ActivityLifecycleCallbacks {

    private static NASMovieApp instance;
    private AppDatabase database;
    private SmbImageCache imageCache;
    private PreferenceManager preferenceManager;

    // 前台Activity数量
    private int foregroundActivities = 0;
    // 标记是否正在显示锁屏
    private boolean isShowingLockScreen = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        database = AppDatabase.getInstance(this);
        imageCache = new SmbImageCache(this);
        preferenceManager = new PreferenceManager(this);

        // 固定使用深色主题
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        // 注册Activity生命周期回调
        registerActivityLifecycleCallbacks(this);
    }

    public static NASMovieApp getInstance() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return database;
    }

    public SmbImageCache getImageCache() {
        return imageCache;
    }

    // ==================== 应用锁逻辑 ====================

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        // 排除锁屏Activity本身
        if (activity instanceof LockActivity) {
            isShowingLockScreen = true;
            return;
        }

        foregroundActivities++;

        // 应用从后台切回前台（第一个Activity启动）
        if (foregroundActivities == 1 && !isShowingLockScreen) {
            checkAndShowLockScreen(activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        // 排除锁屏Activity本身
        if (activity instanceof LockActivity) {
            isShowingLockScreen = false;
            return;
        }

        foregroundActivities--;

        // 应用切到后台（最后一个Activity停止）
        if (foregroundActivities == 0) {
            // 标记需要显示锁屏
            if (preferenceManager != null && preferenceManager.isLockEnabled()) {
                preferenceManager.setShouldShowLock(true);
            }
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

    /**
     * 检查并显示锁屏
     */
    private void checkAndShowLockScreen(Activity activity) {
        if (preferenceManager == null) return;

        if (preferenceManager.isLockEnabled() && preferenceManager.shouldShowLock()) {
            // 启动锁屏Activity
            Intent intent = new Intent(activity, LockActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        }
    }

    /**
     * 获取前台Activity数量
     */
    public int getForegroundActivityCount() {
        return foregroundActivities;
    }
}