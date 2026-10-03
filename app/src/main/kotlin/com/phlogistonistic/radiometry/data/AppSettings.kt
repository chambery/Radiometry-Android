package com.phlogistonistic.radiometry.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _playLastStationOnStartup = MutableStateFlow(prefs.getBoolean(PLAY_LAST, false))
    val playLastStationOnStartup: StateFlow<Boolean> = _playLastStationOnStartup.asStateFlow()

    fun setPlayLastStationOnStartup(enabled: Boolean) {
        _playLastStationOnStartup.value = enabled
        prefs.edit { putBoolean(PLAY_LAST, enabled) }
    }

    var lastStationId: String?
        get() = prefs.getString(LAST_STATION, null)
        set(value) = prefs.edit { putString(LAST_STATION, value) }

    private companion object {
        const val PLAY_LAST = "playLastStationOnStartup"
        const val LAST_STATION = "lastStationID"
    }
}
