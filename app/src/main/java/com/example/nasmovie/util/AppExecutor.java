package com.example.nasmovie.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用全局线程池管理器
 * 统一管理后台线程和主线程切换
 */
public class AppExecutor {

    private static volatile AppExecutor instance;

    // 后台线程池（单线程，保证顺序执行）
    private final ExecutorService diskIO;
    // 网络请求线程池
    private final ExecutorService networkIO;
    // 主线程 Handler
    private final Handler mainHandler;

    private AppExecutor() {
        this.diskIO = Executors.newSingleThreadExecutor();
        this.networkIO = Executors.newFixedThreadPool(3);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static AppExecutor getInstance() {
        if (instance == null) {
            synchronized (AppExecutor.class) {
                if (instance == null) {
                    instance = new AppExecutor();
                }
            }
        }
        return instance;
    }

    /**
     * 获取磁盘 IO 线程池（单线程）
     * 用于数据库操作、文件读写等
     */
    public ExecutorService diskIO() {
        return diskIO;
    }

    /**
     * 获取网络 IO 线程池（3个线程）
     * 用于网络请求、SMB 操作等
     */
    public ExecutorService networkIO() {
        return networkIO;
    }

    /**
     * 在主线程执行
     */
    public void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    /**
     * 在主线程延迟执行
     */
    public void runOnMainThreadDelayed(Runnable runnable, long delayMillis) {
        mainHandler.postDelayed(runnable, delayMillis);
    }

    /**
     * 移除主线程回调
     */
    public void removeMainThreadCallback(Runnable runnable) {
        mainHandler.removeCallbacks(runnable);
    }

    /**
     * 在后台线程执行（磁盘 IO）
     */
    public void runOnDiskIO(Runnable runnable) {
        diskIO.execute(runnable);
    }

    /**
     * 在后台线程执行（网络 IO）
     */
    public void runOnNetworkIO(Runnable runnable) {
        networkIO.execute(runnable);
    }

    /**
     * 释放资源
     */
    public void shutdown() {
        diskIO.shutdown();
        networkIO.shutdown();
    }
}
