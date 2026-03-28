package com.example.nasmovie.data.smb

import android.util.Log
import com.example.nasmovie.data.model.SmbConfig
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.EnumSet

/**
 * SMB客户端 (Kotlin 协程重构版)
 * 用于连接NAS服务器、读取文件和目录
 * 实现 AutoCloseable 支持 use {} 语法
 */
class SmbClient : AutoCloseable {

    companion object {
        private const val TAG = "SmbClient"
    }

    private val smbClient = SMBClient()
    private var connection: Connection? = null
    private var session: Session? = null
    
    var diskShare: DiskShare? = null
        private set
        
    var config: SmbConfig? = null
        private set
        
    private var connected = false

    val isConnected: Boolean
        get() = connected && diskShare != null && connection?.isConnected == true

    override fun close() {
        disconnect()
    }

    /**
     * 连接到SMB服务器
     */
    suspend fun connect(config: SmbConfig): Boolean = withContext(Dispatchers.IO) {
        this@SmbClient.config = config
        connected = false

        try {
            // 创建连接
            connection = smbClient.connect(config.host, config.port)
            val conn = connection ?: run {
                Log.e(TAG, "Failed to connect to server")
                return@withContext false
            }

            // 创建认证上下文
            val ac = if (config.isAnonymous) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(
                    config.username,
                    config.password?.toCharArray() ?: CharArray(0),
                    null
                )
            }

            // 创建会话
            session = conn.authenticate(ac)
            val sess = session ?: run {
                Log.e(TAG, "Authentication failed")
                return@withContext false
            }

            // 连接到共享文件夹
            diskShare = sess.connectShare(config.shareName) as? DiskShare
            if (diskShare == null) {
                Log.e(TAG, "Failed to connect to share")
                return@withContext false
            }

            connected = true
            Log.i(TAG, "Connected to SMB server")
            true

        } catch (e: UnknownHostException) {
            Log.e(TAG, "Unknown host")
            disconnect()
            false
        } catch (e: ConnectException) {
            Log.e(TAG, "Connection refused")
            disconnect()
            false
        } catch (e: IOException) {
            Log.e(TAG, "IO error connecting: ${e.message}")
            disconnect()
            false
        } catch (e: SMBApiException) {
            Log.e(TAG, "SMB API error: ${e.message}")
            disconnect()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected connection error: ${e.message}", e)
            disconnect()
            false
        }
    }

    /**
     * 测试连接
     */
    suspend fun testConnection(config: SmbConfig): Boolean = withContext(Dispatchers.IO) {
        var success = connect(config)
        if (success) {
            try {
                // 尝试列出根目录来验证访问权限
                diskShare?.list(config.moviePath ?: "")
            } catch (e: Exception) {
                Log.e(TAG, "Access denied or authentication error during test: ${e.message}")
                success = false
            }
        }
        disconnect()
        success
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        connected = false

        try {
            diskShare?.close()
            diskShare = null
        } catch (e: IOException) {
            Log.e(TAG, "Error closing share: ${e.message}")
        }

        try {
            session?.close()
            session = null
        } catch (e: IOException) {
            Log.e(TAG, "Error closing session: ${e.message}")
        }

        try {
            connection?.close()
            connection = null
        } catch (e: IOException) {
            Log.e(TAG, "Error closing connection: ${e.message}")
        }
    }

    /**
     * 列出目录内容
     */
    suspend fun listFiles(path: String): List<SmbFileInfo> = withContext(Dispatchers.IO) {
        val files = mutableListOf<SmbFileInfo>()

        if (!isConnected) {
            Log.e(TAG, "Not connected to server")
            return@withContext files
        }

        try {
            val share = diskShare ?: return@withContext files
            val fileInfos = share.list(path)
            for (info in fileInfos) {
                val fileName = info.fileName
                // 跳过.和..
                if (fileName == "." || fileName == "..") {
                    continue
                }

                val fileInfo = SmbFileInfo().apply {
                    name = fileName
                    this.path = if (path.isEmpty()) fileName else "$path\\$fileName"
                    isDirectory = (info.fileAttributes and 0x10.toLong()) != 0L
                    fileSize = info.endOfFile
                }
                files.add(fileInfo)
            }
        } catch (e: SMBApiException) {
            Log.e(TAG, "Error listing files: ${e.message}")
        }

        files
    }

    /**
     * 使用文件的输入流（自动管理资源）
     * 推荐使用此方法而非 readFile()，确保资源正确释放
     */
    suspend fun <T> useFile(path: String, block: (InputStream) -> T): T? = withContext(Dispatchers.IO) {
        if (!isConnected) {
            Log.e(TAG, "Not connected to server")
            return@withContext null
        }

        try {
            val share = diskShare ?: return@withContext null
            share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            ).use { file ->
                block(file.inputStream)
            }
        } catch (e: SMBApiException) {
            Log.e(TAG, "Error opening file: ${e.message}")
            null
        }
    }

    /**
     * 读取文件输入流
     * 注意：调用者必须关闭返回的 InputStream，否则会导致资源泄漏
     * 建议使用 useFile() 方法代替
     */
    suspend fun readFile(path: String): InputStream? = withContext(Dispatchers.IO) {
        if (!isConnected) {
            Log.e(TAG, "Not connected to server")
            return@withContext null
        }

        try {
            val share = diskShare ?: return@withContext null
            val file = share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            file.inputStream
        } catch (e: SMBApiException) {
            Log.e(TAG, "Error opening file: ${e.message}")
            null
        }
    }

    /**
     * 读取文件内容为字节数组
     */
    suspend fun readFileBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext null

        try {
            val share = diskShare ?: return@withContext null
            share.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            ).use { file ->
                val info = file.getFileInformation(FileStandardInformation::class.java)
                val fileSize = info.endOfFile

                file.inputStream.use { isStream ->
                    val buffer = ByteArray(fileSize.toInt())
                    var bytesRead = 0
                    while (bytesRead < buffer.size) {
                        val read = isStream.read(buffer, bytesRead, buffer.size - bytesRead)
                        if (read == -1) break
                        bytesRead += read
                    }
                    buffer
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file: ${e.message}")
            null
        }
    }
}
