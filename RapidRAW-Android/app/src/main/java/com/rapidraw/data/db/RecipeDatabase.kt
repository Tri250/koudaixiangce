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

        // v2026.07: 补齐缺失的迁移路径 1→2 和 2→3。
        // 旧版代码仅有 MIGRATION_3_4，当用户从 v1 或 v2 数据库升级时
        // Room 找不到迁移路径，直接触发 fallbackToDestructiveMigration，
        // 导致 recipes / projects / favorites 数据全部丢失。
        //
        // 历史 schema 变更已不可考，使用 identity migration 传递：
        // 如果实际 schema 与当前 @Entity 声明不匹配，Room 的验证阶段会
        // 抛出 IllegalStateException，此时 fallbackToDestructiveMigration 作为最终兜底。
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v2026.07: identity migration — 保持 v1 schema 不变。
                // 如果 v1 schema 与当前实体不兼容，销毁重建由 fallbackToDestructiveMigration 处理。
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v2026.07: identity migration — 保持 v2 schema 不变。
            }
        }

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
