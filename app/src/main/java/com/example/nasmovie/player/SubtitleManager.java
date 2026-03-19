package com.example.nasmovie.player;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.example.nasmovie.data.parser.SubtitleParser;
import com.example.nasmovie.data.smb.SmbClient;
import com.example.nasmovie.data.smb.SmbFileReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 字幕管理类
 * 负责字幕加载和显示
 */
public class SubtitleManager {

    private static final String TAG = "SubtitleManager";

    private final Context context;
    private SmbClient smbClient;
    private SmbFileReader fileReader;
    private TextView subtitleView;

    private List<SubtitleParser.SubtitleEntry> entries;
    private String currentSubtitlePath;
    private int currentEntryIndex;

    public SubtitleManager(Context context) {
        this.context = context;
        this.entries = new ArrayList<>();
        this.currentEntryIndex = -1;
    }

    /**
     * 设置SMB客户端
     */
    public void setSmbClient(SmbClient client) {
        this.smbClient = client;
        if (client != null && client.getDiskShare() != null) {
            this.fileReader = new SmbFileReader(client.getDiskShare());
        }
    }

    /**
     * 设置字幕显示视图
     */
    public void setSubtitleView(TextView view) {
        this.subtitleView = view;
    }

    /**
     * 加载字幕文件（支持SMB和本地文件）
     */
    public boolean loadSubtitle(String path) {
        try {
            byte[] data;

            // 判断是本地文件还是SMB文件
            if (path.startsWith("/") || path.contains(":\\") || path.contains(":/")) {
                // 本地文件
                data = readLocalFile(path);
            } else {
                // SMB文件
                if (fileReader == null) {
                    Log.e(TAG, "SMB file reader not initialized");
                    return false;
                }
                data = fileReader.readAllBytes(path);
            }

            if (data == null) {
                Log.e(TAG, "Failed to read subtitle file: " + path);
                return false;
            }

            String extension = getExtension(path);
            SubtitleParser.SubtitleData subtitleData = SubtitleParser.parse(data, extension);

            if (subtitleData == null || subtitleData.entries.isEmpty()) {
                Log.e(TAG, "Failed to parse subtitle file: " + path);
                return false;
            }

            entries = subtitleData.entries;
            currentSubtitlePath = path;
            currentEntryIndex = -1;

            Log.i(TAG, "Loaded " + entries.size() + " subtitle entries");
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error loading subtitle: " + e.getMessage());
            return false;
        }
    }

    /**
     * 读取本地文件
     */
    private byte[] readLocalFile(String path) throws IOException {
        File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG, "Local file not found: " + path);
            return null;
        }

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            int fileSize = (int) file.length();
            byte[] buffer = new byte[fileSize];
            int bytesRead = fis.read(buffer);
            if (bytesRead != fileSize) {
                Log.w(TAG, "Partial read: " + bytesRead + " of " + fileSize);
            }
            return buffer;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 更新字幕显示
     * @param positionMs 当前播放位置（毫秒）
     */
    public void update(long positionMs) {
        if (entries.isEmpty() || subtitleView == null) {
            return;
        }

        // 查找当前应该显示的字幕
        int index = findSubtitleIndex(positionMs);

        if (index != currentEntryIndex) {
            currentEntryIndex = index;

            if (index >= 0) {
                SubtitleParser.SubtitleEntry entry = entries.get(index);
                subtitleView.setText(entry.text);
                subtitleView.setVisibility(android.view.View.VISIBLE);
            } else {
                subtitleView.setVisibility(android.view.View.GONE);
            }
        }
    }

    /**
     * 查找指定时间对应的字幕索引（二分查找优化）
     */
    private int findSubtitleIndex(long positionMs) {
        int left = 0;
        int right = entries.size() - 1;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            SubtitleParser.SubtitleEntry entry = entries.get(mid);

            if (positionMs >= entry.startTimeMs && positionMs <= entry.endTimeMs) {
                return mid;
            } else if (positionMs < entry.startTimeMs) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        return -1;
    }

    /**
     * 清除字幕
     */
    public void clear() {
        entries.clear();
        currentEntryIndex = -1;
        if (subtitleView != null) {
            subtitleView.setVisibility(android.view.View.GONE);
        }
    }

    /**
     * 是否已加载字幕
     */
    public boolean hasSubtitle() {
        return !entries.isEmpty();
    }

    /**
     * 获取当前字幕路径
     */
    public String getCurrentSubtitlePath() {
        return currentSubtitlePath;
    }

    /**
     * 获取字幕条目数量
     */
    public int getSubtitleCount() {
        return entries.size();
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String path) {
        if (path == null || !path.contains(".")) {
            return "";
        }
        return path.substring(path.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }
}