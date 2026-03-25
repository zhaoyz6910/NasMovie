package com.example.nasmovie.data.smb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.nasmovie.data.model.SmbConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SMB 扫描管理器 (Kotlin 协程重构版)
 * 整合设备发现和文件扫描功能
 */
class SmbScanManager(private val context: Context) {

    companion object {
        private const val TAG = "SmbScanManager"
    }

    private val deviceScanner = SmbDeviceScanner()
    private val fileScanner = SmbScanner()

    // ==================== 设备发现 ====================

    fun discoverDevices(callback: SmbDeviceScanner.DeviceScanCallback) {
        deviceScanner.setCallback(callback)
        deviceScanner.quickScan()
    }

    fun discoverDevices(subnet: String, callback: SmbDeviceScanner.DeviceScanCallback) {
        deviceScanner.setCallback(callback)
        deviceScanner.startScan(subnet)
    }

    fun discoverDevices(subnet: String, startIp: Int, endIp: Int, callback: SmbDeviceScanner.DeviceScanCallback) {
        deviceScanner.setCallback(callback)
        deviceScanner.startScan(subnet, startIp, endIp)
    }

    fun cancelDeviceDiscovery() {
        deviceScanner.cancelScan()
    }

    val isDiscovering: Boolean
        get() = deviceScanner.isScanning

    // ==================== 文件扫描 ====================

    fun scanMovies(config: SmbConfig, callback: SmbScannerCallback) {
        scanMovies(config, "/", true, callback)
    }

    fun scanMovies(config: SmbConfig, startPath: String, recursive: Boolean, callback: SmbScannerCallback) {
        fileScanner.setCallback(callback)
        fileScanner.startScan(config, startPath, recursive)
    }

    fun cancelFileScan() {
        fileScanner.cancelScan()
    }

    val isScanningFiles: Boolean
        get() = fileScanner.isScanning

    // ==================== 测试连接 ====================

    suspend fun testConnection(config: SmbConfig): Boolean = withContext(Dispatchers.IO) {
        var result = false
        try {
            SmbClient().use { client ->
                result = client.connect(config)
            }
        } catch (e: Exception) {
            Log.e(TAG, "testConnection error", e)
        }
        result
    }

    suspend fun testConnectionAndPath(config: SmbConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            SmbClient().use { client ->
                if (client.connect(config)) {
                    val path = config.moviePath ?: ""
                    // 如果能成功 listFiles，说明路径存在且有权限
                    client.listFiles(path)
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "testConnectionAndPath error", e)
        }
        false
    }

    fun testConnectionAsync(config: SmbConfig, listener: ConnectionTestListener?) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = testConnection(config)
            withContext(Dispatchers.Main) {
                listener?.onResult(success)
            }
        }
    }

    interface ConnectionTestListener {
        fun onResult(success: Boolean)
    }

    // ==================== 资源释放 ====================

    fun release() {
        Log.d(TAG, "Releasing resources")
        deviceScanner.release()
        fileScanner.release()
    }

    // ==================== 便捷方法 ====================

    fun createConfigFromDevice(device: SmbDeviceScanner.DiscoveredDevice): SmbConfig {
        return SmbConfig().apply {
            name = device.displayName
            host = device.ip
            port = device.port
            shareName = "" // 需要用户填写
        }
    }

    fun scanDevice(
        device: SmbDeviceScanner.DiscoveredDevice, shareName: String,
        username: String, password: String?, callback: SmbScannerCallback
    ) {
        val config = SmbConfig().apply {
            name = device.displayName
            host = device.ip
            port = device.port
            this.shareName = shareName
            this.username = username
            this.password = password
        }

        scanMovies(config, callback)
    }
}
