package com.example.nasmovie.data.smb

/**
 * SMB 扫描回调接口
 */
interface SmbScannerCallback {

    /**
     * 扫描开始
     */
    fun onScanStart()

    /**
     * 扫描进度更新
     * @param current 当前进度
     * @param total 总数量
     * @param currentPath 当前扫描的路径
     */
    fun onScanProgress(current: Int, total: Int, currentPath: String)

    /**
     * 发现视频文件
     * @param fileInfo 文件信息
     * @param movie 扫描到的电影信息
     */
    fun onVideoFound(fileInfo: SmbFileInfo, movie: ScanResult.ScannedMovie)

    /**
     * 发现 NFO 文件
     * @param fileInfo 文件信息
     */
    fun onNfoFound(fileInfo: SmbFileInfo)

    /**
     * 发现海报图片
     * @param fileInfo 文件信息
     */
    fun onPosterFound(fileInfo: SmbFileInfo)

    /**
     * 扫描完成
     * @param result 扫描结果
     */
    fun onScanComplete(result: ScanResult)

    /**
     * 扫描出错
     * @param error 错误信息
     */
    fun onScanError(error: String)

    /**
     * 扫描取消
     */
    fun onScanCancelled()
}