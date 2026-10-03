package com.phlogistonistic.radiometry.identify

/** Built when app/libs/shazamkit-android-release.aar is absent. */
fun createSongMatcher(): SongMatcher = object : SongMatcher {
    override val unavailableReason =
        "ShazamKit isn't bundled. Add shazamkit-android-release.aar to app/libs and rebuild."

    override suspend fun match(pcm: ByteArray, sampleRate: Int) = MatchOutcome.Unavailable(unavailableReason)
}
