package com.example.nasmovie.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 偏好设置管理类
 */
class PreferenceManager(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ==================== 主题模式 ====================

    fun setThemeMode(mode: Int) {
        preferences.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun getThemeMode(): Int = preferences.getInt(KEY_THEME_MODE, THEME_SYSTEM)

    // ==================== 默认服务器 ====================

    fun setDefaultServerId(serverId: Long) {
        preferences.edit().putLong(KEY_DEFAULT_SERVER_ID, serverId).apply()
    }

    fun getDefaultServerId(): Long = preferences.getLong(KEY_DEFAULT_SERVER_ID, -1)

    // ==================== 扫描相关 ====================

    fun setLastScanTime(time: Long) {
        preferences.edit().putLong(KEY_LAST_SCAN_TIME, time).apply()
    }

    fun getLastScanTime(): Long = preferences.getLong(KEY_LAST_SCAN_TIME, 0)

    fun setAutoScan(autoScan: Boolean) {
        preferences.edit().putBoolean(KEY_AUTO_SCAN, autoScan).apply()
    }

    fun isAutoScan(): Boolean = preferences.getBoolean(KEY_AUTO_SCAN, false)

    // ==================== 海报质量 ====================

    fun setPosterQuality(quality: Int) {
        preferences.edit().putInt(KEY_POSTER_QUALITY, quality).apply()
    }

    fun getPosterQuality(): Int = preferences.getInt(KEY_POSTER_QUALITY, 2)

    // ==================== 播放器手势 ====================

    fun setPlayerGestureEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PLAYER_GESTURE, enabled).apply()
    }

    fun isPlayerGestureEnabled(): Boolean = preferences.getBoolean(KEY_PLAYER_GESTURE, true)

    // ==================== 通用方法 ====================

    fun putString(key: String, value: String?) {
        preferences.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int): Int = preferences.getInt(key, defaultValue)

    fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long): Long = preferences.getLong(key, defaultValue)

    fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    // ==================== 锁屏功能 ====================

    var isLockEnabled: Boolean
        get() = preferences.getBoolean(KEY_LOCK_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_LOCK_ENABLED, value).apply()
        }

    var lockPassword: String
        get() = preferences.getString(KEY_LOCK_PASSWORD, "") ?: ""
        set(value) {
            if (value.isEmpty()) {
                // 清除密码时也清除盐值
                preferences.edit()
                    .remove(KEY_LOCK_PASSWORD)
                    .remove(KEY_LOCK_PASSWORD_SALT)
                    .apply()
            } else {
                preferences.edit().putString(KEY_LOCK_PASSWORD, value).apply()
            }
        }

    /**
     * 设置加密密码（使用 PBKDF2 加密）
     */
    fun setEncryptedLockPassword(password: String) {
        val salt = generateSalt()
        val encrypted = encryptPasswordPBKDF2(password, salt)
        preferences.edit()
            .putString(KEY_LOCK_PASSWORD_SALT, salt)
            .putString(KEY_LOCK_PASSWORD, encrypted)
            .apply()
    }

    /**
     * 获取存储的密码哈希值
     */
    fun getStoredPasswordHash(): String = preferences.getString(KEY_LOCK_PASSWORD, "") ?: ""

    private fun getLockPasswordSalt(): String =
        preferences.getString(KEY_LOCK_PASSWORD_SALT, "") ?: ""

    fun verifyPassword(password: String): Boolean {
        val salt = getLockPasswordSalt()
        val storedHash = getStoredPasswordHash()

        // 兼容旧的 MD5 密码（无盐值）
        if (salt.isEmpty()) {
            val md5Hash = encryptPasswordMD5(password)
            if (md5Hash == storedHash) {
                setEncryptedLockPassword(password)
                return true
            }
            return false
        }

        // 兼容旧的 SHA-256 + 盐值密码
        if (!storedHash.startsWith("pbkdf2:")) {
            val sha256Hash = encryptPasswordWithSalt(password, salt)
            if (sha256Hash == storedHash) {
                setEncryptedLockPassword(password)
                return true
            }
            return false
        }

        // 使用 PBKDF2 验证
        val encryptedInput = encryptPasswordPBKDF2(password, salt)
        return encryptedInput == storedHash
    }

    var shouldShowLock: Boolean
        get() = preferences.getBoolean(KEY_SHOULD_SHOW_LOCK, false)
        set(value) {
            preferences.edit().putBoolean(KEY_SHOULD_SHOW_LOCK, value).apply()
        }

    fun incrementLockErrorCount() {
        val count = lockErrorCount
        preferences.edit()
            .putInt(KEY_LOCK_ERROR_COUNT, count + 1)
            .putLong(KEY_LOCK_LAST_ERROR_TIME, System.currentTimeMillis())
            .apply()
    }

    val lockErrorCount: Int
        get() = preferences.getInt(KEY_LOCK_ERROR_COUNT, 0)

    fun clearLockErrorCount() {
        preferences.edit()
            .remove(KEY_LOCK_ERROR_COUNT)
            .remove(KEY_LOCK_LAST_ERROR_TIME)
            .apply()
    }

    fun getLockLastErrorTime(): Long = preferences.getLong(KEY_LOCK_LAST_ERROR_TIME, 0)

    /**
     * 检查是否需要延迟验证（防暴力破解）
     */
    val isLockDelayRequired: Boolean
        get() {
            val errorCount = lockErrorCount
            if (errorCount < AppConstants.LOCK_MAX_ERROR_COUNT) {
                return false
            }
            val lastErrorTime = getLockLastErrorTime()
            val currentTime = System.currentTimeMillis()
            return (currentTime - lastErrorTime) < AppConstants.LOCK_DELAY_TIME
        }

    val lockRemainingDelayTime: Long
        get() {
            val lastErrorTime = getLockLastErrorTime()
            val currentTime = System.currentTimeMillis()
            val remaining = AppConstants.LOCK_DELAY_TIME - (currentTime - lastErrorTime)
            return maxOf(0, remaining)
        }

    /**
     * 生成随机盐值
     */
    private fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)
        return bytesToHex(salt)
    }

    /**
     * 使用 SHA-256 + 盐值加密密码（用于兼容旧密码）
     */
    private fun encryptPasswordWithSalt(password: String, salt: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt.toByteArray())
            md.update(password.toByteArray())
            val digest = md.digest()
            bytesToHex(digest)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "SHA-256 not available", e)
            ""
        }
    }

    /**
     * 使用 PBKDF2 加密密码（更安全）
     */
    private fun encryptPasswordPBKDF2(password: String, salt: String): String {
        return try {
            val spec = PBEKeySpec(
                password.toCharArray(),
                hexToBytes(salt),
                PBKDF2_ITERATIONS,
                PBKDF2_KEY_LENGTH
            )
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = factory.generateSecret(spec).encoded
            "pbkdf2:" + bytesToHex(hash)
        } catch (e: Exception) {
            Log.e(TAG, "PBKDF2 not available", e)
            encryptPasswordWithSalt(password, salt)
        }
    }

    /**
     * 十六进制字符串转字节数组
     */
    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4)
                    + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    /**
     * MD5 加密（仅用于兼容旧密码）
     */
    private fun encryptPasswordMD5(password: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            md.update(password.toByteArray())
            val digest = md.digest()
            bytesToHex(digest)
        } catch (e: NoSuchAlgorithmException) {
            Log.e(TAG, "MD5 not available", e)
            ""
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "PreferenceManager"
        private const val PREF_NAME = "nas_movie_prefs"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEFAULT_SERVER_ID = "default_server_id"
        private const val KEY_LAST_SCAN_TIME = "last_scan_time"
        private const val KEY_AUTO_SCAN = "auto_scan"
        private const val KEY_POSTER_QUALITY = "poster_quality"
        private const val KEY_PLAYER_GESTURE = "player_gesture"

        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_LOCK_PASSWORD = "lock_password"
        private const val KEY_LOCK_PASSWORD_SALT = "lock_password_salt"
        private const val KEY_SHOULD_SHOW_LOCK = "should_show_lock"
        private const val KEY_LOCK_ERROR_COUNT = "lock_error_count"
        private const val KEY_LOCK_LAST_ERROR_TIME = "lock_last_error_time"

        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_SYSTEM = 2

        private const val PBKDF2_ITERATIONS = 10000
        private const val PBKDF2_KEY_LENGTH = 256
    }
}