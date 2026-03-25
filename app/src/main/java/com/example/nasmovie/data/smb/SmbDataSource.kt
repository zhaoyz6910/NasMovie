package com.example.nasmovie.data.smb

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.example.nasmovie.data.model.SmbConfig
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.File
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet

/**
 * ExoPlayer 的 SMB 数据源
 * 实现 DataSource 接口，让 ExoPlayer 可以直接播放 SMB 上的视频
 */
@UnstableApi
class SmbDataSource(private val config: SmbConfig) : BaseDataSource(true) {

    private var connection: com.hierynomus.smbj.connection.Connection? = null
    private var session: com.hierynomus.smbj.session.Session? = null
    private var diskShare: com.hierynomus.smbj.share.DiskShare? = null
    private var smbFile: File? = null
    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var uri: Uri? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        opened = true

        transferInitializing(dataSpec)

        return try {
            // 连接 SMB 服务器
            connect()

            // 解析路径
            val filePath = parseFilePath(uri!!)
            Log.d(TAG, "Opening file: $filePath")

            // 打开文件
            smbFile = diskShare!!.openFile(
                filePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE, SMB2CreateOptions.FILE_SEQUENTIAL_ONLY)
            )

            // 获取文件大小
            val info = smbFile!!.getFileInformation(FileStandardInformation::class.java)
            val fileSize = info.endOfFile

            // 打开输入流
            inputStream = smbFile!!.inputStream

            // 计算剩余字节
            if (dataSpec.position != C.LENGTH_UNSET.toLong()) {
                // 跳转到指定位置
                val skipped = inputStream!!.skip(dataSpec.position)
                bytesRemaining = fileSize - skipped
                Log.d(TAG, "Seeked to position: $skipped, remaining: $bytesRemaining")
            } else {
                bytesRemaining = fileSize
            }

            if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                bytesRemaining = minOf(bytesRemaining, dataSpec.length)
            }

            transferStarted(dataSpec)
            Log.d(TAG, "File opened, size: $fileSize, remaining: $bytesRemaining")

            if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else bytesRemaining
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file: ${e.message}", e)
            closeConnection()
            throw IOException("Failed to open SMB file: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()
        val bytesRead = inputStream!!.read(buffer, offset, bytesToRead)

        if (bytesRead == -1) {
            return C.RESULT_END_OF_INPUT
        }

        bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    @Throws(IOException::class)
    override fun close() {
        if (opened) {
            opened = false
            closeConnection()
            transferEnded()
        }
    }

    @Throws(IOException::class)
    private fun connect() {
        val smbClient = SMBClient()

        connection = smbClient.connect(config.host, config.port)
        if (connection == null) {
            throw IOException("Failed to connect to server: ${config.host}")
        }

        val ac: AuthenticationContext = if (config.isAnonymous) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(
                config.username,
                config.password?.toCharArray(),
                null
            )
        }

        session = connection!!.authenticate(ac)
        if (session == null) {
            throw IOException("Authentication failed")
        }

        diskShare = session!!.connectShare(config.shareName) as? com.hierynomus.smbj.share.DiskShare
        if (diskShare == null) {
            throw IOException("Failed to connect to share: ${config.shareName}")
        }

        Log.d(TAG, "Connected to SMB server")
    }

    private fun closeConnection() {
        try {
            inputStream?.close()
            inputStream = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing input stream: ${e.message}")
        }

        try {
            smbFile?.close()
            smbFile = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing file: ${e.message}")
        }

        try {
            diskShare?.close()
            diskShare = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing share: ${e.message}")
        }

        try {
            session?.close()
            session = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session: ${e.message}")
        }

        try {
            connection?.close()
            connection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing connection: ${e.message}")
        }
    }

    /**
     * 从 URI 解析文件路径
     * URI 格式: smb://host/share/path/to/file.mp4
     */
    private fun parseFilePath(uri: Uri): String {
        var path = uri.path ?: return ""

        // 移除开头的 /
        path = path.removePrefix("/")

        // 移除 share name 部分（因为已经连接到 share）
        val shareName = config.shareName ?: return path
        path = when {
            path.startsWith("$shareName/") -> path.substring(shareName.length + 1)
            path.startsWith(shareName) -> path.substring(shareName.length)
            else -> path
        }

        // 统一使用反斜杠
        return path.replace("/", "\\")
    }

    /**
     * DataSource 工厂
     */
    @UnstableApi
    class Factory(private val config: SmbConfig) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(config)
    }

    companion object {
        private const val TAG = "SmbDataSource"
        const val SMB_SCHEME = "smb"
    }
}