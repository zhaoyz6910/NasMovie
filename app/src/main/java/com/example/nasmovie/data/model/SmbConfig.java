package com.example.nasmovie.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * SMB服务器配置实体
 */
@Entity(tableName = "smb_config")
public class SmbConfig {

    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;           // 服务器名称
    private String host;           // IP地址
    private int port;              // 端口（默认445）
    private String username;       // 用户名
    private String password;       // 密码
    private String shareName;      // 共享文件夹名
    private String moviePath;      // 电影目录路径
    private boolean isDefault;     // 默认服务器
    private long lastConnectTime;  // 最后连接时间

    public SmbConfig() {
        this.port = 445;
        this.isDefault = false;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getShareName() {
        return shareName;
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public String getMoviePath() {
        return moviePath;
    }

    public void setMoviePath(String moviePath) {
        this.moviePath = moviePath;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(boolean aDefault) {
        isDefault = aDefault;
    }

    public long getLastConnectTime() {
        return lastConnectTime;
    }

    public void setLastConnectTime(long lastConnectTime) {
        this.lastConnectTime = lastConnectTime;
    }

    /**
     * 是否匿名登录
     */
    public boolean isAnonymous() {
        return username == null || username.isEmpty();
    }

    /**
     * 获取完整的SMB路径
     */
    public String getSmbPath() {
        return "smb://" + host + "/" + shareName + "/" + (moviePath != null ? moviePath : "");
    }
}