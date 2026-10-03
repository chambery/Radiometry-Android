package com.phlogistonistic.radiometry.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.phlogistonistic.radiometry.data.AppSettings
import com.phlogistonistic.radiometry.data.Station
import com.phlogistonistic.radiometry.data.StationStore
import com.phlogistonistic.radiometry.playback.RadioPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationListScreen(store: StationStore, player: RadioPlayer, settings: AppSettings) {
    val stations by store.stations.collectAsStateWithLifecycle()
    val ui by player.ui.collectAsStateWithLifecycle()

    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Station?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showingSettings by remember { mutableStateOf(false) }
    var showingNowPlaying by remember { mutableStateOf(false) }

    val visible = stations
        .filter { !favoritesOnly || it.isFavorite }
        .filter { search.isBlank() || it.name.contains(search, true) || it.genre.contains(search, true) }

    fun delete(station: Station) {
        if (ui.station?.id == station.id) player.stop()
        store.delete(station.id)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (favoritesOnly) "Favorites" else "Radiometry") },
                actions = {
                    IconToggleButton(checked = favoritesOnly, onCheckedChange = { favoritesOnly = it }) {
                        Icon(
                            if (favoritesOnly) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = "Show favorites only",
                        )
                    }
                    IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add station") }
                    IconButton(onClick = { showingSettings = true }) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        bottomBar = {
            if (ui.station != null) {
                MiniPlayer(
                    ui = ui,
                    onToggle = player::toggle,
                    onOpen = { showingNowPlaying = true },
                    modifier = Modifier.navigationBarsPadding().padding(8.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search stations") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Filled.Clear, "Clear search") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (visible.isEmpty()) {
                EmptyState(
                    title = when {
                        search.isNotBlank() -> "No Results"
                        favoritesOnly -> "No Favorites"
                        else -> "No Stations"
                    },
                    message = when {
                        search.isNotBlank() -> "No stations match “$search”."
                        favoritesOnly -> "Tap the star on a station to add it here."
                        else -> "Tap + to add an internet radio stream."
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(visible, key = { it.id }) { station ->
                        StationRow(
                            station = station,
                            isCurrent = ui.station?.id == station.id,
                            isPlaying = ui.station?.id == station.id && ui.isPlaying,
                            onPlay = { player.play(station) },
                            onToggleFavorite = { store.toggleFavorite(station.id) },
                            onEdit = { editing = station },
                            onDelete = { delete(station) },
                        )
                        HorizontalDivider(Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }

    if (adding) {
        StationEditorDialog(station = null, onDismiss = { adding = false }) { store.add(it); adding = false }
    }
    editing?.let { station ->
        StationEditorDialog(station = station, onDismiss = { editing = null }) {
            store.update(it)
            player.stationEdited(it)
            editing = null
        }
    }
    if (showingSettings) {
        SettingsDialog(settings = settings, onReset = store::resetToDefaults, onDismiss = { showingSettings = false })
    }
    if (showingNowPlaying && ui.station != null) {
        val station = stations.firstOrNull { it.id == ui.station?.id } ?: ui.station!!
        NowPlayingSheet(
            ui = ui,
            isFavorite = station.isFavorite,
            onToggle = player::toggle,
            onStop = player::stop,
            onToggleFavorite = { store.toggleFavorite(station.id) },
            onDismiss = { showingNowPlaying = false },
        )
    }
}

@Composable
private fun StationRow(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onPlay),
        leadingContent = {
            Icon(
                if (isPlaying) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Radio,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        },
        headlineContent = {
            Text(station.name, fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal)
        },
        supportingContent = if (station.genre.isNotEmpty()) ({ Text(station.genre) }) else null,
        trailingContent = {
            Box {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (station.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (station.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (station.isFavorite) Color(0xFFF5B400) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("Copy stream URL") }, onClick = {
                        menuOpen = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(station.name, station.url))
                    })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        },
    )
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(Icons.Filled.Radio, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}
