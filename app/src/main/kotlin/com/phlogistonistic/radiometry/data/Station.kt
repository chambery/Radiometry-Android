package com.phlogistonistic.radiometry.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Station(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val genre: String = "",
    val isFavorite: Boolean = false,
) {
    companion object {
        val defaults = listOf(
            Station(name = "WVFS 89.7 Tallahassee", url = "http://voice.wvfs.fsu.edu:8000/stream", genre = "Indie/Alternative"),
            Station(name = "SomaFM Groove Salad", url = "https://ice1.somafm.com/groovesalad-128-mp3", genre = "Ambient / Downtempo"),
            Station(name = "SomaFM Drone Zone", url = "https://ice1.somafm.com/dronezone-128-mp3", genre = "Ambient"),
            Station(name = "SomaFM DEF CON Radio", url = "https://ice1.somafm.com/defcon-128-mp3", genre = "Electronic"),
            Station(name = "SomaFM Secret Agent", url = "https://ice1.somafm.com/secretagent-128-mp3", genre = "Lounge"),
            Station(name = "Radio Paradise Main Mix", url = "https://stream.radioparadise.com/aac-320", genre = "Eclectic"),
            Station(name = "KEXP 90.3 Seattle", url = "https://kexp.streamguys1.com/kexp160.aac", genre = "Indie / Alternative"),
        )
    }
}

/** A song identified by Shazam. */
data class IdentifiedSong(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    val appleMusicUrl: String? = null,
) {
    val displayText: String get() = if (artist.isEmpty()) title else "$title — $artist"
}
