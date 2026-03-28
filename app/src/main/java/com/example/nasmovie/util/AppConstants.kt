package com.example.nasmovie.util

/**
 * 应用全局常量配置类
 * 统一管理应用中的配置常量
 */
object AppConstants {

    // ==================== 播放器配置 ====================

    /**
     * 播放器控件自动隐藏延迟时间（毫秒）
     */
    const val PLAYER_HIDE_CONTROLS_DELAY = 3000

    /**
     * 手势快进/快退步长（毫秒）
     */
    const val PLAYER_SEEK_STEP = 10000

    // ==================== 应用锁配置 ====================

    /**
     * 密码长度
     */
    const val LOCK_PASSWORD_LENGTH = 4

    /**
     * 最小后台时间，超过此时间返回需要显示锁屏（毫秒）
     */
    const val LOCK_MIN_BACKGROUND_TIME: Long = 500

    /**
     * 密码错误最大次数
     */
    const val LOCK_MAX_ERROR_COUNT = 5

    /**
     * 密码错误锁定时间（毫秒）
     */
    const val LOCK_DELAY_TIME: Long = 30000

    // ==================== UI 配置 ====================

    /**
     * 双击退出间隔时间（毫秒）
     */
    const val BACK_PRESS_INTERVAL: Long = 2000

    /**
     * 轮播图自动切换间隔时间（毫秒）
     */
    const val AUTO_SLIDE_INTERVAL: Long = 4000

    /**
     * 轮播图虚拟数量（用于无限轮播）
     */
    const val VIEWPAGER_VIRTUAL_COUNT = 10000

    // ==================== SMB 配置 ====================

    /**
     * SMB 默认端口
     */
    const val SMB_PORT = 445

    /**
     * NETBIOS 端口
     */
    const val NETBIOS_PORT = 139

    /**
     * SMB 连接超时时间（毫秒）
     */
    const val SMB_CONNECT_TIMEOUT = 1000

    /**
     * SMB 设备扫描线程池大小
     */
    const val SMB_SCAN_THREAD_POOL_SIZE = 50

    /**
     * 最大递归扫描深度
     * 设为 5 是因为大多数电影库结构不会超过 5 层目录嵌套
     */
    const val MAX_RECURSION_DEPTH = 5

    // ==================== 缓存配置 ====================

    /**
     * 海报缓存最大大小（字节）
     * 100MB，假设每张海报约 200KB，可缓存约 500 张海报
     */
    const val MAX_POSTER_CACHE_SIZE: Long = 100 * 1024 * 1024

    /**
     * 搜索历史最大记录数
     */
    const val MAX_SEARCH_HISTORY_SIZE = 10

    // ==================== 数据库配置 ====================

    /**
     * 数据库名称
     */
    const val DATABASE_NAME = "nas_movie.db"

    /**
     * 数据库版本
     * 注意：此值必须与 AppDatabase 中的 version 保持一致
     */
    const val DATABASE_VERSION = 8

    // ==================== 视频文件扩展名 ====================

    /**
     * 支持的视频文件扩展名
     */
    val VIDEO_EXTENSIONS = arrayOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
        "m4v", "3gp", "mpg", "mpeg", "m2ts", "ts", "mts",
        "divx", "xvid", "rm", "rmvb"
    )

    /**
     * 支持的字幕文件扩展名
     */
    val SUBTITLE_EXTENSIONS = arrayOf(
        "srt", "ass", "ssa", "sub", "vtt"
    )

    // ==================== 海报文件名 ====================

    /**
     * 海报文件名（按优先级排序）
     */
    val POSTER_NAMES = arrayOf(
        "poster.jpg", "poster.png",
        "folder.jpg", "folder.png",
        "cover.jpg", "cover.png",
        "fanart.jpg", "fanart.png",
        "backdrop.jpg", "backdrop.png"
    )
}