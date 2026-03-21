# NAS影视库

一款基于 SMB 协议的 NAS 影视管理与播放应用，支持自动扫描、元数据解析、海报展示和视频播放。

> 建议搭配刮削器使用：[MDC-NG](https://github.com/mdc-ng/mdc-ng)

## 功能特性

### 🎬 影视管理
- **自动扫描媒体库** - 扫描 SMB 服务器上的电影文件，自动识别海报和元数据
- **智能嵌套扫描** - 扫描到影片目录后自动停止深入，避免扫描无用的预告片/花絮目录
- **NFO 元数据解析** - 支持 Kodi 格式的 NFO 文件，自动提取电影信息
- **海报展示** - 自动匹配 poster.jpg、folder.jpg、cover.jpg 等海报图片
- **电影详情** - 展示电影名称、年份、时长、导演、演员、剧情简介、评分等信息

### 📁 SMB 支持
- **多服务器管理** - 支持添加多个 NAS/SMB 服务器
- **设备发现** - 自动扫描局域网内的 SMB 设备
- **匿名/账号登录** - 支持匿名访问和账号密码登录
- **自定义路径** - 可配置共享文件夹和电影子目录
- **连接测试** - 保存前可测试服务器连接状态

### 🎥 视频播放
- **ExoPlayer 播放器** - 基于 Media3 的现代播放器，体积更小、性能更优
- **SMB 直接播放** - 无需下载，直接流式播放 SMB 上的视频
- **播放控制** - 支持播放/暂停、快进/快退、进度拖动
- **手势操作** - 支持音量、亮度、进度的手势调节
- **字幕选择** - 自动识别并选择同目录下的字幕文件
- **字幕位置自适应** - 根据控制栏显示/隐藏自动调整字幕位置

### 📌 收藏与记录
- **收藏功能** - 独立收藏 Tab，快速访问喜爱的电影
- **观看记录** - 自动记录观看进度，支持续播
- **搜索功能** - 按电影名称搜索
- **多种排序** - 支持按标题、添加时间、年份、评分、时长、文件大小排序

### 🔒 隐私保护
- **应用锁** - 4位数字密码保护，应用切后台后自动锁定
- **防暴力破解** - 连续5次错误密码后需等待30秒

### 🎨 界面设置
- **主题模式** - 支持浅色/深色/跟随系统三种主题
- **响应式布局** - 适配手机和平板设备

## 技术架构

### 技术栈
- **语言**: Java
- **最低 SDK**: Android 10 (API 29)
- **目标 SDK**: Android 14 (API 34)

### 核心依赖

| 库 | 版本 | 用途 |
|---|---|---|
| SMBJ | 0.13.0 | SMB 协议访问 |
| Media3 (ExoPlayer) | 1.3.1 | 视频播放 |
| Glide | 4.16.0 | 图片加载与缓存 |
| Room | 2.6.1 | 本地数据库 |
| Material Design | 1.11.0 | UI 组件 |
| Gson | 2.10.1 | JSON 解析 |

### 项目结构

```
app/src/main/java/com/example/nasmovie/
├── NASMovieApp.java          # Application 入口
├── data/
│   ├── db/                   # Room 数据库 (AppDatabase, DAO)
│   ├── local/                # 本地数据源
│   ├── model/                # 数据实体 (Movie, SmbConfig, WatchProgress, Favorite)
│   ├── parser/               # NFO/字幕解析器
│   ├── repository/           # 数据仓库层
│   └── smb/                  # SMB 相关实现 (SmbClient, SmbScanner, SmbDeviceScanner)
├── player/                   # 播放器组件 (PlayerGestureHandler, SubtitleManager)
├── service/                  # 业务服务 (ScanService, MovieService, PlayerService)
├── ui/                       # 界面层
│   ├── MainActivity.java     # 主 Activity
│   ├── ExoPlayerActivity.java # ExoPlayer 视频播放器
│   ├── LockActivity.java     # 应用锁验证页
│   ├── HomeFragment.java     # 首页
│   ├── DetailFragment.java   # 电影详情页
│   ├── FavoritesFragment.java # 收藏页
│   ├── SettingsFragment.java # 设置页
│   ├── SearchFragment.java   # 搜索页
│   ├── adapter/               # RecyclerView 适配器
│   └── widget/               # 自定义控件
└── util/                     # 工具类
```

## 使用说明

### 1. 添加服务器
1. 进入「设置」→「服务器管理」
2. 点击右下角「+」添加服务器
3. 填写服务器信息：
   - 名称：自定义显示名称
   - IP地址：NAS 的 IP
   - 端口：默认 445
   - 共享文件夹：SMB 共享名
   - 电影路径：电影存放的子目录（可选）
   - 用户名/密码：如需要认证则填写
4. 点击「测试连接」验证
5. 保存配置

### 2. 扫描媒体库
1. 返回主界面
2. 点击右上角菜单 →「扫描媒体库」
3. 等待扫描完成
4. 海报和电影信息将自动显示在首页

### 3. 播放电影
1. 点击电影海报进入详情页
2. 点击「播放」按钮开始播放
3. 播放界面支持手势：
   - 左侧上下滑：调节亮度
   - 右侧上下滑：调节音量
   - 左右滑动：快进/快退

### 4. 开启应用锁
1. 进入「设置」→「应用锁」
2. 开启开关
3. 设置 4 位数字密码
4. 确认密码完成设置

## 文件命名规范

为了获得最佳体验，建议按以下规范整理电影文件：

```
电影目录/
├── 电影名称 (年份)/
│   ├── 电影名称 (年份).mkv    # 视频文件
│   ├── 电影名称 (年份).nfo    # 元数据文件
│   ├── poster.jpg             # 海报图片
│   ├── thumb.jpg              # 缩略图
│   └── 字幕文件.ass/srt       # 字幕文件（可选）
```

## 构建说明

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34

### 构建步骤

1. 克隆项目
```bash
git clone <repository-url>
```

2. 使用 Android Studio 打开项目

3. 同步 Gradle 依赖
```bash
./gradlew sync
```

4. 编译 APK
```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本（需配置签名）
./gradlew assembleRelease
```

### 签名配置

Release 签名需要在项目根目录创建 `keystore.properties` 文件：

```properties
storePassword=你的密钥库密码
keyAlias=nasmovie
keyPassword=你的密钥密码
```

### 输出路径
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`

## 权限说明

| 权限 | 用途 |
|---|---|
| INTERNET | 访问 SMB 服务器和网络资源 |
| ACCESS_NETWORK_STATE | 检测网络连接状态 |
| ACCESS_WIFI_STATE | 获取 WiFi 信息用于设备发现 |
| READ_EXTERNAL_STORAGE | 读取本地缓存的海报图片 |
| WAKE_LOCK | 播放视频时保持屏幕唤醒 |
| FOREGROUND_SERVICE | 后台扫描服务 |
| POST_NOTIFICATIONS | Android 13+ 扫描进度通知 |
| VIBRATE | 密码错误震动反馈 |

## 更新日志

### v1.5
- ✅ 播放器从 VLC 迁移到 ExoPlayer (Media3)
- ✅ 优化应用体积
- ✅ 提升播放性能和稳定性

### v1.1
- ✅ 字幕位置自适应调整
- ✅ 播放器手势控制优化

### v1.0
- ✅ SMB 服务器管理与连接
- ✅ 自动扫描媒体库
- ✅ NFO 元数据解析
- ✅ 海报和缩略图缓存
- ✅ VLC 视频播放
- ✅ 播放手势控制
- ✅ 观看进度记录
- ✅ 收藏功能
- ✅ 应用锁保护

## 开源协议

本项目基于 MIT 协议开源。

## 致谢

- [SMBJ](https://github.com/hierynomus/smbj) - SMB 协议 Java 实现
- [Media3/ExoPlayer](https://developer.android.com/media/media3) - Android 官方播放器
- [Glide](https://github.com/bumptech/glide) - 图片加载库
- [Room](https://developer.android.com/training/data-storage/room) - Android 数据库框架