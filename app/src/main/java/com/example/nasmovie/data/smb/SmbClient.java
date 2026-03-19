package com.example.nasmovie.data.smb;

import android.util.Log;

import com.example.nasmovie.data.model.SmbConfig;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * SMB客户端
 * 用于连接NAS服务器、读取文件和目录
 * 实现 AutoCloseable 支持 try-with-resources
 */
public class SmbClient implements AutoCloseable {

    private static final String TAG = "SmbClient";

    private SMBClient smbClient;
    private Connection connection;
    private Session session;
    private DiskShare diskShare;
    private SmbConfig config;
    private boolean connected = false;

    public SmbClient() {
        this.smbClient = new SMBClient();
    }

    @Override
    public void close() {
        disconnect();
    }

    /**
     * 连接到SMB服务器
     */
    public boolean connect(SmbConfig config) {
        this.config = config;
        this.connected = false;

        try {
            // 创建连接
            connection = smbClient.connect(config.getHost(), config.getPort());
            if (connection == null) {
                Log.e(TAG, "Failed to connect to server: " + config.getHost());
                return false;
            }

            // 创建认证上下文
            AuthenticationContext ac;
            if (config.isAnonymous()) {
                ac = AuthenticationContext.anonymous();
            } else {
                ac = new AuthenticationContext(
                    config.getUsername(),
                    config.getPassword().toCharArray(),
                    null
                );
            }

            // 创建会话
            session = connection.authenticate(ac);
            if (session == null) {
                Log.e(TAG, "Authentication failed");
                return false;
            }

            // 连接到共享文件夹
            diskShare = (DiskShare) session.connectShare(config.getShareName());
            if (diskShare == null) {
                Log.e(TAG, "Failed to connect to share: " + config.getShareName());
                return false;
            }

            connected = true;
            Log.i(TAG, "Connected to SMB server: " + config.getHost());
            return true;

        } catch (java.net.UnknownHostException e) {
            Log.e(TAG, "Unknown host: " + config.getHost());
            disconnect();
            return false;
        } catch (java.net.ConnectException e) {
            Log.e(TAG, "Connection refused: " + config.getHost() + ":" + config.getPort());
            disconnect();
            return false;
        } catch (java.io.IOException e) {
            Log.e(TAG, "IO error connecting to " + config.getHost() + ": " + e.getMessage());
            disconnect();
            return false;
        } catch (SMBApiException e) {
            Log.e(TAG, "SMB API error: " + e.getMessage());
            disconnect();
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected connection error: " + e.getMessage(), e);
            disconnect();
            return false;
        }
    }

    /**
     * 测试连接
     */
    public boolean testConnection(SmbConfig config) {
        boolean success = connect(config);
        if (success) {
            try {
                // 尝试列出根目录来验证访问权限
                diskShare.list(config.getMoviePath() != null ? config.getMoviePath() : "");
            } catch (Exception e) {
                Log.e(TAG, "Access denied or authentication error during test: " + e.getMessage());
                success = false;
            }
        }
        disconnect();
        return success;
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        connected = false;

        try {
            if (diskShare != null) {
                diskShare.close();
                diskShare = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing share: " + e.getMessage());
        }

        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing session: " + e.getMessage());
        }

        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing connection: " + e.getMessage());
        }
    }

    /**
     * 列出目录内容
     */
    public List<SmbFileInfo> listFiles(String path) {
        List<SmbFileInfo> files = new ArrayList<>();

        if (!isConnected()) {
            Log.e(TAG, "Not connected to server");
            return files;
        }

        try {
            List<com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation> fileInfos =
                diskShare.list(path);
            for (com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation info : fileInfos) {
                String fileName = info.getFileName();
                // 跳过.和..
                if (".".equals(fileName) || "..".equals(fileName)) {
                    continue;
                }

                SmbFileInfo fileInfo = new SmbFileInfo();
                fileInfo.setName(fileName);
                fileInfo.setPath(path.isEmpty() ? fileName : path + "\\" + fileName);
                fileInfo.setDirectory((info.getFileAttributes() & 0x10) != 0);
                fileInfo.setFileSize(info.getEndOfFile());

                files.add(fileInfo);
            }
        } catch (SMBApiException e) {
            Log.e(TAG, "Error listing files: " + e.getMessage());
        }

        return files;
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String path) {
        if (!isConnected()) {
            return false;
        }
        try {
            return diskShare.fileExists(path);
        } catch (SMBApiException e) {
            return false;
        }
    }

    /**
     * 检查目录是否存在
     */
    public boolean directoryExists(String path) {
        if (!isConnected()) {
            return false;
        }
        try {
            return diskShare.folderExists(path);
        } catch (SMBApiException e) {
            return false;
        }
    }

    /**
     * 读取文件输入流
     */
    public InputStream readFile(String path) {
        if (!isConnected()) {
            Log.e(TAG, "Not connected to server");
            return null;
        }

        try {
            com.hierynomus.smbj.share.File file = diskShare.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            );

            return file.getInputStream();

        } catch (SMBApiException e) {
            Log.e(TAG, "Error opening file: " + e.getMessage());
            return null;
        }
    }

    /**
     * 读取文件内容为字节数组
     */
    public byte[] readFileBytes(String path) {
        if (!isConnected()) {
            return null;
        }

        try {
            com.hierynomus.smbj.share.File file = diskShare.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            );

            // 获取文件大小
            FileStandardInformation info = file.getFileInformation(FileStandardInformation.class);
            long fileSize = info.getEndOfFile();

            // 读取文件内容
            InputStream is = file.getInputStream();
            byte[] buffer = new byte[(int) fileSize];
            int bytesRead = 0;
            while (bytesRead < buffer.length) {
                int read = is.read(buffer, bytesRead, buffer.length - bytesRead);
                if (read == -1) break;
                bytesRead += read;
            }
            is.close();
            file.close();

            return buffer;

        } catch (IOException | SMBApiException e) {
            Log.e(TAG, "Error reading file: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取文件大小
     */
    public long getFileSize(String path) {
        if (!isConnected()) {
            return 0;
        }

        try {
            com.hierynomus.smbj.share.File file = diskShare.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            );

            FileStandardInformation info = file.getFileInformation(FileStandardInformation.class);
            file.close();
            return info.getEndOfFile();

        } catch (Exception e) {
            Log.e(TAG, "Error getting file size: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return connected && diskShare != null && connection != null && connection.isConnected();
    }

    /**
     * 获取当前配置
     */
    public SmbConfig getConfig() {
        return config;
    }

    /**
     * 获取DiskShare对象
     */
    public DiskShare getDiskShare() {
        return diskShare;
    }
}