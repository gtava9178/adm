package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ChatWallpaper
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "resalah_prefs")

class UserPreferencesStore(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val wallpaperKey = stringPreferencesKey("chat_wallpaper")

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromWire(prefs[themeKey])
    }

    val wallpaperFlow: Flow<ChatWallpaper> = context.dataStore.data.map { prefs ->
        ChatWallpaper.fromWire(prefs[wallpaperKey])
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.wireValue
        }
    }

    suspend fun setChatWallpaper(wallpaper: ChatWallpaper) {
        context.dataStore.edit { prefs ->
            prefs[wallpaperKey] = wallpaper.wireValue
        }
    }
}
