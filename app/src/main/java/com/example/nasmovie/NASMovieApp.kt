package com.example.nasmovie

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.example.nasmovie.data.db.AppDatabase
import com.example.nasmovie.data.smb.SmbImageCache
import com.example.nasmovie.ui.LockActivity
import com.example.nasmovie.util.PreferenceManager
import com.example.nasmovie.util.SmbImageLoader
import java.lang.ref.WeakReference

/**
 * 应用程序入口类
 */
class NASMovieApp : Application(), Application.ActivityLifecycleCallbacks {

    companion object {
        private var instance: NASMovieApp? = null

        fun getInstance(): NASMovieApp {
            return instance ?: throw IllegalStateException("NASMovieApp 未初始化，请确保在 AndroidManifest.xml 中正确配置")
        }

        // 最小后台时间（毫秒），超过此时间返回需要显示锁屏
        // 设为 500ms 是为了防止短暂切换应用（如通知栏）时误触发锁屏
        private const val MIN_BACKGROUND_TIME_FOR_LOCK = 500L
    }

    private lateinit var _database: AppDatabase
    private lateinit var _imageCache: SmbImageCache
    private lateinit var _preferenceManager: PreferenceManager

    // 记录应用进入后台的时间（用于判断是否需要显示锁屏）
    private var backgroundTime: Long = 0
    // 标记是否正在显示锁屏
    private var isShowingLockScreen: Boolean = false
    // 标记是否正在启动锁屏
    private var isStartingLockScreen: Boolean = false
    // 当前 Started 的 Activity 数量
    private var startedActivityCount: Int = 0

    // Handler 用于延迟操作
    private val lockHandler = Handler(Looper.getMainLooper())
    private var currentActivityRef: WeakReference<Activity>? = null

    val database: AppDatabase
        get() = _database

    val imageCache: SmbImageCache
        get() = _imageCache

    override fun onCreate() {
        super.onCreate()
        instance = this
        _database = AppDatabase.getInstance(this)
        _imageCache = SmbImageCache(this)
        _preferenceManager = PreferenceManager(this)

        // 固定使用深色主题
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // 注册Activity生命周期回调
        registerActivityLifecycleCallbacks(this)
    }

    // ==================== 应用锁逻辑 ====================

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
        // 排除锁屏Activity本身
        if (activity is LockActivity) {
            isShowingLockScreen = true
            isStartingLockScreen = false
            return
        }

        startedActivityCount++

        // 应用从后台切回前台时检查锁屏
        if (startedActivityCount == 1 && !isShowingLockScreen && !isStartingLockScreen) {
            checkAndShowLockScreen(activity)
        }
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
        // 排除锁屏Activity本身
        if (activity is LockActivity) {
            isShowingLockScreen = false
            return
        }

        startedActivityCount--

        // 应用切到后台，记录时间
        if (startedActivityCount == 0) {
            backgroundTime = System.currentTimeMillis()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    /**
     * 检查并显示锁屏
     */
    private fun checkAndShowLockScreen(activity: Activity) {
        if (!::_preferenceManager.isInitialized) return
        if (!_preferenceManager.isLockEnabled) return

        // 防止重复启动锁屏
        if (isShowingLockScreen || isStartingLockScreen) return

        // 判断是否需要显示锁屏：
        // 1. 首次启动（backgroundTime == 0）
        // 2. 从后台返回（backgroundTime > 0 且超过最小后台时间）
        val needShowLock = if (backgroundTime == 0L) {
            // 首次启动
            true
        } else {
            // 从后台返回，检查时间间隔
            val currentTime = System.currentTimeMillis()
            currentTime - backgroundTime >= MIN_BACKGROUND_TIME_FOR_LOCK
        }

        if (needShowLock) {
            // 标记正在启动锁屏
            isStartingLockScreen = true

            // 保存当前 Activity 的弱引用
            currentActivityRef = WeakReference(activity)

            // 启动锁屏Activity
            val intent = Intent(activity, LockActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            activity.startActivity(intent)

            // 移除之前的回调，避免内存泄漏
            lockHandler.removeCallbacks(lockResetRunnable)
            // 延迟重置标记
            lockHandler.postDelayed(lockResetRunnable, 500)
        }
    }

    /**
     * 重置锁屏启动标记的 Runnable
     */
    private val lockResetRunnable = Runnable {
        isStartingLockScreen = false
    }

    override fun onTerminate() {
        super.onTerminate()
        // 释放图片缓存资源
        if (::_imageCache.isInitialized) {
            _imageCache.release()
        }
        // 释放图片加载器协程资源
        SmbImageLoader.release()
    }
}