package com.termux.companion.di

import android.content.Context
import androidx.room.Room
import com.termux.companion.data.ai.AISuggestionService
import com.termux.companion.data.ai.CommandAutocomplete
import com.termux.companion.data.db.AppDatabase
import com.termux.companion.data.db.CommandHistoryDao
import com.termux.companion.data.security.CryptoStore
import com.termux.companion.data.security.SecurityRepository
import com.termux.companion.data.settings.SettingsRepository
import com.termux.companion.data.termux.TermuxCommandExecutor
import com.termux.companion.data.termux.TermuxCommandRunner
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
        ).addMigrations(AppDatabase.MIGRATION_1_2).build()
    }

    @Provides
    @Singleton
    fun provideCommandHistoryDao(db: AppDatabase): CommandHistoryDao {
        return db.commandHistoryDao()
    }

    @Provides
    @Singleton
    fun provideCryptoStore(): CryptoStore = CryptoStore()

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        cryptoStore: CryptoStore
    ): SettingsRepository {
        return SettingsRepository(context, cryptoStore)
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
    fun provideTermuxCommandRunner(executor: TermuxCommandExecutor): TermuxCommandRunner {
        return executor
    }

    @Provides
    @Singleton
    fun provideTermuxDiagnosticsChecker(@ApplicationContext context: Context): TermuxDiagnosticsChecker {
        return TermuxDiagnosticsChecker(context)
    }

    @Provides
    @Singleton
    fun provideSecurityRepository(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository,
        termuxCommandExecutor: TermuxCommandExecutor
    ): SecurityRepository {
        return SecurityRepository(context, settingsRepository, termuxCommandExecutor)
    }
}
