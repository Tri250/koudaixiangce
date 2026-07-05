package com.alcedo.studio.di

import android.content.Context
import com.alcedo.studio.data.db.AppDatabase
import com.alcedo.studio.data.repository.ExportJobRepository
import com.alcedo.studio.data.repository.FavoriteRepository
import com.alcedo.studio.data.repository.PresetRepository
import com.alcedo.studio.data.repository.ProjectRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideProjectRepository(database: AppDatabase): ProjectRepository {
        return ProjectRepository(database)
    }

    @Provides
    @Singleton
    fun providePresetRepository(database: AppDatabase): PresetRepository {
        return PresetRepository(database)
    }

    @Provides
    @Singleton
    fun provideFavoriteRepository(database: AppDatabase): FavoriteRepository {
        return FavoriteRepository(database)
    }

    @Provides
    @Singleton
    fun provideExportJobRepository(database: AppDatabase): ExportJobRepository {
        return ExportJobRepository(database)
    }
}
