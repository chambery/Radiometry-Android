package com.phlogistonistic.radiometry.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
import com.phlogistonistic.radiometry.data.AppSettings
import com.phlogistonistic.radiometry.data.IdentifiedSong
import com.phlogistonistic.radiometry.data.Station
import com.phlogistonistic.radiometry.identify.MatchOutcome
import com.phlogistonistic.radiometry.identify.SongIdentifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RadioPlayer(
    private val context: Context,
    private val settings: AppSettings,
    private val identifier: SongIdentifier,
) {
    sealed interface State {
        data object Idle : State
        data object Buffering : State
        data object Playing : State
        data object Paused : State
        data class Failed(val message: String) : State
    }

    sealed interface Identification {
        data object Idle : Identification
        data object Listening : Identification
        data class Unavailable(val reason: String) : Identification
    }

    data class UiState(
        val station: Station? = null,
        val state: State = State.Idle,
        /** Title published by the stream itself (ICY metadata). */
        val streamTitle: String? = null,
        /** Best guess from Shazam, used only when the stream publishes no title. */
        val identifiedSong: IdentifiedSong? = null,
        val identification: Identification = Identification.Idle,
    ) {
        val isPlaying: Boolean get() = state == State.Playing || state == State.Buffering
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var identifyJob: Job? = null
    private var shazamDisabledReason: String? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = syncState()

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "playback error ${error.errorCodeName}", error)
                _ui.update { it.copy(state = State.Failed(describe(error))) }
            }

            override fun onMetadata(metadata: Metadata) {
                val title = (0 until metadata.length()).map { metadata[it] }
                    .filterIsInstance<IcyInfo>()
                    .firstNotNullOfOrNull { it.title?.trim()?.takeIf(String::isNotEmpty) }
                    ?: return
                if (title == _ui.value.streamTitle) return
                Log.i(TAG, "stream title: $title")
                // The stream speaks for itself; drop any Shazam guess.
                stopIdentifying()
                _ui.update { it.copy(streamTitle = title, identifiedSong = null) }
                // Show the song in the notification and lock screen, not just in the app.
                _ui.value.station?.let { station -> updateSessionMetadata(station, songFromStreamTitle(title)) }
            }
        })
    }

    fun play(station: Station) {
        val current = _ui.value
        if (current.station?.id == station.id && current.state == State.Playing) return
        context.startService(Intent(context, PlaybackService::class.java))
        settings.lastStationId = station.id
        _ui.value = UiState(station = station, state = State.Buffering)
        exoPlayer.setMediaItem(mediaItem(station))
        exoPlayer.prepare()
        exoPlayer.play()
        startIdentifying(station)
    }

    fun toggle() {
        val current = _ui.value
        when {
            current.isPlaying -> pause()
            current.station != null -> resume()
        }
    }

    fun pause() {
        stopIdentifying()
        exoPlayer.pause()
        _ui.update { it.copy(state = State.Paused) }
    }

    /** Live streams go stale while paused; reconnect for a fresh buffer. */
    fun resume() {
        val station = _ui.value.station ?: return
        _ui.update { it.copy(state = State.Idle) }
        play(station)
    }

    fun stop() {
        stopIdentifying()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _ui.update { it.copy(state = State.Idle, streamTitle = null, identifiedSong = null) }
    }

    /** Keeps the current station's display info in sync after an edit. */
    fun stationEdited(station: Station) {
        if (_ui.value.station?.id == station.id) _ui.update { it.copy(station = station) }
    }

    private fun syncState() {
        val current = _ui.value
        if (current.station == null || current.state is State.Failed) return
        val state = when {
            exoPlayer.playerError != null -> return
            exoPlayer.isPlaying -> State.Playing
            exoPlayer.playWhenReady && exoPlayer.playbackState == Player.STATE_BUFFERING -> State.Buffering
            exoPlayer.mediaItemCount == 0 -> State.Idle
            !exoPlayer.playWhenReady -> State.Paused
            else -> current.state
        }
        if (state != current.state) _ui.update { it.copy(state = state) }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Can't reach the stream. Check your connection."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The station's stream isn't available."
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "This stream uses an insecure connection that's blocked."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This stream's format isn't supported."
        else -> "Unable to play stream (${error.errorCodeName})."
    }

    /** Same URI, new metadata: Media3 swaps it in without interrupting playback. */
    private fun updateSessionMetadata(station: Station, song: IdentifiedSong) {
        if (exoPlayer.mediaItemCount > 0) exoPlayer.replaceMediaItem(0, mediaItem(station, song))
    }

    /** Parses the conventional ICY "Artist - Title" StreamTitle format. */
    private fun songFromStreamTitle(title: String): IdentifiedSong {
        val parts = title.split(" - ", limit = 2)
        return if (parts.size == 2) IdentifiedSong(title = parts[1].trim(), artist = parts[0].trim())
        else IdentifiedSong(title = title, artist = "")
    }

    private fun mediaItem(station: Station, song: IdentifiedSong? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(station.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song?.title ?: station.name)
                    .setArtist(
                        when {
                            song == null -> station.genre
                            song.artist.isEmpty() -> station.name
                            else -> "${song.artist} · ${station.name}"
                        }
                    )
                    .setStation(station.name)
                    .setArtworkUri(song?.artworkUrl?.let(Uri::parse))
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()

    // region Shazam fallback

    private fun startIdentifying(station: Station) {
        stopIdentifying()
        shazamDisabledReason?.let { reason ->
            _ui.update { it.copy(identification = Identification.Unavailable(reason)) }
            return
        }
        identifyJob = scope.launch {
            // Give the stream a moment to deliver its own metadata first.
            delay(8_000)
            while (isActive) {
                val current = _ui.value
                if (current.streamTitle != null || current.station?.id != station.id) return@launch
                Log.i(TAG, "identifying ${station.name}")
                _ui.update { it.copy(identification = Identification.Listening) }
                var retryAfter = 60_000L
                when (val outcome = identifier.identify(station.url)) {
                    is MatchOutcome.Match -> {
                        Log.i(TAG, "match: ${outcome.song.displayText}")
                        if (_ui.value.streamTitle != null) return@launch
                        _ui.update { it.copy(identifiedSong = outcome.song, identification = Identification.Idle) }
                        updateSessionMetadata(station, outcome.song)
                    }
                    MatchOutcome.NoMatch -> {
                        // Talk, ads, or an unknown track; try again sooner.
                        _ui.update { it.copy(identifiedSong = null, identification = Identification.Idle) }
                        retryAfter = 30_000
                    }
                    is MatchOutcome.Unavailable -> {
                        Log.i(TAG, "identification unavailable: ${outcome.reason}")
                        shazamDisabledReason = outcome.reason
                        _ui.update { it.copy(identification = Identification.Unavailable(outcome.reason)) }
                        return@launch
                    }
                    is MatchOutcome.Error -> {
                        Log.w(TAG, "identify error", outcome.cause)
                        _ui.update { it.copy(identification = Identification.Idle) }
                        retryAfter = 45_000
                    }
                }
                delay(retryAfter)
            }
        }
    }

    private fun stopIdentifying() {
        identifyJob?.cancel()
        identifyJob = null
        if (_ui.value.identification == Identification.Listening) {
            _ui.update { it.copy(identification = Identification.Idle) }
        }
    }

    // endregion

    private companion object {
        const val TAG = "RadiometryPlayer"
    }
}
