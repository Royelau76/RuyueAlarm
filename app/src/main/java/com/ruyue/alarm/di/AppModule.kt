package com.ruyue.alarm.di

import android.content.Context
import com.ruyue.alarm.data.AppDatabase
import com.ruyue.alarm.data.Db
import com.ruyue.alarm.data.EventLogDao
import com.ruyue.alarm.data.ReminderDao
import com.ruyue.alarm.data.ReminderDatabase
import com.ruyue.alarm.data.ReminderDb
import com.ruyue.alarm.data.SavedPlaceDao
import com.ruyue.alarm.data.SavedPlaceRepository
import com.ruyue.alarm.data.SsidHistoryDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = Db.get(context)

    @Provides
    fun provideEventLogDao(db: AppDatabase): EventLogDao = db.eventLogDao()

    /** 提醒数据走独立库文件，避免改动 M0 证据日志库的 schema（见 ReminderDb 的注释） */
    @Provides
    @Singleton
    fun provideReminderDatabase(@ApplicationContext context: Context): ReminderDatabase =
        ReminderDb.get(context)

    @Provides
    fun provideReminderDao(db: ReminderDatabase): ReminderDao = db.reminderDao()

    @Provides
    fun provideSsidHistoryDao(db: ReminderDatabase): SsidHistoryDao = db.ssidHistoryDao()

    @Provides
    fun provideSavedPlaceDao(db: ReminderDatabase): SavedPlaceDao = db.savedPlaceDao()

    @Provides
    @Singleton
    fun provideSavedPlaceRepository(dao: SavedPlaceDao): SavedPlaceRepository =
        SavedPlaceRepository(dao)
}
