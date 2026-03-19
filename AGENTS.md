# NASMovie - AGENTS.md

## 项目概述

NASMovie 是一款基于 SMB 协议的 Android NAS 影视管理与播放应用。支持自动扫描媒体库、NFO 元数据解析、海报展示和 VLC 视频播放。

- **语言**: Java
- **最低 SDK**: Android 10 (API 29)
- **目标 SDK**: Android 14 (API 34)
- **架构**: 单 Activity + 多 Fragment/Activity 架构

## 核心依赖

| 库 | 版本 | 用途 |
|---|---|---|
| SMBJ | 0.13.0 | SMB 协议访问 |
| libVLC | 3.6.5 | 视频播放 (原生支持 SMB) |
| Glide | 4.16.0 | 图片加载与缓存 |
| Room | 2.6.1 | 本地数据库 |
| Material Design | 1.11.0 | UI 组件 |
| Gson | 2.10.1 | JSON 解析 |

## 项目结构

```
app/src/main/java/com/example/nasmovie/
├── NASMovieApp.java          # Application 入口，管理全局状态和应用锁
├── data/
│   ├── db/                   # Room 数据库 (AppDatabase, DAO)
│   ├── model/                # 数据实体 (Movie, SmbConfig, WatchProgress, Favorite)
│   ├── parser/               # NFO/字幕解析器
│   ├── repository/           # 数据仓库层
│   └── smb/                  # SMB 相关实现 (SmbClient, SmbScanner, SmbScanManager)
├── player/                   # 播放器组件 (PlayerGestureHandler, SubtitleManager)
├── service/                  # 业务服务 (ScanService, MovieService, PlayerService)
├── ui/                       # 界面层
│   ├── MainActivity.java     # 主 Activity，管理底部导航和 Fragment 切换
│   ├── VlcPlayerActivity.java # VLC 视频播放器
│   ├── LockActivity.java     # 应用锁验证页
│   ├── HomeFragment.java     # 首页
│   ├── DetailFragment.java   # 电影详情页
│   ├── FavoritesFragment.java # 收藏页
│   ├── SettingsFragment.java # 设置页
│   ├── SearchFragment.java   # 搜索页
│   └── adapter/              # RecyclerView 适配器
└── util/                     # 工具类 (FileUtils, PreferenceManager)
```

## 数据模型

### 核心实体

- **Movie**: 电影实体，包含标题、年份、时长、导演、演员、评分、海报路径、视频路径、字幕路径等
- **SmbConfig**: SMB 服务器配置，包含主机、端口、共享名、认证信息
- **WatchProgress**: 观看进度记录
- **Favorite**: 收藏记录

### 数据库版本: 5

## 构建与运行

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

### 构建命令

```bash
# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK (已配置签名)
./gradlew assembleRelease

# 清理构建
./gradlew clean

# 同步依赖
./gradlew sync
```

### 输出路径
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## 主要功能模块

### 1. SMB 服务器管理
- 多服务器配置管理
- 匿名/账号认证
- 连接测试
- 路径: `ServerManageFragment`, `ServerEditFragment`

### 2. 媒体库扫描
- SMB 设备发现 (IP 段扫描)
- 递归目录扫描 (嵌套扫描到影片为止)
- NFO 元数据解析
- 海报图片匹配
- 路径: `data/smb/SmbScanner.java`, `ScanService.java`

### 3. 视频播放
- VLC 原生 SMB 协议支持
- 手势控制 (亮度/音量/进度)
- 字幕加载与同步
- 播放进度记录与续播
- 路径: `VlcPlayerActivity.java`

### 4. 应用锁
- 4 位数字密码保护
- 后台切换自动锁定
- 防暴力破解 (5 次错误后等待 30 秒)
- 路径: `LockActivity.java`, `NASMovieApp.java`

## 开发规范

### Activity/Fragment 导航
- `MainActivity` 作为容器，管理三个主 Tab (首页、收藏、设置)
- 使用 `FragmentTransaction.hide()/show()` 进行 Tab 切换
- 子页面使用自定义 backStack 管理返回栈
- 详情页等子页面隐藏底部导航

### 数据库操作
- Room 数据库通过 `NASMovieApp.getDatabase()` 获取单例
- 数据库操作应在后台线程执行
- 使用 `Repository` 层封装数据访问

### SMB 文件访问
- 使用 `SmbClient` 建立连接
- 使用 `SmbScanner` 扫描文件
- 视频播放使用 VLC 的 SMB 原生支持

### 图片加载
- 海报使用 Glide 加载
- SMB 图片通过 `SmbImageCache` 缓存到本地

## 注意事项

### VLC 兼容性
- VLC 原生库 (libvlc.so) 目前未按 Android 15 的 16KB 页面对齐
- 在 Android 15+ 设备上可能需要等待 VLC 4.x 更新

### 签名配置
- Release 签名密钥: `nasmovie-key.jks`
- 密钥别名: `nasmovie`
- 密钥密码: `nasmovie123`

### 权限要求
- `INTERNET` - SMB 网络访问
- `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` - 网络状态
- `READ_EXTERNAL_STORAGE` - 读取本地缓存
- `WAKE_LOCK` - 播放时保持唤醒
- `FOREGROUND_SERVICE` - 后台扫描服务
- `POST_NOTIFICATIONS` - Android 13+ 通知

## 参考文档

- SMB 扫描功能详见: `SMB_SCAN_README.md`
- 建议搭配刮削器使用: [MDC-NG](https://github.com/mdc-ng/mdc-ng)
