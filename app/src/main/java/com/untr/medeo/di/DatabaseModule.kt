package com.untr.medeo.di

import android.content.Context
import androidx.room.Room
import com.untr.medeo.data.local.AppDatabase
import com.untr.medeo.data.local.FavoriteDao
import com.untr.medeo.data.local.MIGRATION_1_2
import com.untr.medeo.data.local.MIGRATION_2_3
import com.untr.medeo.data.local.ProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "medeo.db"
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideProgressDao(database: AppDatabase): ProgressDao = database.progressDao()

}
