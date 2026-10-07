package app.personal.banglaai.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import app.personal.banglaai.MainActivity
import app.personal.banglaai.R

/**
 * Foreground media-playback service: keeps the process alive while reading with the screen off, shows a PRIVATE-SAFE
 * notification (generic text only — never file names or document text, and hidden on the lock screen) with pause/resume/stop,
 * and maps headset buttons through a MediaSession. It cannot start reading by itself; [ReadAloudController] is driven by the app UI.
 * Not exported. Status: compiles; real behaviour (Android 14+ foreground-service rules, headset keys) is device-test pending.
 */
class ReadAloudService : Service() {
    private var session: MediaSession? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "পড়ে শোনানো", NotificationManager.IMPORTANCE_LOW).apply {
            lockscreenVisibility = Notification.VISIBILITY_SECRET; setShowBadge(false) })
        session = MediaSession(this, "ReadAloud").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { ReadAloudController.resume() }
                override fun onPause() { ReadAloudController.pause() }
                override fun onStop() { ReadAloudController.stop() }
                override fun onSkipToNext() { ReadAloudController.next() }
                override fun onSkipToPrevious() { ReadAloudController.previous() }
            }); isActive = true }
        ReadAloudController.listener = { refresh() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> ReadAloudController.pause()
            ACTION_RESUME -> ReadAloudController.resume()
            ACTION_STOP -> { ReadAloudController.stop(); stopSelfClean(); return START_NOT_STICKY }
        }
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        return START_NOT_STICKY   // never auto-restarts after being killed: nothing resumes without the owner
    }

    private fun refresh() {
        val s = ReadAloudController.state
        session?.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(if (s == TtsReader.State.Playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0, 1f).build())
        if (s == TtsReader.State.Ready || s == TtsReader.State.Initialising) return stopSelfClean()   // reading finished or stopped
        getSystemService(NotificationManager::class.java).notify(ID, notification())
    }

    private fun notification(): Notification {
        fun pi(action: String) = PendingIntent.getService(this, action.hashCode(), Intent(this, ReadAloudService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE)
        val playing = ReadAloudController.isPlaying
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (playing) "পড়া চলছে" else "পড়া থামানো আছে")      // generic on purpose: no private text or filename
            .setVisibility(Notification.VISIBILITY_SECRET).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))   // opens the locked app; no private action
            .addAction(Notification.Action.Builder(null, if (playing) "থামান" else "চালু", pi(if (playing) ACTION_PAUSE else ACTION_RESUME)).build())
            .addAction(Notification.Action.Builder(null, "বন্ধ", pi(ACTION_STOP)).build())
            .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun stopSelfClean() { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }

    override fun onDestroy() { ReadAloudController.listener = null; session?.release(); session = null; super.onDestroy() }

    companion object {
        private const val CHANNEL = "read_aloud"; private const val ID = 4107
        const val ACTION_PAUSE = "app.personal.banglaai.PAUSE"; const val ACTION_RESUME = "app.personal.banglaai.RESUME"; const val ACTION_STOP = "app.personal.banglaai.STOP"
        fun start(c: Context) = c.startForegroundService(Intent(c, ReadAloudService::class.java))
    }
}
