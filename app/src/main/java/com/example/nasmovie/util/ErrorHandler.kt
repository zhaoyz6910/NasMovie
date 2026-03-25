package com.example.nasmovie.util

import android.content.Context
import android.widget.Toast

/**
 * 统一错误处理工具类
 * 提供错误码映射和用户友好的错误提示
 */
class ErrorHandler(context: Context) {

    private val context = context.applicationContext

    /**
     * 处理错误并显示提示
     */
    fun handleError(errorCode: Int) {
        val message = getErrorMessage(errorCode)
        showToast(message)
    }

    /**
     * 处理异常并显示提示
     */
    fun handleException(e: Exception?) {
        val message = getExceptionMessage(e)
        showToast(message)
    }

    /**
     * 处理错误并回调
     */
    fun handleError(errorCode: Int, callback: ErrorCallback?) {
        val message = getErrorMessage(errorCode)
        callback?.onError(errorCode, message)
    }

    /**
     * 获取错误消息
     */
    fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            ERROR_NETWORK -> "网络连接失败，请检查网络设置"
            ERROR_SMB_CONNECTION -> "无法连接到 NAS 服务器"
            ERROR_SMB_AUTH -> "认证失败，请检查用户名和密码"
            ERROR_SMB_FILE_NOT_FOUND -> "文件不存在或已被删除"
            ERROR_DATABASE -> "数据操作失败，请重试"
            ERROR_PLAYER -> "视频播放失败，请检查文件格式"
            else -> "发生未知错误，请重试"
        }
    }

    /**
     * 根据异常类型获取错误消息
     */
    fun getExceptionMessage(e: Exception?): String {
        if (e == null) {
            return getErrorMessage(ERROR_UNKNOWN)
        }

        val exceptionName = e.javaClass.simpleName

        // SMB 相关异常
        if (exceptionName.contains("SMB") || exceptionName.contains("Smb")) {
            e.message?.let { msg ->
                when {
                    msg.contains("authentication") || msg.contains("logon") ->
                        return getErrorMessage(ERROR_SMB_AUTH)
                    msg.contains("connection") || msg.contains("timeout") ->
                        return getErrorMessage(ERROR_SMB_CONNECTION)
                    msg.contains("not found") || msg.contains("does not exist") ->
                        return getErrorMessage(ERROR_SMB_FILE_NOT_FOUND)
                    else -> { /* 继续执行默认处理 */ }
                }
            }
            return getErrorMessage(ERROR_SMB_CONNECTION)
        }

        // 网络相关异常
        if (exceptionName.contains("Socket") || exceptionName.contains("Connect") ||
            exceptionName.contains("UnknownHost") || exceptionName.contains("Timeout")) {
            return getErrorMessage(ERROR_NETWORK)
        }

        // 数据库相关异常
        if (exceptionName.contains("SQLite") || exceptionName.contains("Database")) {
            return getErrorMessage(ERROR_DATABASE)
        }

        // 播放器相关异常
        if (exceptionName.contains("Player") || exceptionName.contains("Playback") ||
            exceptionName.contains("Decoder")) {
            return getErrorMessage(ERROR_PLAYER)
        }

        return getErrorMessage(ERROR_UNKNOWN)
    }

    /**
     * 显示 Toast 提示
     */
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * 错误回调接口
     */
    interface ErrorCallback {
        fun onError(errorCode: Int, message: String)
    }

    companion object {
        // 错误码定义
        const val ERROR_NETWORK = 1001
        const val ERROR_SMB_CONNECTION = 1002
        const val ERROR_SMB_AUTH = 1003
        const val ERROR_SMB_FILE_NOT_FOUND = 1004
        const val ERROR_DATABASE = 1005
        const val ERROR_PLAYER = 1006
        const val ERROR_UNKNOWN = 9999
    }
}