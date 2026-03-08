package com.example.nasmovie.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 偏好设置管理类
 */
public class PreferenceManager {

    private static final String PREF_NAME = "nas_movie_prefs";

    // 键名
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_DEFAULT_SERVER_ID = "default_server_id";
    private static final String KEY_LAST_SCAN_TIME = "last_scan_time";
    private static final String KEY_AUTO_SCAN = "auto_scan";
    private static final String KEY_POSTER_QUALITY = "poster_quality";
    private static final String KEY_PLAYER_GESTURE = "player_gesture";

    // 锁屏相关键名
    private static final String KEY_LOCK_ENABLED = "lock_enabled";
    private static final String KEY_LOCK_PASSWORD = "lock_password";
    private static final String KEY_SHOULD_SHOW_LOCK = "should_show_lock";
    private static final String KEY_LOCK_ERROR_COUNT = "lock_error_count";
    private static final String KEY_LOCK_LAST_ERROR_TIME = "lock_last_error_time";

    // 主题模式
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_SYSTEM = 2;

    private SharedPreferences preferences;

    public PreferenceManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ==================== 主题模式 ====================

    public void setThemeMode(int mode) {
        preferences.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public int getThemeMode() {
        return preferences.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    // ==================== 默认服务器 ====================

    public void setDefaultServerId(long serverId) {
        preferences.edit().putLong(KEY_DEFAULT_SERVER_ID, serverId).apply();
    }

    public long getDefaultServerId() {
        return preferences.getLong(KEY_DEFAULT_SERVER_ID, -1);
    }

    // ==================== 扫描相关 ====================

    public void setLastScanTime(long time) {
        preferences.edit().putLong(KEY_LAST_SCAN_TIME, time).apply();
    }

    public long getLastScanTime() {
        return preferences.getLong(KEY_LAST_SCAN_TIME, 0);
    }

    public void setAutoScan(boolean autoScan) {
        preferences.edit().putBoolean(KEY_AUTO_SCAN, autoScan).apply();
    }

    public boolean isAutoScan() {
        return preferences.getBoolean(KEY_AUTO_SCAN, false);
    }

    // ==================== 海报质量 ====================

    public void setPosterQuality(int quality) {
        preferences.edit().putInt(KEY_POSTER_QUALITY, quality).apply();
    }

    public int getPosterQuality() {
        return preferences.getInt(KEY_POSTER_QUALITY, 2); // 默认中等质量
    }

    // ==================== 播放器手势 ====================

    public void setPlayerGestureEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_PLAYER_GESTURE, enabled).apply();
    }

    public boolean isPlayerGestureEnabled() {
        return preferences.getBoolean(KEY_PLAYER_GESTURE, true);
    }

    // ==================== 通用方法 ====================

    public void putString(String key, String value) {
        preferences.edit().putString(key, value).apply();
    }

    public String getString(String key, String defaultValue) {
        return preferences.getString(key, defaultValue);
    }

    public void putInt(String key, int value) {
        preferences.edit().putInt(key, value).apply();
    }

    public int getInt(String key, int defaultValue) {
        return preferences.getInt(key, defaultValue);
    }

    public void putLong(String key, long value) {
        preferences.edit().putLong(key, value).apply();
    }

    public long getLong(String key, long defaultValue) {
        return preferences.getLong(key, defaultValue);
    }

    public void putBoolean(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return preferences.getBoolean(key, defaultValue);
    }

    public void remove(String key) {
        preferences.edit().remove(key).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    // ==================== 锁屏功能 ====================

    public void setLockEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply();
    }

    public boolean isLockEnabled() {
        return preferences.getBoolean(KEY_LOCK_ENABLED, false);
    }

    public void setLockPassword(String password) {
        // 使用MD5加密存储密码
        String encrypted = encryptPassword(password);
        preferences.edit().putString(KEY_LOCK_PASSWORD, encrypted).apply();
    }

    public String getLockPassword() {
        return preferences.getString(KEY_LOCK_PASSWORD, "");
    }

    public boolean verifyPassword(String password) {
        String encryptedInput = encryptPassword(password);
        String storedPassword = getLockPassword();
        return encryptedInput.equals(storedPassword);
    }

    public void setShouldShowLock(boolean shouldShow) {
        preferences.edit().putBoolean(KEY_SHOULD_SHOW_LOCK, shouldShow).apply();
    }

    public boolean shouldShowLock() {
        return preferences.getBoolean(KEY_SHOULD_SHOW_LOCK, false);
    }

    public void incrementLockErrorCount() {
        int count = getLockErrorCount();
        preferences.edit().putInt(KEY_LOCK_ERROR_COUNT, count + 1)
                .putLong(KEY_LOCK_LAST_ERROR_TIME, System.currentTimeMillis())
                .apply();
    }

    public int getLockErrorCount() {
        return preferences.getInt(KEY_LOCK_ERROR_COUNT, 0);
    }

    public void clearLockErrorCount() {
        preferences.edit().remove(KEY_LOCK_ERROR_COUNT)
                .remove(KEY_LOCK_LAST_ERROR_TIME)
                .apply();
    }

    public long getLockLastErrorTime() {
        return preferences.getLong(KEY_LOCK_LAST_ERROR_TIME, 0);
    }

    /**
     * 检查是否需要延迟验证（防暴力破解）
     * 连续5次错误后需要等待30秒
     */
    public boolean isLockDelayRequired() {
        int errorCount = getLockErrorCount();
        if (errorCount < 5) {
            return false;
        }
        long lastErrorTime = getLockLastErrorTime();
        long currentTime = System.currentTimeMillis();
        // 需要等待30秒
        return (currentTime - lastErrorTime) < 30000;
    }

    public long getLockRemainingDelayTime() {
        long lastErrorTime = getLockLastErrorTime();
        long currentTime = System.currentTimeMillis();
        long remaining = 30000 - (currentTime - lastErrorTime);
        return Math.max(0, remaining);
    }

    private String encryptPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return password; // 降级处理
        }
    }
}