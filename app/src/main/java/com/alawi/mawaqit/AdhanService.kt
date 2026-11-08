package com.alawi.mawaqit

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class AdhanService : Service() {

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val stopper = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val p = Prayer.byKey(intent?.getStringExtra(Scheduler.EXTRA_PRAYER)) ?: Prayer.DHUHR
        val t = intent?.getLongExtra(Scheduler.EXTRA_TIME, System.currentTimeMillis()) ?: System.currentTimeMillis()
        val test = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false

        Notifier.ensureChannels(this)
        val n = buildNotification(p, t, test)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }
        play()
        return START_NOT_STICKY
    }

    private fun buildNotification(p: Prayer, t: Long, test: Boolean): Notification {
        val stopPi = PendingIntent.getService(
            this, 5, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 6, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (test) "تجربة صوت الأذان" else "حان الآن موعد أذان ${p.displayName(t)}"
        return Notification.Builder(this, Notifier.CH_ADHAN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText("${Prefs(this).city} • ${Fmt.time(t)}")
            .setContentIntent(open)
            .setDeleteIntent(stopPi)
            .setCategory(Notification.CATEGORY_ALARM)
            .setColor(0xFFD9B44A.toInt())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "إيقاف الأذان", stopPi
                ).build()
            )
            .build()
    }

    private fun play() {
        release()
        val prefs = Prefs(this)
        val custom = prefs.adhanUri?.let { Uri.parse(it) }
        val ok = custom != null && tryPlay(custom, false)
        if (!ok) {
            val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (def == null || !tryPlay(def, true)) { stopSelf(); return }
            // صوت المنبه الافتراضي: نوقفه بعد 45 ثانية
            handler.postDelayed(stopper, 45_000L)
        } else {
            // حد أقصى للأمان: 7 دقائق
            handler.postDelayed(stopper, 7 * 60_000L)
        }
    }

    private fun tryPlay(uri: Uri, loop: Boolean): Boolean = try {
        val mp = MediaPlayer()
        mp.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        mp.setDataSource(applicationContext, uri)
        mp.isLooping = loop
        mp.setOnCompletionListener { stopSelf() }
        mp.setOnErrorListener { _, _, _ -> stopSelf(); true }
        mp.prepare()
        mp.start()
        player = mp
        true
    } catch (e: Exception) {
        false
    }

    private fun release() {
        handler.removeCallbacks(stopper)
        try { player?.stop() } catch (e: Exception) { }
        player?.release()
        player = null
    }

    override fun onDestroy() {
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.alawi.mawaqit.STOP_ADHAN"
        const val EXTRA_TEST = "test"
        const val NOTIF_ID = 77

        fun start(ctx: Context, p: Prayer, t: Long, test: Boolean = false) {
            val i = Intent(ctx, AdhanService::class.java).apply {
                putExtra(Scheduler.EXTRA_PRAYER, p.key)
                putExtra(Scheduler.EXTRA_TIME, t)
                putExtra(EXTRA_TEST, test)
            }
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP))
        }
    }
}
