package com.example.nasmovie.data.smb

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.share.DiskShare
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet

/**
 * SMB文件读取器
 * 用于读取SMB共享文件
 */
class SmbFileReader(private var diskShare: DiskShare?) {

    companion object {
        private const val TAG = "SmbFileReader"
    }

    /**
     * 读取文件输入流
     */
    @Throws(IOException::class)
    fun getInputStream(path: String): InputStream {
        if (diskShare == null) {
            throw IOException("DiskShare is null")
        }

        return try {
            val file = diskShare!!.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            file.getInputStream()
        } catch (e: SMBApiException) {
            Log.e(TAG, "Error opening file: ${e.message}")
            throw IOException("Failed to open file: $path", e)
        }
    }

    /**
     * 读取文件内容为字节数组
     */
    @Throws(IOException::class)
    fun readAllBytes(path: String): ByteArray {
        if (diskShare == null) {
            throw IOException("DiskShare is null")
        }

        var file: com.hierynomus.smbj.share.File? = null
        var inputStream: InputStream? = null

        return try {
            file = diskShare!!.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )

            // 获取文件大小
            val info = file.getFileInformation(FileStandardInformation::class.java)
            val fileSize = info.endOfFile

            // 对于大文件，限制最大读取大小
            val maxSize = 50 * 1024 * 1024 // 50MB
            val size = fileSize.coerceAtMost(maxSize.toLong()).toInt()

            // 读取文件内容
            inputStream = file.getInputStream()
            val buffer = ByteArray(size)
            var bytesRead = 0
            while (bytesRead < buffer.size) {
                val read = inputStream.read(buffer, bytesRead, buffer.size - bytesRead)
                if (read == -1) break
                bytesRead += read
            }

            // 如果实际读取的字节数小于预期，返回实际大小的数组
            if (bytesRead < buffer.size) {
                buffer.copyOf(bytesRead)
            } else {
                buffer
            }
        } catch (e: SMBApiException) {
            Log.e(TAG, "Error reading file: ${e.message}")
            throw IOException("Failed to read file: $path", e)
        } finally {
            inputStream?.close()
            file?.close()
        }
    }

    /**
     * 获取文件大小
     */
    @Throws(IOException::class)
    fun getFileSize(path: String): Long {
        if (diskShare == null) {
            throw IOException("DiskShare is null")
        }

        var file: com.hierynomus.smbj.share.File? = null
        return try {
            file = diskShare!!.openFile(
                path,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            val info = file.getFileInformation(FileStandardInformation::class.java)
            info.endOfFile
        } catch (e: SMBApiException) {
            throw IOException("Failed to get file size: $path", e)
        } finally {
            file?.close()
        }
    }

    /**
     * 检查文件是否存在
     */
    fun fileExists(path: String): Boolean {
        if (diskShare == null) {
            return false
        }
        return try {
            diskShare!!.fileExists(path)
        } catch (e: SMBApiException) {
            false
        }
    }
}