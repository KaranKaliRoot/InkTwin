package com.app.inktwin.di

import android.content.Context
import com.app.inktwin.data.room.InkTwinDatabase
import com.app.inktwin.data.repository.GlyphRepository
import com.app.inktwin.data.repository.GlyphRepositoryImpl
import com.app.inktwin.data.repository.HandwritingProfileRepository
import com.app.inktwin.data.repository.HandwritingProfileRepositoryImpl
import com.app.inktwin.data.repository.NoteDraftRepository
import com.app.inktwin.data.repository.NoteDraftRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InkTwinDatabase {
        return InkTwinDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideHandwritingProfileRepository(database: InkTwinDatabase): HandwritingProfileRepository {
        return HandwritingProfileRepositoryImpl(database)
    }

    @Provides
    @Singleton
    fun provideGlyphRepository(database: InkTwinDatabase): GlyphRepository {
        return GlyphRepositoryImpl(database)
    }

    @Provides
    @Singleton
    fun provideNoteDraftRepository(database: InkTwinDatabase): NoteDraftRepository {
        return NoteDraftRepositoryImpl(database)
    }
}