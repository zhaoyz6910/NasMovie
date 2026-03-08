package com.example.nasmovie.data.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.nasmovie.data.model.SmbConfig;

import java.util.List;

/**
 * SMB服务器配置DAO
 */
@Dao
public interface SmbConfigDao {

    @Insert
    long insert(SmbConfig config);

    @Update
    void update(SmbConfig config);

    @Delete
    void delete(SmbConfig config);

    @Query("DELETE FROM smb_config WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM smb_config WHERE id = :id")
    SmbConfig getById(long id);

    @Query("SELECT * FROM smb_config ORDER BY name ASC")
    List<SmbConfig> getAll();

    @Query("SELECT COUNT(*) FROM smb_config")
    int getCount();
}