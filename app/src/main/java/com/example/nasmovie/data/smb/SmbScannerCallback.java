package com.example.nasmovie.data.smb;

/**
 * SMB 扫描回调接口
 */
public interface SmbScannerCallback {

    /**
     * 扫描开始
     */
    void onScanStart();

    /**
     * 扫描进度更新
     * @param current 当前进度
     * @param total 总数量
     * @param currentPath 当前扫描的路径
     */
    void onScanProgress(int current, int total, String currentPath);

    /**
     * 发现视频文件
     * @param fileInfo 文件信息
     * @param movie 扫描到的电影信息
     */
    void onVideoFound(SmbFileInfo fileInfo, ScanResult.ScannedMovie movie);

    /**
     * 发现 NFO 文件
     * @param fileInfo 文件信息
     */
    void onNfoFound(SmbFileInfo fileInfo);

    /**
     * 发现海报图片
     * @param fileInfo 文件信息
     */
    void onPosterFound(SmbFileInfo fileInfo);

    /**
     * 扫描完成
     * @param result 扫描结果
     */
    void onScanComplete(ScanResult result);

    /**
     * 扫描出错
     * @param error 错误信息
     */
    void onScanError(String error);

    /**
     * 扫描取消
     */
    void onScanCancelled();
}
