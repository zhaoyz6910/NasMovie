package com.example.nasmovie.data.smb;

import android.util.Log;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;

/**
 * SMB文件读取器
 * 用于读取SMB共享文件
 */
public class SmbFileReader {

    private static final String TAG = "SmbFileReader";

    private DiskShare diskShare;

    public SmbFileReader(DiskShare diskShare) {
        this.diskShare = diskShare;
    }

    /**
     * 读取文件输入流
     */
    public InputStream getInputStream(String path) throws IOException {
        if (diskShare == null) {
            throw new IOException("DiskShare is null");
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
            throw new IOException("Failed to open file: " + path, e);
        }
    }

    /**
     * 读取文件内容为字节数组
     */
    public byte[] readAllBytes(String path) throws IOException {
        if (diskShare == null) {
            throw new IOException("DiskShare is null");
        }

        com.hierynomus.smbj.share.File file = null;
        InputStream is = null;

        try {
            file = diskShare.openFile(
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

            // 对于大文件，限制最大读取大小
            int maxSize = 50 * 1024 * 1024; // 50MB
            int size = (int) Math.min(fileSize, maxSize);

            // 读取文件内容
            is = file.getInputStream();
            byte[] buffer = new byte[size];
            int bytesRead = 0;
            while (bytesRead < buffer.length) {
                int read = is.read(buffer, bytesRead, buffer.length - bytesRead);
                if (read == -1) break;
                bytesRead += read;
            }

            // 如果实际读取的字节数小于预期，返回实际大小的数组
            if (bytesRead < buffer.length) {
                byte[] result = new byte[bytesRead];
                System.arraycopy(buffer, 0, result, 0, bytesRead);
                return result;
            }

            return buffer;

        } catch (SMBApiException e) {
            Log.e(TAG, "Error reading file: " + e.getMessage());
            throw new IOException("Failed to read file: " + path, e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {}
            }
            if (file != null) {
                try {
                    file.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 获取文件大小
     */
    public long getFileSize(String path) throws IOException {
        if (diskShare == null) {
            throw new IOException("DiskShare is null");
        }

        com.hierynomus.smbj.share.File file = null;
        try {
            file = diskShare.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            );

            FileStandardInformation info = file.getFileInformation(FileStandardInformation.class);
            return info.getEndOfFile();

        } catch (SMBApiException e) {
            throw new IOException("Failed to get file size: " + path, e);
        } finally {
            if (file != null) {
                try {
                    file.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String path) {
        if (diskShare == null) {
            return false;
        }
        try {
            return diskShare.fileExists(path);
        } catch (SMBApiException e) {
            return false;
        }
    }
}