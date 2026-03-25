package com.example.nasmovie.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 应用全局线程池管理器
 * 统一管理后台线程和主线程切换
 */
object AppExecutor {

    // 后台线程池（单线程，保证顺序执行）
    private val diskIO: ExecutorService = Executors.newSingleThreadExecutor()

    // 网络请求线程池
    private val networkIO: ExecutorService = Executors.newFixedThreadPool(3)

    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 获取磁盘 IO 线程池（单线程）
     * 用于数据库操作、文件读写等
     */
    fun diskIO(): ExecutorService = diskIO

    /**
     * 获取网络 IO 线程池（3个线程）
     * 用于网络请求、SMB 操作等
     */
    fun networkIO(): ExecutorService = networkIO

    /**
     * 在主线程执行
     */
    fun runOnMainThread(runnable: Runnable) {
        mainHandler.post(runnable)
    }

    /**
     * 在主线程延迟执行
     */
    fun runOnMainThreadDelayed(runnable: Runnable, delayMillis: Long) {
        mainHandler.postDelayed(runnable, delayMillis)
    }

    /**
     * 移除主线程回调
     */
    fun removeMainThreadCallback(runnable: Runnable) {
        mainHandler.removeCallbacks(runnable)
    }

    /**
     * 在后台线程执行（磁盘 IO）
     */
    fun runOnDiskIO(runnable: Runnable) {
        diskIO.execute(runnable)
    }

    /**
     * 在后台线程执行（网络 IO）
     */
    fun runOnNetworkIO(runnable: Runnable) {
        networkIO.execute(runnable)
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        diskIO.shutdown()
        networkIO.shutdown()
    }
}