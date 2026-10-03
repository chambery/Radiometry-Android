package com.phlogistonistic.radiometry.identify

import com.phlogistonistic.radiometry.BuildConfig
import com.phlogistonistic.radiometry.data.IdentifiedSong
import com.shazam.shazamkit.AudioSampleRateInHz
import com.shazam.shazamkit.DeveloperToken
import com.shazam.shazamkit.DeveloperTokenProvider
import com.shazam.shazamkit.MatchResult
import com.shazam.shazamkit.Session
import com.shazam.shazamkit.ShazamKit
import com.shazam.shazamkit.ShazamKitResult

/** Built when app/libs/shazamkit-android-release.aar is present. */
fun createSongMatcher(): SongMatcher = ShazamMatcher(BuildConfig.SHAZAM_DEVELOPER_TOKEN)

private class ShazamMatcher(private val token: String) : SongMatcher {
    override val unavailableReason: String? =
        if (token.isBlank()) "No Shazam developer token. Set shazam.developerToken in local.properties (see tools/make-shazam-token.py)." else null

    private val session: Session? by lazy {
        val catalog = ShazamKit.createShazamCatalog(DeveloperTokenProvider { DeveloperToken(token) })
        when (val result = ShazamKit.createSession(catalog)) {
            is ShazamKitResult.Success -> result.data
            is ShazamKitResult.Failure -> null
        }
    }

    override suspend fun match(pcm: ByteArray, sampleRate: Int): MatchOutcome {
        val session = session ?: return MatchOutcome.Unavailable("Couldn't start a ShazamKit session.")
        val rate = when (sampleRate) {
            48_000 -> AudioSampleRateInHz.SAMPLE_RATE_48000
            32_000 -> AudioSampleRateInHz.SAMPLE_RATE_32000
            16_000 -> AudioSampleRateInHz.SAMPLE_RATE_16000
            else -> AudioSampleRateInHz.SAMPLE_RATE_44100
        }
        val generator = when (val result = ShazamKit.createSignatureGenerator(rate)) {
            is ShazamKitResult.Success -> result.data
            is ShazamKitResult.Failure -> return MatchOutcome.Error(result.reason)
        }
        generator.append(pcm, pcm.size, System.currentTimeMillis())
        return when (val result = session.match(generator.generateSignature())) {
            is MatchResult.Match -> {
                val item = result.matchedMediaItems.firstOrNull() ?: return MatchOutcome.NoMatch
                MatchOutcome.Match(
                    IdentifiedSong(
                        title = item.title ?: return MatchOutcome.NoMatch,
                        artist = item.artist.orEmpty(),
                        artworkUrl = item.artworkURL?.toString(),
                        appleMusicUrl = item.appleMusicURL?.toString(),
                    )
                )
            }
            is MatchResult.NoMatch -> MatchOutcome.NoMatch
            is MatchResult.Error -> MatchOutcome.Error(result.exception)
        }
    }
}
