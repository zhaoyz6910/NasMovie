# SMB 扫描功能实现文档

基于 infuse-android 项目参考实现的 SMB 扫描功能。

## 文件结构

```
app/src/main/java/com/example/nasmovie/data/smb/
├── SmbClient.java          # SMB 客户端封装（已存在，基于 SMBJ 库）
├── SmbFileInfo.java        # SMB 文件信息模型（已存在）
├── SmbFileReader.java      # SMB 文件读取器（已存在）
├── ScanResult.java         # 扫描结果类（新增）
├── SmbScanner.java         # SMB 文件扫描器（新增）
├── SmbScannerCallback.java # 扫描回调接口（新增）
├── SmbDeviceScanner.java   # SMB 设备发现扫描器（新增）
└── SmbScanManager.java     # 扫描管理器（新增，整合功能）
```

## 功能特性

### 1. SMB 设备发现 (SmbDeviceScanner)

- **IP 段扫描**：扫描指定网段的 SMB 服务器
- **端口探测**：检测 SMB 445 端口和 NetBIOS 139 端口
- **主机名解析**：自动解析设备主机名
- **快速扫描**：自动检测本机 IP 并扫描同网段

### 2. SMB 文件扫描 (SmbScanner)

- **嵌套扫描到影片为止**：扫描目录时，如果当前目录包含视频文件，则停止继续深入扫描子目录，直接处理该目录的视频文件并返回。这样可以提高扫描效率，避免扫描过深的目录结构。
- **递归扫描**：支持递归扫描子目录（遇到视频文件会停止深入）
- **视频文件识别**：支持多种视频格式（mp4, mkv, avi, mov, wmv, flv, webm, m4v, 3gp, mpg, mpeg, m2ts, ts, mts 等）
- **NFO 文件匹配**：自动匹配同名 NFO 元数据文件
- **海报图片匹配**：自动查找 poster.jpg, folder.jpg, cover.jpg, fanart.jpg 等
- **扫描进度回调**：实时返回扫描进度

### 3. 扫描管理器 (SmbScanManager)

- **统一 API**：整合设备发现和文件扫描功能
- **异步操作**：所有操作在后台线程执行
- **主线程回调**：所有回调在主线程执行，可直接更新 UI

## 使用示例

### 1. 发现局域网 SMB 设备

```java
SmbScanManager scanManager = new SmbScanManager(context);

// 快速扫描（自动检测本机网段）
scanManager.discoverDevices(new SmbDeviceScanner.DeviceScanCallback() {
    @Override
    public void onScanStart() {
        Log.d(TAG, "开始扫描设备...");
    }

    @Override
    public void onDeviceFound(SmbDeviceScanner.DiscoveredDevice device) {
        Log.d(TAG, "发现设备: " + device.getDisplayName() +
            " (" + device.getIp() + ":" + device.getPort() + ")");
    }

    @Override
    public void onScanProgress(int current, int total, String ip) {
        Log.d(TAG, "扫描进度: " + current + "/" + total + " - " + ip);
    }

    @Override
    public void onScanComplete(List<SmbDeviceScanner.DiscoveredDevice> devices) {
        Log.d(TAG, "扫描完成，共发现 " + devices.size() + " 个设备");
        // 更新 UI
    }

    @Override
    public void onScanError(String error) {
        Log.e(TAG, "扫描出错: " + error);
    }
});

// 取消扫描
// scanManager.cancelDeviceDiscovery();
```

### 2. 扫描 SMB 服务器上的电影

```java
// 创建 SMB 配置
SmbConfig config = new SmbConfig();
config.setName("我的 NAS");
config.setHost("192.168.1.100");
config.setPort(445);
config.setUsername("admin");
config.setPassword("password");
config.setShareName("movies");

// 开始扫描
scanManager.scanMovies(config, "/", true, new SmbScannerCallback() {
    @Override
    public void onScanStart() {
        Log.d(TAG, "开始扫描电影...");
    }

    @Override
    public void onScanProgress(int current, int total, String currentPath) {
        Log.d(TAG, "扫描进度: " + current + " - " + currentPath);
    }

    @Override
    public void onVideoFound(SmbFileInfo fileInfo, ScanResult.ScannedMovie movie) {
        Log.d(TAG, "发现电影: " + movie.getName() +
            ", 大小: " + movie.getVideoSize() +
            ", NFO: " + movie.hasNfo() +
            ", 海报: " + movie.hasPoster());
    }

    @Override
    public void onNfoFound(SmbFileInfo fileInfo) {
        Log.d(TAG, "发现 NFO: " + fileInfo.getName());
    }

    @Override
    public void onPosterFound(SmbFileInfo fileInfo) {
        Log.d(TAG, "发现海报: " + fileInfo.getName());
    }

    @Override
    public void onScanComplete(ScanResult result) {
        Log.d(TAG, "扫描完成!");
        Log.d(TAG, "  总文件: " + result.getTotalFiles());
        Log.d(TAG, "  视频文件: " + result.getVideoFiles());
        Log.d(TAG, "  NFO 文件: " + result.getNfoFiles());
        Log.d(TAG, "  海报文件: " + result.getPosterFiles());
        Log.d(TAG, "  耗时: " + result.getFormattedScanTime());
        Log.d(TAG, "  速度: " + result.getScanSpeed());

        // 获取扫描到的电影列表
        List<ScanResult.ScannedMovie> movies = result.getMovies();
        for (ScanResult.ScannedMovie movie : movies) {
            Log.d(TAG, "电影: " + movie.getName());
        }
    }

    @Override
    public void onScanError(String error) {
        Log.e(TAG, "扫描出错: " + error);
    }

    @Override
    public void onScanCancelled() {
        Log.d(TAG, "扫描已取消");
    }
});

// 取消扫描
// scanManager.cancelFileScan();
```

### 嵌套扫描工作原理

扫描器采用"嵌套扫描到影片为止"的策略，适用于典型的媒体库目录结构：

```
/movies/
├── 动作片/
│   ├── 碟中谍7.mp4      ← 扫描到这里停止深入
│   ├── 碟中谍7.nfo
│   └── poster.jpg
│   └── 预告片/           ← 不会扫描这个子目录
│       └── trailer.mp4
├── 科幻片/
│   └── 星际穿越.mp4      ← 扫描到这里停止深入
│   └── 星际穿越.nfo
└── 纪录片/               ← 会继续扫描（因为当前目录没有视频文件）
    └── 地球脉动/
        └── 第一集.mp4    ← 扫描到这里停止深入
```

**扫描规则**：
1. 扫描当前目录下的所有文件
2. 如果当前目录包含视频文件，处理这些视频文件并**停止继续扫描子目录**
3. 如果当前目录没有视频文件，继续递归扫描子目录
4. 这样可以避免扫描无用的子目录（如预告片、花絮等），提高扫描效率

### 3. 测试 SMB 连接

```java
// 同步测试
boolean success = scanManager.testConnection(config);
if (success) {
    Log.d(TAG, "连接成功!");
} else {
    Log.e(TAG, "连接失败");
}

// 异步测试
scanManager.testConnectionAsync(config, new SmbScanManager.ConnectionTestListener() {
    @Override
    public void onResult(boolean success) {
        // 在主线程回调
        if (success) {
            Log.d(TAG, "连接成功!");
        } else {
            Log.e(TAG, "连接失败");
        }
    }
});
```

### 4. 生命周期管理

```java
public class MainActivity extends AppCompatActivity {
    private SmbScanManager scanManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scanManager = new SmbScanManager(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放资源，停止所有扫描
        scanManager.release();
    }
}
```

## 依赖库

```gradle
dependencies {
    // SMB 协议（已存在）
    implementation 'com.hierynomus:smbj:0.13.0'
}
```

## 权限要求

```xml
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />

<!-- Android 6.0+ 需要动态申请 -->
```

## 注意事项

1. **线程安全**：所有扫描操作在后台线程执行，回调在主线程执行
2. **资源释放**：在 Activity/Fragment 销毁时调用 `release()` 方法
3. **超时设置**：设备扫描的连接超时为 1000ms，可根据网络环境调整
4. **线程池大小**：设备扫描使用 50 个线程的线程池，可根据设备性能调整

## 参考项目

- infuse-android 项目：`D:\Project\infuse-android`
- 基于 SMBJ 库实现 SMB 协议支持
