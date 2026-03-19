package com.example.nasmovie.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 偏好设置管理类
 */
public class PreferenceManager {

    private static final String TAG = "PreferenceManager";
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
    private static final String KEY_LOCK_PASSWORD_SALT = "lock_password_salt";
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
        // 生成随机盐值
        String salt = generateSalt();
        // 使用 PBKDF2 加密存储密码（更安全）
        String encrypted = encryptPasswordPBKDF2(password, salt);
        // 存储格式: salt:hash
        preferences.edit()
                .putString(KEY_LOCK_PASSWORD_SALT, salt)
                .putString(KEY_LOCK_PASSWORD, encrypted)
                .apply();
    }

    public String getLockPassword() {
        return preferences.getString(KEY_LOCK_PASSWORD, "");
    }

    private String getLockPasswordSalt() {
        return preferences.getString(KEY_LOCK_PASSWORD_SALT, "");
    }

    public boolean verifyPassword(String password) {
        String salt = getLockPasswordSalt();
        String storedHash = getLockPassword();
        
        // 兼容旧的 MD5 密码（无盐值）
        if (salt.isEmpty()) {
            String md5Hash = encryptPasswordMD5(password);
            if (md5Hash.equals(storedHash)) {
                // 密码正确，升级为新格式
                setLockPassword(password);
                return true;
            }
            return false;
        }
        
        // 兼容旧的 SHA-256 + 盐值密码
        if (!storedHash.startsWith("pbkdf2:")) {
            String sha256Hash = encryptPasswordWithSalt(password, salt);
            if (sha256Hash.equals(storedHash)) {
                // 密码正确，升级为 PBKDF2
                setLockPassword(password);
                return true;
            }
            return false;
        }
        
        // 使用 PBKDF2 验证
        String encryptedInput = encryptPasswordPBKDF2(password, salt);
        return encryptedInput.equals(storedHash);
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

    /**
     * 生成随机盐值
     */
    private String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * 使用 SHA-256 + 盐值加密密码（用于兼容旧密码）
     */
    private String encryptPasswordWithSalt(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            md.update(password.getBytes());
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "SHA-256 not available", e);
            return "";
        }
    }

    // PBKDF2 迭代次数
    private static final int PBKDF2_ITERATIONS = 10000;
    private static final int PBKDF2_KEY_LENGTH = 256;

    /**
     * 使用 PBKDF2 加密密码（更安全）
     */
    private String encryptPasswordPBKDF2(String password, String salt) {
        try {
            java.security.spec.KeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    password.toCharArray(),
                    hexToBytes(salt),
                    PBKDF2_ITERATIONS,
                    PBKDF2_KEY_LENGTH
            );
            javax.crypto.SecretKeyFactory factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return "pbkdf2:" + bytesToHex(hash);
        } catch (Exception e) {
            Log.e(TAG, "PBKDF2 not available", e);
            // 回退到 SHA-256
            return encryptPasswordWithSalt(password, salt);
        }
    }

    /**
     * 十六进制字符串转字节数组
     */
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * MD5 加密（仅用于兼容旧密码）
     */
    private String encryptPasswordMD5(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5 not available", e);
            return "";
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}