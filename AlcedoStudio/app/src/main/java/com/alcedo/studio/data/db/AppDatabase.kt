package com.alcedo.studio.data.db

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import android.content.Context
import com.alcedo.studio.core.Constants
import com.alcedo.studio.core.L
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Database(
    entities = [
        ProjectEntity::class,
        PresetEntity::class,
        FavoriteEntity::class,
        ExportJobEntity::class,
    ],
    version = Constants.Database.VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun presetDao(): PresetDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun exportJobDao(): ExportJobDao

    companion object {
        private const val TAG = "AppDatabase"

        @Volatile
        private var INSTANCE: AppDatabase? = null
        private val isBuilding = AtomicBoolean(false)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            if (isBuilding.getAndSet(true)) {
                throw IllegalStateException("Database is already being built")
            }

            return try {
                val builder = Room.databaseBuilder(
                    context,
                    AppDatabase::class.java,
                    Constants.Database.NAME
                )

                builder.addCallback(DatabaseCallback())

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    builder.fallbackToDestructiveMigrationOnDowngrade()
                } else {
                    builder.fallbackToDestructiveMigration()
                }

                builder.build()
            } catch (e: Exception) {
                L.e(TAG, "Failed to build database, attempting recovery", e)
                recoverFromCorruptedDatabase(context)
                try {
                    Room.databaseBuilder(
                        context,
                        AppDatabase::class.java,
                        Constants.Database.NAME
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                } catch (e2: Exception) {
                    L.e(TAG, "Database recovery failed", e2)
                    throw e2
                }
            } finally {
                isBuilding.set(false)
            }
        }

        private fun recoverFromCorruptedDatabase(context: Context) {
            try {
                val dbPath = context.getDatabasePath(Constants.Database.NAME)
                if (!dbPath.exists()) {
                    return
                }

                val dbDir = dbPath.parentFile ?: return
                if (!dbDir.exists()) return

                val backupFile = File(dbDir, "${Constants.Database.NAME}${Constants.Database.BACKUP_SUFFIX}")

                if (backupFile.exists()) {
                    val deleted = backupFile.delete()
                    if (!deleted) {
                        L.w(TAG, "Failed to delete old backup file")
                    }
                }

                val success = dbPath.copyTo(backupFile, overwrite = true)
                if (success.exists() && success.length() > 0) {
                    dbPath.delete()
                    L.w(TAG, "Corrupted database backed up to ${backupFile.name}")
                } else {
                    L.e(TAG, "Failed to backup corrupted database")
                }
            } catch (e: Exception) {
                L.e(TAG, "Failed to backup corrupted database", e)
            }
        }

        fun clearInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: RoomDatabase) {
            super.onCreate(db)
            L.i(TAG, "Database created")
        }

        override fun onOpen(db: RoomDatabase) {
            super.onOpen(db)
            L.d(TAG, "Database opened")
        }
    }
}

object Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun adjustmentsToJson(value: com.alcedo.studio.data.model.Adjustments): String =
        json.encodeToString(value)

    @TypeConverter
    fun jsonToAdjustments(value: String): com.alcedo.studio.data.model.Adjustments =
        json.decodeFromString(value)

    @TypeConverter
    fun editHistoryToJson(value: List<com.alcedo.studio.data.model.EditHistoryEntry>): String =
        json.encodeToString(value)

    @TypeConverter
    fun jsonToEditHistory(value: String): List<com.alcedo.studio.data.model.EditHistoryEntry> =
        json.decodeFromString(value)

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> = json.decodeFromString(value)
}
