package com.phlogistonistic.radiometry.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phlogistonistic.radiometry.data.AppSettings
import com.phlogistonistic.radiometry.data.Station

@Composable
fun StationEditorDialog(station: Station?, onDismiss: () -> Unit, onSave: (Station) -> Unit) {
    var name by rememberSaveable { mutableStateOf(station?.name.orEmpty()) }
    var url by rememberSaveable { mutableStateOf(station?.url.orEmpty()) }
    var genre by rememberSaveable { mutableStateOf(station?.genre.orEmpty()) }

    val parsed = Uri.parse(url.trim())
    val urlValid = parsed.scheme?.lowercase() in setOf("http", "https") && !parsed.host.isNullOrEmpty()
    val valid = name.isNotBlank() && urlValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (station == null) "Add Station" else "Edit Station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("Stream URL") },
                    placeholder = { Text("https://example.com/stream.mp3") },
                    isError = url.isNotBlank() && !urlValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val base = station ?: Station(name = "", url = "")
                onSave(base.copy(name = name.trim(), url = url.trim(), genre = genre.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SettingsDialog(settings: AppSettings, onReset: () -> Unit, onDismiss: () -> Unit) {
    val playLast by settings.playLastStationOnStartup.collectAsStateWithLifecycle()
    var confirmingReset by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Play last station on startup", Modifier.weight(1f))
                    Switch(checked = playLast, onCheckedChange = settings::setPlayLastStationOnStartup)
                }
                TextButton(onClick = { confirmingReset = true }) {
                    Text("Reset Station List…", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Reset the station list to the defaults?") },
            text = { Text("Stations you added will be removed and favorites cleared.") },
            confirmButton = {
                TextButton(onClick = { onReset(); confirmingReset = false }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingReset = false }) { Text("Cancel") } },
        )
    }
}
