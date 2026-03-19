package com.example.nasmovie;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.nasmovie.data.db.AppDatabase;
import com.example.nasmovie.data.smb.SmbImageCache;
import com.example.nasmovie.ui.ExoPlayerActivity;
import com.example.nasmovie.ui.LockActivity;
import com.example.nasmovie.util.PreferenceManager;

import java.lang.ref.WeakReference;

/**
 * 应用程序入口类
 */
public class NASMovieApp extends Application implements Application.ActivityLifecycleCallbacks {

    private static NASMovieApp instance;
    private AppDatabase database;
    private SmbImageCache imageCache;
    private PreferenceManager preferenceManager;

    // 记录应用进入后台的时间（用于判断是否需要显示锁屏）
    private long backgroundTime = 0;
    // 标记是否正在显示锁屏
    private boolean isShowingLockScreen = false;
    // 标记是否正在启动锁屏
    private boolean isStartingLockScreen = false;
    // 当前 Started 的 Activity 数量
    private int startedActivityCount = 0;

    // 最小后台时间（毫秒），超过此时间返回需要显示锁屏
    private static final long MIN_BACKGROUND_TIME_FOR_LOCK = 500;

    // Handler 用于延迟操作
    private final Handler lockHandler = new Handler(Looper.getMainLooper());
    private WeakReference<Activity> currentActivityRef;

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
            isStartingLockScreen = false;
            return;
        }

        startedActivityCount++;

        // 应用从后台切回前台时检查锁屏
        if (startedActivityCount == 1 && !isShowingLockScreen && !isStartingLockScreen) {
            checkAndShowLockScreen(activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        // 为所有 Activity 设置 FLAG_SECURE，禁止任务管理器显示内容和截图
        activity.getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
        );
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

        startedActivityCount--;

        // 应用切到后台，记录时间
        if (startedActivityCount == 0) {
            backgroundTime = System.currentTimeMillis();
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
        if (!preferenceManager.isLockEnabled()) return;

        // 防止重复启动锁屏
        if (isShowingLockScreen || isStartingLockScreen) return;

        // 判断是否需要显示锁屏：
        // 1. 首次启动（backgroundTime == 0）
        // 2. 从后台返回（backgroundTime > 0 且超过最小后台时间）
        boolean needShowLock = false;
        
        if (backgroundTime == 0) {
            // 首次启动
            needShowLock = true;
        } else {
            // 从后台返回，检查时间间隔
            long currentTime = System.currentTimeMillis();
            if (currentTime - backgroundTime >= MIN_BACKGROUND_TIME_FOR_LOCK) {
                needShowLock = true;
            }
        }

        if (needShowLock) {
            // 标记正在启动锁屏
            isStartingLockScreen = true;
            
            // 保存当前 Activity 的弱引用
            currentActivityRef = new WeakReference<>(activity);
            
            // 启动锁屏Activity
            Intent intent = new Intent(activity, LockActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivity(intent);
            
            // 移除之前的回调，避免内存泄漏
            lockHandler.removeCallbacks(lockResetRunnable);
            // 延迟重置标记
            lockHandler.postDelayed(lockResetRunnable, 500);
        }
    }

    /**
     * 重置锁屏启动标记的 Runnable
     * 使用静态内部类避免持有外部类引用
     */
    private final Runnable lockResetRunnable = new Runnable() {
        @Override
        public void run() {
            isStartingLockScreen = false;
        }
    };

    @Override
    public void onTerminate() {
        super.onTerminate();
        // 释放图片缓存资源
        if (imageCache != null) {
            imageCache.release();
        }
    }
}