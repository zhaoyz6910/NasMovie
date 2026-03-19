package com.example.nasmovie.data.smb;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;

import com.example.nasmovie.data.model.SmbConfig;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

/**
 * ExoPlayer 的 SMB 数据源
 * 实现 DataSource 接口，让 ExoPlayer 可以直接播放 SMB 上的视频
 */
@UnstableApi
public class SmbDataSource extends BaseDataSource {

    private static final String TAG = "SmbDataSource";
    public static final String SMB_SCHEME = "smb";

    private final SmbConfig config;

    private Connection connection;
    private Session session;
    private DiskShare diskShare;
    private File smbFile;
    private InputStream inputStream;
    private long bytesRemaining;
    private boolean opened;
    private Uri uri;

    public SmbDataSource(SmbConfig config) {
        super(true); // isNetwork = true
        this.config = config;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        uri = dataSpec.uri;
        opened = true;

        transferInitializing(dataSpec);

        try {
            // 连接 SMB 服务器
            connect();

            // 解析路径
            String filePath = parseFilePath(uri);
            Log.d(TAG, "Opening file: " + filePath);

            // 打开文件
            smbFile = diskShare.openFile(
                filePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_SEQUENTIAL_ONLY)
            );

            // 获取文件大小
            FileStandardInformation info = smbFile.getFileInformation(FileStandardInformation.class);
            long fileSize = info.getEndOfFile();

            // 打开输入流
            inputStream = smbFile.getInputStream();

            // 计算剩余字节
            if (dataSpec.position != C.LENGTH_UNSET) {
                // 跳转到指定位置
                long skipped = inputStream.skip(dataSpec.position);
                bytesRemaining = fileSize - skipped;
                Log.d(TAG, "Seeked to position: " + skipped + ", remaining: " + bytesRemaining);
            } else {
                bytesRemaining = fileSize;
            }

            if (dataSpec.length != C.LENGTH_UNSET) {
                bytesRemaining = Math.min(bytesRemaining, dataSpec.length);
            }

            transferStarted(dataSpec);
            Log.d(TAG, "File opened, size: " + fileSize + ", remaining: " + bytesRemaining);

            return dataSpec.length != C.LENGTH_UNSET ? dataSpec.length : bytesRemaining;

        } catch (Exception e) {
            Log.e(TAG, "Failed to open file: " + e.getMessage(), e);
            closeConnection();
            throw new IOException("Failed to open SMB file: " + e.getMessage(), e);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        }

        int bytesToRead = (int) Math.min(length, bytesRemaining);
        int bytesRead = inputStream.read(buffer, offset, bytesToRead);

        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT;
        }

        bytesRemaining -= bytesRead;
        bytesTransferred(bytesRead);
        return bytesRead;
    }

    @Nullable
    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws IOException {
        if (opened) {
            opened = false;
            closeConnection();
            transferEnded();
        }
    }

    private void connect() throws IOException {
        com.hierynomus.smbj.SMBClient smbClient = new com.hierynomus.smbj.SMBClient();

        connection = smbClient.connect(config.getHost(), config.getPort());
        if (connection == null) {
            throw new IOException("Failed to connect to server: " + config.getHost());
        }

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

        session = connection.authenticate(ac);
        if (session == null) {
            throw new IOException("Authentication failed");
        }

        diskShare = (DiskShare) session.connectShare(config.getShareName());
        if (diskShare == null) {
            throw new IOException("Failed to connect to share: " + config.getShareName());
        }

        Log.d(TAG, "Connected to SMB server: " + config.getHost());
    }

    private void closeConnection() {
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing input stream: " + e.getMessage());
        }

        try {
            if (smbFile != null) {
                smbFile.close();
                smbFile = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing file: " + e.getMessage());
        }

        try {
            if (diskShare != null) {
                diskShare.close();
                diskShare = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing share: " + e.getMessage());
        }

        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing session: " + e.getMessage());
        }

        try {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error closing connection: " + e.getMessage());
        }
    }

    /**
     * 从 URI 解析文件路径
     * URI 格式: smb://host/share/path/to/file.mp4
     */
    private String parseFilePath(Uri uri) {
        String path = uri.getPath();
        if (path == null) {
            return "";
        }

        // 移除开头的 /
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // 移除 share name 部分（因为已经连接到 share）
        String shareName = config.getShareName();
        if (path.startsWith(shareName + "/")) {
            path = path.substring(shareName.length() + 1);
        } else if (path.startsWith(shareName)) {
            path = path.substring(shareName.length());
        }

        // 统一使用反斜杠
        path = path.replace("/", "\\");

        return path;
    }

    /**
     * DataSource 工厂
     */
    @UnstableApi
    public static class Factory implements DataSource.Factory {

        private final SmbConfig config;

        public Factory(SmbConfig config) {
            this.config = config;
        }

        @Override
        public DataSource createDataSource() {
            return new SmbDataSource(config);
        }
    }
}
