package com.example.nasmovie.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.nasmovie.data.model.SmbConfig

/**
 * SMB服务器配置DAO
 */
@Dao
interface SmbConfigDao {

    @Insert
    suspend fun insert(config: SmbConfig): Long

    @Update
    suspend fun update(config: SmbConfig)

    @Delete
    suspend fun delete(config: SmbConfig)

    @Query("DELETE FROM smb_config WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM smb_config WHERE id = :id")
    suspend fun getById(id: Long): SmbConfig?

    @Query("SELECT * FROM smb_config ORDER BY name ASC")
    suspend fun getAll(): List<SmbConfig>

    @Query("SELECT COUNT(*) FROM smb_config")
    suspend fun getCount(): Int
}