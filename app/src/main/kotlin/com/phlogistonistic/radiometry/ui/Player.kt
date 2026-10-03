package com.phlogistonistic.radiometry.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.phlogistonistic.radiometry.playback.RadioPlayer.Identification
import com.phlogistonistic.radiometry.playback.RadioPlayer.State
import com.phlogistonistic.radiometry.playback.RadioPlayer.UiState

/** One-line description of what's on air: stream title, Shazam guess, or player status. */
private fun UiState.statusLine(): String = when (val s = state) {
    State.Idle -> "Tap a station to play"
    State.Buffering -> "Connecting…"
    State.Paused -> "Paused"
    is State.Failed -> s.message
    State.Playing -> streamTitle
        ?: identifiedSong?.displayText
        ?: if (identification == Identification.Listening) "Identifying song…" else station?.genre?.ifEmpty { "Live" } ?: "Live"
}

private val UiState.artworkUrl: String? get() = if (streamTitle == null) identifiedSong?.artworkUrl else null

@Composable
fun Artwork(url: String?, size: Dp, corner: Dp, placeholder: ImageVector = Icons.Filled.Radio, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(corner)
    val fallback = @Composable {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(placeholder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2))
        }
    }
    Box(modifier.size(size).clip(shape)) {
        if (url == null) fallback() else SubcomposeAsyncImage(
            model = url,
            contentDescription = "Album art",
            contentScale = ContentScale.Crop,
            loading = { fallback() },
            error = { fallback() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun MiniPlayer(ui: UiState, onToggle: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClickLabel = "Open Now Playing", onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(ui.artworkUrl, size = 44.dp, corner = 8.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    ui.station?.name ?: "Not Playing",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ui.statusLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ui.state is State.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (ui.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    ui: UiState,
    isFavorite: Boolean,
    onToggle: () -> Unit,
    onStop: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val station = ui.station ?: return
    val song = ui.identifiedSong.takeIf { ui.streamTitle == null }
    val title = ui.streamTitle ?: song?.title ?: station.name
    val subtitle = when {
        song != null -> song.artist
        ui.streamTitle != null -> station.name
        else -> station.genre
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 32.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Artwork(
                ui.artworkUrl, size = 280.dp, corner = 20.dp,
                modifier = Modifier.widthIn(max = 320.dp).aspectRatio(1f),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                when {
                    song != null -> Badge(Icons.Filled.GraphicEq, "Identified by Shazam")
                    ui.identification == Identification.Listening -> Badge(Icons.Filled.MusicNote, "Identifying song…")
                }
                when (val s = ui.state) {
                    State.Buffering -> Text("Connecting…", style = MaterialTheme.typography.bodySmall)
                    State.Paused -> Text("Paused", style = MaterialTheme.typography.bodySmall)
                    is State.Failed -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                IconButton(onClick = onStop, enabled = ui.state != State.Idle) {
                    Icon(Icons.Filled.Stop, "Stop", Modifier.size(36.dp))
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(88.dp)) {
                    Icon(
                        if (ui.isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = if (ui.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(88.dp),
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) Color(0xFFF5B400) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            song?.appleMusicUrl?.let { url ->
                OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp))
                    Text("Open in Apple Music", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
    }
}
