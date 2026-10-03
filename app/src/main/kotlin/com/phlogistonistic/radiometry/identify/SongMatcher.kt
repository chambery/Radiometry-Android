package com.phlogistonistic.radiometry.identify

import com.phlogistonistic.radiometry.data.IdentifiedSong

sealed interface MatchOutcome {
    data class Match(val song: IdentifiedSong) : MatchOutcome
    data object NoMatch : MatchOutcome
    data class Unavailable(val reason: String) : MatchOutcome
    data class Error(val cause: Throwable) : MatchOutcome
}

/** Matches a short clip of 16-bit little-endian mono PCM against a song catalog. */
interface SongMatcher {
    /** Non-null when matching can't work at all (missing SDK or credentials). */
    val unavailableReason: String?

    /** Longest clip, in seconds, the catalog accepts per query. */
    val maxQuerySeconds: Double get() = 12.0

    suspend fun match(pcm: ByteArray, sampleRate: Int): MatchOutcome
}
