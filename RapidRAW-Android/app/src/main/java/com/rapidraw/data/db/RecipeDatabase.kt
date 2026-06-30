package com.rapidraw.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RecipeEntity::class, ProjectEntity::class, FavoriteEntity::class], version = 4, exportSchema = false)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun projectDao(): ProjectDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: RecipeDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // FavoriteEntity added semanticTags column (List<String> stored as TEXT)
                db.execSQL("ALTER TABLE favorites ADD COLUMN semanticTags TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): RecipeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RecipeDatabase::class.java,
                    "rapidraw_recipes"
                )
                    .addMigrations(MIGRATION_3_4)
                    // v1.5.5 hotfix: 作为安全降级策略，当缺少迁移路径时
                    // 重建数据库而非抛 IllegalStateException 闪退。
                    // 仅影响本地缓存数据（配方/收藏），图片文件不受影响。
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
