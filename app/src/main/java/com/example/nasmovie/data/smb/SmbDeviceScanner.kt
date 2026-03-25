package com.example.nasmovie.data.smb

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.nasmovie.util.AppConstants
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * SMB 设备扫描器
 * 用于自动发现局域网中的 SMB 服务器
 * 支持 IP 段扫描和端口探测
 */
class SmbDeviceScanner {

    // 扫描状态
    @Volatile
    var isScanning = false
        private set
    @Volatile
    private var isCancelled = false

    // 线程池
    private var executorService: ExecutorService? = null

    // 回调
    private var callback: DeviceScanCallback? = null

    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    // 发现的设备列表
    private val discoveredDevices = mutableListOf<DiscoveredDevice>()

    /**
     * 设置扫描回调
     */
    fun setCallback(callback: DeviceScanCallback) {
        this.callback = callback
    }

    /**
     * 开始扫描局域网中的 SMB 服务器
     * @param subnet 网段，如 "192.168.1"
     */
    fun startScan(subnet: String) {
        startScan(subnet, 1, 254)
    }

    /**
     * 开始扫描指定 IP 范围
     * @param subnet 网段，如 "192.168.1"
     * @param startIp 起始 IP（1-254）
     * @param endIp 结束 IP（1-254）
     */
    fun startScan(subnet: String, startIp: Int, endIp: Int) {
        if (isScanning) {
            Log.w(TAG, "Scan already in progress")
            return
        }

        isScanning = true
        isCancelled = false
        discoveredDevices.clear()

        // 创建线程池
        executorService = Executors.newFixedThreadPool(AppConstants.SMB_SCAN_THREAD_POOL_SIZE)

        // 通知开始
        notifyScanStart()

        // 提交扫描任务
        executorService?.execute {
            try {
                scanIpRange(subnet, startIp, endIp)
            } catch (e: Exception) {
                Log.e(TAG, "Scan error: ${e.message}", e)
                notifyScanError(e.message ?: "Unknown error")
            } finally {
                isScanning = false
                if (!isCancelled) {
                    notifyScanComplete(ArrayList(discoveredDevices))
                }
                shutdownExecutor()
            }
        }
    }

    /**
     * 扫描 IP 段
     */
    private fun scanIpRange(subnet: String, startIp: Int, endIp: Int) {
        val total = endIp - startIp + 1
        val futures = mutableListOf<java.util.concurrent.Future<DiscoveredDevice?>>()

        // 提交所有扫描任务
        for (i in startIp..endIp) {
            if (isCancelled) break

            val ip = "$subnet.$i"
            val progress = i - startIp + 1

            // 更新进度
            notifyScanProgress(progress, total, ip)

            // 提交扫描任务
            val task = Callable { scanIp(ip) }
            futures.add(executorService!!.submit(task))
        }

        // 收集结果
        for (future in futures) {
            if (isCancelled) break

            try {
                future.get()?.let { device ->
                    synchronized(discoveredDevices) {
                        // 检查是否已存在
                        val exists = discoveredDevices.any { it.ip == device.ip }
                        if (!exists) {
                            discoveredDevices.add(device)
                            notifyDeviceFound(device)
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Log.w(TAG, "Scan interrupted: ${e.message}")
            } catch (e: ExecutionException) {
                Log.w(TAG, "Error getting scan result: ${e.message}")
            }
        }
    }

    /**
     * 扫描单个 IP
     */
    private fun scanIp(ip: String): DiscoveredDevice? {
        // 先检查 SMB 445 端口
        if (isPortOpen(ip, AppConstants.SMB_PORT)) {
            Log.d(TAG, "Found SMB server")

            val device = DiscoveredDevice(
                ip = ip,
                port = AppConstants.SMB_PORT,
                type = DiscoveredDevice.Type.SMB
            )

            // 尝试获取主机名
            val hostname = resolveHostname(ip)
            if (hostname != null) {
                device.hostname = hostname
                device.name = hostname
            } else {
                device.name = "SMB Server $ip"
            }

            // 尝试 NetBIOS 端口是否开放
            device.netbiosOpen = isPortOpen(ip, AppConstants.NETBIOS_PORT)

            return device
        }

        return null
    }

    /**
     * 检查端口是否开放
     */
    private fun isPortOpen(ip: String, port: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), AppConstants.SMB_CONNECT_TIMEOUT)
                true
            }
        } catch (e: IOException) {
            false
        }
    }

    /**
     * 解析主机名
     */
    private fun resolveHostname(ip: String): String? {
        return try {
            val address = InetAddress.getByName(ip)
            val hostname = address.hostName
            // 如果主机名和 IP 相同，则返回 null
            if (hostname == ip) null else hostname
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 快速扫描默认网段
     * 自动检测本机 IP 并扫描同网段
     */
    fun quickScan() {
        val localIp = getLocalIpAddress()
        if (localIp == null) {
            notifyScanError("Could not get local IP address")
            return
        }

        // 提取网段
        val subnet = localIp.substringBeforeLast(".")
        Log.d(TAG, "Quick scanning subnet: $subnet")

        startScan(subnet)
    }

    /**
     * 获取本机 IP 地址
     */
    private fun getLocalIpAddress(): String? {
        return try {
            var wlanInterface = NetworkInterface.getByName("wlan0")
            if (wlanInterface == null) {
                wlanInterface = NetworkInterface.getByName("eth0")
            }

            wlanInterface?.inetAddresses?.asSequence()
                ?.filter { !it.isLoopbackAddress && it is Inet4Address }
                ?.firstOrNull()
                ?.hostAddress
                ?: InetAddress.getLocalHost().hostAddress
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP: ${e.message}")
            null
        }
    }

    /**
     * 取消扫描
     */
    fun cancelScan() {
        Log.d(TAG, "Cancelling device scan")
        isCancelled = true
        isScanning = false
        shutdownExecutor()
    }

    /**
     * 关闭线程池
     */
    private fun shutdownExecutor() {
        executorService?.let { executor ->
            if (!executor.isShutdown) {
                executor.shutdown()
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    executor.shutdownNow()
                }
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        cancelScan()
    }

    // ==================== 回调通知方法 ====================

    private fun notifyScanStart() {
        callback?.let { mainHandler.post { it.onScanStart() } }
    }

    private fun notifyDeviceFound(device: DiscoveredDevice) {
        callback?.let { mainHandler.post { it.onDeviceFound(device) } }
    }

    private fun notifyScanProgress(current: Int, total: Int, ip: String) {
        callback?.let { mainHandler.post { it.onScanProgress(current, total, ip) } }
    }

    private fun notifyScanComplete(devices: List<DiscoveredDevice>) {
        callback?.let { mainHandler.post { it.onScanComplete(devices) } }
    }

    private fun notifyScanError(error: String) {
        isScanning = false
        callback?.let { mainHandler.post { it.onScanError(error) } }
    }

    /**
     * 发现的设备信息
     */
    data class DiscoveredDevice(
        var name: String? = null,
        var ip: String = "",
        var port: Int = AppConstants.SMB_PORT,
        var hostname: String? = null,
        var type: Type = Type.UNKNOWN,
        var netbiosOpen: Boolean = false,
        var requiresAuth: Boolean = false
    ) {
        enum class Type {
            SMB,    // SMB 服务器
            NAS,    // NAS 设备
            PC,     // 电脑
            UNKNOWN // 未知类型
        }

        /**
         * 获取显示名称
         */
        val displayName: String
            get() = when {
                !name.isNullOrEmpty() -> name!!
                !hostname.isNullOrEmpty() -> hostname!!
                else -> ip
            }
    }

    /**
     * 回调接口
     */
    interface DeviceScanCallback {
        fun onScanStart()
        fun onDeviceFound(device: DiscoveredDevice)
        fun onScanProgress(current: Int, total: Int, ip: String)
        fun onScanComplete(devices: List<DiscoveredDevice>)
        fun onScanError(error: String)
    }

    companion object {
        private const val TAG = "SmbDeviceScanner"
    }
}