package com.example.nasmovie.util;

import android.content.Context;
import android.widget.Toast;

/**
 * 统一错误处理工具类
 * 提供错误码映射和用户友好的错误提示
 */
public class ErrorHandler {

    // 错误码定义
    public static final int ERROR_NETWORK = 1001;
    public static final int ERROR_SMB_CONNECTION = 1002;
    public static final int ERROR_SMB_AUTH = 1003;
    public static final int ERROR_SMB_FILE_NOT_FOUND = 1004;
    public static final int ERROR_DATABASE = 1005;
    public static final int ERROR_PLAYER = 1006;
    public static final int ERROR_UNKNOWN = 9999;

    private final Context context;

    public ErrorHandler(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 处理错误并显示提示
     * @param errorCode 错误码
     */
    public void handleError(int errorCode) {
        String message = getErrorMessage(errorCode);
        showToast(message);
    }

    /**
     * 处理异常并显示提示
     * @param e 异常
     */
    public void handleException(Exception e) {
        String message = getExceptionMessage(e);
        showToast(message);
    }

    /**
     * 处理错误并回调
     * @param errorCode 错误码
     * @param callback 错误回调
     */
    public void handleError(int errorCode, ErrorCallback callback) {
        String message = getErrorMessage(errorCode);
        if (callback != null) {
            callback.onError(errorCode, message);
        }
    }

    /**
     * 获取错误消息
     * @param errorCode 错误码
     * @return 用户友好的错误消息
     */
    public String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case ERROR_NETWORK:
                return "网络连接失败，请检查网络设置";
            case ERROR_SMB_CONNECTION:
                return "无法连接到 NAS 服务器";
            case ERROR_SMB_AUTH:
                return "认证失败，请检查用户名和密码";
            case ERROR_SMB_FILE_NOT_FOUND:
                return "文件不存在或已被删除";
            case ERROR_DATABASE:
                return "数据操作失败，请重试";
            case ERROR_PLAYER:
                return "视频播放失败，请检查文件格式";
            default:
                return "发生未知错误，请重试";
        }
    }

    /**
     * 根据异常类型获取错误消息
     * @param e 异常
     * @return 用户友好的错误消息
     */
    public String getExceptionMessage(Exception e) {
        if (e == null) {
            return getErrorMessage(ERROR_UNKNOWN);
        }

        String exceptionName = e.getClass().getSimpleName();
        
        // SMB 相关异常
        if (exceptionName.contains("SMB") || exceptionName.contains("Smb")) {
            if (e.getMessage() != null) {
                if (e.getMessage().contains("authentication") || e.getMessage().contains("logon")) {
                    return getErrorMessage(ERROR_SMB_AUTH);
                }
                if (e.getMessage().contains("connection") || e.getMessage().contains("timeout")) {
                    return getErrorMessage(ERROR_SMB_CONNECTION);
                }
                if (e.getMessage().contains("not found") || e.getMessage().contains("does not exist")) {
                    return getErrorMessage(ERROR_SMB_FILE_NOT_FOUND);
                }
            }
            return getErrorMessage(ERROR_SMB_CONNECTION);
        }

        // 网络相关异常
        if (exceptionName.contains("Socket") || exceptionName.contains("Connect") || 
            exceptionName.contains("UnknownHost") || exceptionName.contains("Timeout")) {
            return getErrorMessage(ERROR_NETWORK);
        }

        // 数据库相关异常
        if (exceptionName.contains("SQLite") || exceptionName.contains("Database")) {
            return getErrorMessage(ERROR_DATABASE);
        }

        // 播放器相关异常
        if (exceptionName.contains("Player") || exceptionName.contains("Playback") ||
            exceptionName.contains("Decoder")) {
            return getErrorMessage(ERROR_PLAYER);
        }

        return getErrorMessage(ERROR_UNKNOWN);
    }

    /**
     * 显示 Toast 提示
     */
    private void showToast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * 错误回调接口
     */
    public interface ErrorCallback {
        void onError(int errorCode, String message);
    }
}
