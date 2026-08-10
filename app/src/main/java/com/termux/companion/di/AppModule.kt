package com.termux.companion.di

import android.content.Context
import androidx.room.Room
import com.termux.companion.data.ai.AISuggestionService
import com.termux.companion.data.ai.CommandAutocomplete
import com.termux.companion.data.db.AppDatabase
import com.termux.companion.data.db.CommandHistoryDao
import com.termux.companion.data.settings.SettingsRepository
import com.termux.companion.data.termux.TermuxCommandExecutor
import com.termux.companion.data.termux.TermuxDiagnosticsChecker
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
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "termux_companion.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideCommandHistoryDao(db: AppDatabase): CommandHistoryDao {
        return db.commandHistoryDao()
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideAISuggestionService(): AISuggestionService {
        return AISuggestionService()
    }

    @Provides
    @Singleton
    fun provideCommandAutocomplete(@ApplicationContext context: Context): CommandAutocomplete {
        return CommandAutocomplete(context)
    }

    @Provides
    @Singleton
    fun provideTermuxCommandExecutor(@ApplicationContext context: Context): TermuxCommandExecutor {
        return TermuxCommandExecutor(context)
    }

    @Provides
    @Singleton
    fun provideTermuxDiagnosticsChecker(@ApplicationContext context: Context): TermuxDiagnosticsChecker {
        return TermuxDiagnosticsChecker(context)
    }
}
