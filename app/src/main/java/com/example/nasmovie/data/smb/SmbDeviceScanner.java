package com.example.nasmovie.data.smb;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * SMB 设备扫描器
 * 用于自动发现局域网中的 SMB 服务器
 * 支持 IP 段扫描和端口探测
 */
public class SmbDeviceScanner {

    private static final String TAG = "SmbDeviceScanner";

    // SMB 默认端口
    private static final int SMB_PORT = 445;
    private static final int NETBIOS_PORT = 139;

    // 扫描超时时间（毫秒）
    private static final int CONNECT_TIMEOUT = 1000;

    // 线程池大小
    private static final int THREAD_POOL_SIZE = 50;

    // 回调接口
    public interface DeviceScanCallback {
        /**
         * 扫描开始
         */
        void onScanStart();

        /**
         * 发现设备
         * @param device 发现的设备
         */
        void onDeviceFound(DiscoveredDevice device);

        /**
         * 扫描进度
         * @param current 当前进度
         * @param total 总数
         * @param ip 当前扫描的 IP
         */
        void onScanProgress(int current, int total, String ip);

        /**
         * 扫描完成
         * @param devices 所有发现的设备
         */
        void onScanComplete(List<DiscoveredDevice> devices);

        /**
         * 扫描出错
         * @param error 错误信息
         */
        void onScanError(String error);
    }

    // 扫描状态
    private volatile boolean isScanning = false;
    private volatile boolean isCancelled = false;

    // 线程池
    private ExecutorService executorService;

    // 回调
    private DeviceScanCallback callback;

    // 主线程 Handler
    private Handler mainHandler;

    // 发现的设备列表
    private List<DiscoveredDevice> discoveredDevices;

    public SmbDeviceScanner() {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.discoveredDevices = new ArrayList<>();
    }

    /**
     * 设置扫描回调
     */
    public void setCallback(DeviceScanCallback callback) {
        this.callback = callback;
    }

    /**
     * 开始扫描局域网中的 SMB 服务器
     * @param subnet 网段，如 "192.168.1"
     */
    public void startScan(String subnet) {
        startScan(subnet, 1, 254);
    }

    /**
     * 开始扫描指定 IP 范围
     * @param subnet 网段，如 "192.168.1"
     * @param startIp 起始 IP（1-254）
     * @param endIp 结束 IP（1-254）
     */
    public void startScan(String subnet, int startIp, int endIp) {
        if (isScanning) {
            Log.w(TAG, "Scan already in progress");
            return;
        }

        isScanning = true;
        isCancelled = false;
        discoveredDevices.clear();

        // 创建线程池
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // 通知开始
        notifyScanStart();

        // 提交扫描任务
        executorService.execute(() -> {
            try {
                scanIpRange(subnet, startIp, endIp);
            } catch (Exception e) {
                Log.e(TAG, "Scan error: " + e.getMessage(), e);
                notifyScanError(e.getMessage());
            } finally {
                isScanning = false;
                if (!isCancelled) {
                    notifyScanComplete(new ArrayList<>(discoveredDevices));
                }
                shutdownExecutor();
            }
        });
    }

    /**
     * 扫描 IP 段
     */
    private void scanIpRange(String subnet, int startIp, int endIp) {
        int total = endIp - startIp + 1;
        List<Future<DiscoveredDevice>> futures = new ArrayList<>();

        // 提交所有扫描任务
        for (int i = startIp; i <= endIp; i++) {
            if (isCancelled) {
                break;
            }

            final String ip = subnet + "." + i;
            final int progress = i - startIp + 1;

            // 更新进度
            notifyScanProgress(progress, total, ip);

            // 提交扫描任务
            Callable<DiscoveredDevice> task = () -> scanIp(ip);
            futures.add(executorService.submit(task));
        }

        // 收集结果
        for (Future<DiscoveredDevice> future : futures) {
            if (isCancelled) {
                break;
            }

            try {
                DiscoveredDevice device = future.get();
                if (device != null) {
                    synchronized (discoveredDevices) {
                        // 检查是否已存在
                        boolean exists = false;
                        for (DiscoveredDevice d : discoveredDevices) {
                            if (d.getIp().equals(device.getIp())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            discoveredDevices.add(device);
                            notifyDeviceFound(device);
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                Log.w(TAG, "Error getting scan result: " + e.getMessage());
            }
        }
    }

    /**
     * 扫描单个 IP
     */
    private DiscoveredDevice scanIp(String ip) {
        // 先检查 SMB 445 端口
        if (isPortOpen(ip, SMB_PORT)) {
            Log.d(TAG, "Found SMB server at " + ip + ":" + SMB_PORT);

            DiscoveredDevice device = new DiscoveredDevice();
            device.setIp(ip);
            device.setPort(SMB_PORT);
            device.setType(DiscoveredDevice.Type.SMB);

            // 尝试获取主机名
            String hostname = resolveHostname(ip);
            if (hostname != null) {
                device.setHostname(hostname);
                device.setName(hostname);
            } else {
                device.setName("SMB Server " + ip);
            }

            // 尝试 NetBIOS 端口是否开放
            device.setNetbiosOpen(isPortOpen(ip, NETBIOS_PORT));

            return device;
        }

        return null;
    }

    /**
     * 检查端口是否开放
     */
    private boolean isPortOpen(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 解析主机名
     */
    private String resolveHostname(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            String hostname = address.getHostName();
            // 如果主机名和 IP 相同，则返回 null
            if (hostname.equals(ip)) {
                return null;
            }
            return hostname;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 快速扫描默认网段
     * 自动检测本机 IP 并扫描同网段
     */
    public void quickScan() {
        String localIp = getLocalIpAddress();
        if (localIp == null) {
            notifyScanError("Could not get local IP address");
            return;
        }

        // 提取网段
        String subnet = localIp.substring(0, localIp.lastIndexOf("."));
        Log.d(TAG, "Quick scanning subnet: " + subnet);

        startScan(subnet);
    }

    /**
     * 获取本机 IP 地址
     */
    private String getLocalIpAddress() {
        try {
            java.net.NetworkInterface wlanInterface = java.net.NetworkInterface.getByName("wlan0");
            if (wlanInterface == null) {
                // 尝试其他常见接口名
                wlanInterface = java.net.NetworkInterface.getByName("eth0");
            }

            if (wlanInterface != null) {
                java.util.Enumeration<java.net.InetAddress> addresses = wlanInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }

            // 备用方案
            java.net.InetAddress localhost = java.net.InetAddress.getLocalHost();
            return localhost.getHostAddress();

        } catch (Exception e) {
            Log.e(TAG, "Error getting local IP: " + e.getMessage());
            return null;
        }
    }

    /**
     * 取消扫描
     */
    public void cancelScan() {
        Log.d(TAG, "Cancelling device scan");
        isCancelled = true;
        isScanning = false;
        shutdownExecutor();
    }

    /**
     * 是否正在扫描
     */
    public boolean isScanning() {
        return isScanning;
    }

    /**
     * 关闭线程池
     */
    private void shutdownExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        cancelScan();
    }

    // ==================== 回调通知方法 ====================

    private void notifyScanStart() {
        if (callback != null) {
            mainHandler.post(() -> callback.onScanStart());
        }
    }

    private void notifyDeviceFound(DiscoveredDevice device) {
        if (callback != null) {
            mainHandler.post(() -> callback.onDeviceFound(device));
        }
    }

    private void notifyScanProgress(int current, int total, String ip) {
        if (callback != null) {
            mainHandler.post(() -> callback.onScanProgress(current, total, ip));
        }
    }

    private void notifyScanComplete(List<DiscoveredDevice> devices) {
        if (callback != null) {
            mainHandler.post(() -> callback.onScanComplete(devices));
        }
    }

    private void notifyScanError(String error) {
        isScanning = false;
        if (callback != null) {
            mainHandler.post(() -> callback.onScanError(error));
        }
    }

    /**
     * 发现的设备信息
     */
    public static class DiscoveredDevice {

        public enum Type {
            SMB,            // SMB 服务器
            NAS,            // NAS 设备
            PC,             // 电脑
            UNKNOWN         // 未知类型
        }

        private String name;            // 设备名称
        private String ip;              // IP 地址
        private int port;               // 端口
        private String hostname;        // 主机名
        private Type type;              // 设备类型
        private boolean netbiosOpen;    // NetBIOS 端口是否开放
        private boolean requiresAuth;   // 是否需要认证

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public boolean isNetbiosOpen() {
            return netbiosOpen;
        }

        public void setNetbiosOpen(boolean netbiosOpen) {
            this.netbiosOpen = netbiosOpen;
        }

        public boolean isRequiresAuth() {
            return requiresAuth;
        }

        public void setRequiresAuth(boolean requiresAuth) {
            this.requiresAuth = requiresAuth;
        }

        /**
         * 获取显示名称
         */
        public String getDisplayName() {
            if (name != null && !name.isEmpty()) {
                return name;
            }
            if (hostname != null && !hostname.isEmpty()) {
                return hostname;
            }
            return ip;
        }

        @Override
        public String toString() {
            return "DiscoveredDevice{" +
                "name='" + name + '\'' +
                ", ip='" + ip + '\'' +
                ", port=" + port +
                ", hostname='" + hostname + '\'' +
                ", type=" + type +
                ", netbiosOpen=" + netbiosOpen +
                '}';
        }
    }
}
