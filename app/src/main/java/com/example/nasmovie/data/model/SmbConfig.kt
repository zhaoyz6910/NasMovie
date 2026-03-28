package com.example.nasmovie.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nasmovie.util.AppConstants

/**
 * SMB服务器配置实体
 */
@Entity(
    tableName = "smb_config",
    indices = [
        Index("isDefault"),
        Index(value = ["name"], unique = true)
    ]
)
data class SmbConfig(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    var name: String? = null,
    var host: String? = null,
    var port: Int = AppConstants.SMB_PORT,
    var username: String? = null,
    var password: String? = null,
    var shareName: String? = null,
    var moviePath: String? = null,
    var isDefault: Boolean = false,
    var lastConnectTime: Long = 0
) {
    /**
     * 是否匿名登录
     */
    val isAnonymous: Boolean
        get() = username.isNullOrEmpty()

    /**
     * 获取完整的SMB路径
     */
    val smbPath: String
        get() = "smb://$host/$shareName/${moviePath ?: ""}"
}