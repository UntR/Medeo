package com.czpn7.ying.di

import android.content.Context
import androidx.room.Room
import com.czpn7.ying.data.local.AppDatabase
import com.czpn7.ying.data.local.FavoriteDao
import com.czpn7.ying.data.local.ProgressDao
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
        "ying.db"
    ).build()

    @Provides
    fun provideFavoriteDao(database: AppDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideProgressDao(database: AppDatabase): ProgressDao = database.progressDao()
}
