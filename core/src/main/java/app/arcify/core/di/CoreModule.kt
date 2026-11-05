package app.arcify.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.arcify.core.data.ArcifyDatabase
import app.arcify.core.data.FocusSessionDao
import app.arcify.core.data.TagDao
import app.arcify.core.timer.WallClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ArcifyDatabase =
        Room.databaseBuilder(context, ArcifyDatabase::class.java, "arcify.db").build()

    @Provides
    fun focusSessionDao(db: ArcifyDatabase): FocusSessionDao = db.focusSessionDao()

    @Provides
    fun tagDao(db: ArcifyDatabase): TagDao = db.tagDao()

    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun wallClock(): WallClock = WallClock { System.currentTimeMillis() }
}
