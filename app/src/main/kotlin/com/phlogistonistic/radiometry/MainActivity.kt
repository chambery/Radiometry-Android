package com.phlogistonistic.radiometry

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.phlogistonistic.radiometry.ui.RadiometryTheme
import com.phlogistonistic.radiometry.ui.StationListScreen

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* playback works either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RadiometryApp

        if (savedInstanceState == null) {
            // The playback notification (and its lock screen controls) needs this on Android 13+.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (app.settings.playLastStationOnStartup.value && app.player.ui.value.station == null) {
                app.store.find(app.settings.lastStationId)?.let(app.player::play)
            }
        }

        setContent {
            RadiometryTheme {
                StationListScreen(app.store, app.player, app.settings)
            }
        }
    }
}
