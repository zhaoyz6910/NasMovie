package com.example.nasmovie.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.nasmovie.data.model.Favorite;
import com.example.nasmovie.data.model.Movie;
import com.example.nasmovie.data.model.SmbConfig;
import com.example.nasmovie.data.model.WatchProgress;

/**
 * 应用数据库
 */
@Database(
    entities = {
        SmbConfig.class,
        Movie.class,
        WatchProgress.class,
        Favorite.class
    },
    version = 5,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;
    private static final String DATABASE_NAME = "nas_movie.db";

    public abstract SmbConfigDao smbConfigDao();
    public abstract MovieDao movieDao();
    public abstract WatchProgressDao watchProgressDao();
    public abstract FavoriteDao favoriteDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.getApplicationContext(),
                        AppDatabase.class,
                        DATABASE_NAME
                    )
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return instance;
    }
}