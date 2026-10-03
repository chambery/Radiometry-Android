package com.phlogistonistic.radiometry

import android.app.Application
import com.phlogistonistic.radiometry.data.AppSettings
import com.phlogistonistic.radiometry.data.StationStore
import com.phlogistonistic.radiometry.identify.SongIdentifier
import com.phlogistonistic.radiometry.identify.createSongMatcher
import com.phlogistonistic.radiometry.playback.RadioPlayer

class RadiometryApp : Application() {
    lateinit var store: StationStore
        private set
    lateinit var settings: AppSettings
        private set
    lateinit var player: RadioPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = StationStore(this)
        settings = AppSettings(this)
        player = RadioPlayer(this, settings, SongIdentifier(this, createSongMatcher()))
    }

    companion object {
        lateinit var instance: RadiometryApp
            private set
    }
}
