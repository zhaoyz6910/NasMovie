package com.example.nasmovie.data.smb;

import android.content.Context;
import android.util.Log;

import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.util.AppExecutor;

import java.util.List;

/**
 * SMB 扫描管理器
 * 整合设备发现和文件扫描功能，提供简单易用的 API
 *
 * 使用示例：
 * <pre>
 * SmbScanManager scanManager = new SmbScanManager(context);
 *
 * // 1. 发现局域网 SMB 设备
 * scanManager.discoverDevices(new SmbDeviceScanner.DeviceScanCallback() {
 *     @Override
 *     public void onDeviceFound(SmbDeviceScanner.DiscoveredDevice device) {
 *         Log.d(TAG, "发现设备: " + device.getDisplayName() + " (" + device.getIp() + ")");
 *     }
 *
 *     @Override
 *     public void onScanComplete(List<SmbDeviceScanner.DiscoveredDevice> devices) {
 *         Log.d(TAG, "扫描完成，共发现 " + devices.size() + " 个设备");
 *     }
 * });
 *
 * // 2. 扫描 SMB 服务器上的电影文件
 * SmbConfig config = new SmbConfig();
 * config.setHost("192.168.1.100");
 * config.setUsername("admin");
 * config.setPassword("password");
 * config.setShareName("movies");
 *
 * scanManager.scanMovies(config, "/", new SmbScannerCallback() {
 *     @Override
 *     public void onVideoFound(SmbFileInfo fileInfo, ScanResult.ScannedMovie movie) {
 *         Log.d(TAG, "发现电影: " + movie.getName());
 *     }
 *
 *     @Override
 *     public void onScanComplete(ScanResult result) {
 *         Log.d(TAG, "扫描完成，共发现 " + result.getVideoFiles() + " 部电影");
 *     }
 * });
 * </pre>
 */
public class SmbScanManager {

    private static final String TAG = "SmbScanManager";

    private Context context;
    private SmbDeviceScanner deviceScanner;
    private SmbScanner fileScanner;

    public SmbScanManager(Context context) {
        this.context = context.getApplicationContext();
        this.deviceScanner = new SmbDeviceScanner();
        this.fileScanner = new SmbScanner();
    }

    // ==================== 设备发现 ====================

    /**
     * 快速扫描局域网 SMB 设备
     * 自动检测本机 IP 并扫描同网段
     */
    public void discoverDevices(SmbDeviceScanner.DeviceScanCallback callback) {
        deviceScanner.setCallback(callback);
        deviceScanner.quickScan();
    }

    /**
     * 扫描指定网段的 SMB 设备
     * @param subnet 网段，如 "192.168.1"
     */
    public void discoverDevices(String subnet, SmbDeviceScanner.DeviceScanCallback callback) {
        deviceScanner.setCallback(callback);
        deviceScanner.startScan(subnet);
    }

    /**
     * 扫描指定 IP 范围的 SMB 设备
     * @param subnet 网段，如 "192.168.1"
     * @param startIp 起始 IP（1-254）
     * @param endIp 结束 IP（1-254）
     */
    public void discoverDevices(String subnet, int startIp, int endIp,
                                  SmbDeviceScanner.DeviceScanCallback callback) {
        deviceScanner.setCallback(callback);
        deviceScanner.startScan(subnet, startIp, endIp);
    }

    /**
     * 取消设备发现
     */
    public void cancelDeviceDiscovery() {
        deviceScanner.cancelScan();
    }

    /**
     * 是否正在发现设备
     */
    public boolean isDiscovering() {
        return deviceScanner.isScanning();
    }

    // ==================== 文件扫描 ====================

    /**
     * 扫描 SMB 服务器上的电影文件
     * @param config SMB 配置
     * @param callback 扫描回调
     */
    public void scanMovies(SmbConfig config, SmbScannerCallback callback) {
        scanMovies(config, "/", true, callback);
    }

    /**
     * 扫描 SMB 服务器上的电影文件
     * @param config SMB 配置
     * @param startPath 起始路径
     * @param recursive 是否递归扫描
     * @param callback 扫描回调
     */
    public void scanMovies(SmbConfig config, String startPath, boolean recursive,
                           SmbScannerCallback callback) {
        fileScanner.setCallback(callback);
        fileScanner.startScan(config, startPath, recursive);
    }

    /**
     * 取消文件扫描
     */
    public void cancelFileScan() {
        fileScanner.cancelScan();
    }

    /**
     * 是否正在扫描文件
     */
    public boolean isScanningFiles() {
        return fileScanner.isScanning();
    }

    // ==================== 测试连接 ====================

    /**
     * 测试 SMB 连接
     * @param config SMB 配置
     * @return 连接是否成功
     */
    public boolean testConnection(SmbConfig config) {
        SmbClient client = new SmbClient();
        boolean result = client.connect(config);
        client.disconnect();
        return result;
    }

    /**
     * 异步测试 SMB 连接
     * @param config SMB 配置
     * @param listener 结果监听
     */
    public void testConnectionAsync(SmbConfig config, ConnectionTestListener listener) {
        AppExecutor.getInstance().runOnNetworkIO(() -> {
            SmbClient client = new SmbClient();
            boolean result = client.connect(config);
            client.disconnect();

            if (listener != null) {
                android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                handler.post(() -> listener.onResult(result));
            }
        });
    }

    /**
     * 连接测试监听器
     */
    public interface ConnectionTestListener {
        void onResult(boolean success);
    }

    // ==================== 资源释放 ====================

    /**
     * 释放资源
     * 在 Activity/Fragment 销毁时调用
     */
    public void release() {
        Log.d(TAG, "Releasing resources");
        deviceScanner.release();
        fileScanner.release();
    }

    // ==================== 便捷方法 ====================

    /**
     * 根据发现的创建设备创建 SmbConfig
     */
    public SmbConfig createConfigFromDevice(SmbDeviceScanner.DiscoveredDevice device) {
        SmbConfig config = new SmbConfig();
        config.setName(device.getDisplayName());
        config.setHost(device.getIp());
        config.setPort(device.getPort());
        config.setShareName(""); // 需要用户填写
        return config;
    }

    /**
     * 扫描特定设备
     * 组合设备信息和文件扫描
     */
    public void scanDevice(SmbDeviceScanner.DiscoveredDevice device, String shareName,
                           String username, String password, SmbScannerCallback callback) {
        SmbConfig config = new SmbConfig();
        config.setName(device.getDisplayName());
        config.setHost(device.getIp());
        config.setPort(device.getPort());
        config.setShareName(shareName);
        config.setUsername(username);
        config.setPassword(password);

        scanMovies(config, callback);
    }
}
