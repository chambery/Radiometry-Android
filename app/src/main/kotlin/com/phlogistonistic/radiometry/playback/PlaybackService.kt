package com.phlogistonistic.radiometry.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.phlogistonistic.radiometry.MainActivity
import com.phlogistonistic.radiometry.RadiometryApp

/** Hosts the media session: background playback, notification, lock screen and headset controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, RadiometryApp.instance.player.exoPlayer)
            .setSessionActivity(openApp)
            .build()
            // The app drives the player directly rather than through a MediaController, so register
            // the session ourselves; otherwise no playback notification is posted.
            .also(::addSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        // The player is app-scoped and outlives the service; only release the session.
        session?.release()
        session = null
        super.onDestroy()
    }
}
