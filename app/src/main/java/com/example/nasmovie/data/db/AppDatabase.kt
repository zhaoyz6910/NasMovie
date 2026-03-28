package com.example.nasmovie.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.nasmovie.data.model.Favorite
import com.example.nasmovie.data.model.Movie
import com.example.nasmovie.data.model.SmbConfig
import com.example.nasmovie.data.model.WatchProgress

/**
 * 应用数据库
 */
@Database(
    entities = [
        SmbConfig::class,
        Movie::class,
        WatchProgress::class,
        Favorite::class
    ],
    version = 8,
    exportSchema = true  // 启用 schema 导出，便于版本管理
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smbConfigDao(): SmbConfigDao
    abstract fun movieDao(): MovieDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null
        private const val DATABASE_NAME = "nas_movie.db"

        /**
         * 从版本 6 迁移到版本 7
         * 添加外键约束到 watch_progress 和 favorite 表
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 删除旧表
                db.execSQL("DROP TABLE IF EXISTS watch_progress")
                db.execSQL("DROP TABLE IF EXISTS favorite")

                // 创建带外键的新表
                db.execSQL(
                    """
                    CREATE TABLE watch_progress (
                        movieId TEXT NOT NULL PRIMARY KEY,
                        position INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        updateTime INTEGER NOT NULL,
                        percentage INTEGER NOT NULL,
                        FOREIGN KEY(movieId) REFERENCES movie(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_updateTime ON watch_progress(updateTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_watch_progress_movieId ON watch_progress(movieId)")

                db.execSQL(
                    """
                    CREATE TABLE favorite (
                        movieId TEXT NOT NULL PRIMARY KEY,
                        addTime INTEGER NOT NULL,
                        FOREIGN KEY(movieId) REFERENCES movie(id) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_addTime ON favorite(addTime)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_favorite_movieId ON favorite(movieId)")
            }
        }

        /**
         * 从版本 7 迁移到版本 8
         * 1. Movie.serverId 类型从 String 改为 Long
         * 2. SmbConfig 添加索引
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 删除 serverId 索引（因为要修改列类型）
                db.execSQL("DROP INDEX IF EXISTS index_movie_serverId")

                // 2. 添加新列（临时）
                db.execSQL("ALTER TABLE movie ADD COLUMN serverIdNew INTEGER")

                // 3. 迁移数据：只迁移有效的数字 ID
                db.execSQL(
                    """
                    UPDATE movie
                    SET serverIdNew = CAST(serverId AS INTEGER)
                    WHERE serverId IS NOT NULL
                      AND serverId != ''
                      AND GLOB('*[0-9]*', serverId)
                    """.trimIndent()
                )

                // 4. 删除旧列
                db.execSQL("ALTER TABLE movie DROP COLUMN serverId")

                // 5. 重命名新列
                db.execSQL("ALTER TABLE movie ALTER COLUMN serverIdNew RENAME TO serverId")

                // 6. 重建 serverId 索引（新类型）
                db.execSQL("CREATE INDEX IF NOT EXISTS index_movie_serverId ON movie(serverId)")

                // 7. 为 SmbConfig 添加索引
                db.execSQL("CREATE INDEX IF NOT EXISTS index_smb_config_isDefault ON smb_config(isDefault)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_smb_config_name ON smb_config(name)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    // 注意：降级时会清空数据
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
