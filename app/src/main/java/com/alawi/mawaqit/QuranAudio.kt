package com.alawi.mawaqit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * تلاوة الشيخ عبدالله علي جابر (إمام المسجد الحرام سابقاً) — المصحف المرتل، تسجيل الأستوديو.
 * التشغيل من الإنترنت (mp3quran.net، واحتياطي quranicaudio.com)،
 * وتوقيت الآيات من mp3quran.net عشان نظلل الآية اللي يقرأها ونحفظ وين وقفت.
 */
object QuranPlayer {
    const val RECITER = "الشيخ عبدالله علي جابر"
    private const val PRIMARY = "https://server11.mp3quran.net/a_jbr/"
    private const val BACKUP = "https://download.quranicaudio.com/quran/abdullaah_alee_jaabir_studio/"
    private const val TIMING_READ = 76

    data class Timing(val ayah: Int, val start: Int, val end: Int)

    private var mp: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    var surah = 0
        private set
    var playing = false
        private set
    var preparing = false
        private set
    var autoNext = true
    private var timings: List<Timing>? = null
    private val timingCache = HashMap<Int, List<Timing>>()
    private var listener: (() -> Unit)? = null
    private var usedBackup = false

    fun setListener(l: (() -> Unit)?) { listener = l }
    private fun notifyUi() = handler.post { listener?.invoke() }

    private fun pad(n: Int) = n.toString().padStart(3, '0')

    fun positionMs(): Int = try { mp?.currentPosition ?: 0 } catch (e: Exception) { 0 }
    fun durationMs(): Int = try { mp?.duration ?: 0 } catch (e: Exception) { 0 }

    /** الآية اللي يقرأها الشيخ الحين (0 = البسملة/الاستعاذة) أو -1 لو التوقيت غير متوفر */
    fun currentAyah(): Int {
        val t = timings ?: return -1
        val pos = positionMs()
        var cur = -1
        for (x in t) { if (pos + 50 >= x.start) cur = x.ayah else break }
        return cur
    }

    fun startOfAyah(a: Int): Int? = timings?.firstOrNull { it.ayah == a }?.start

    /** وضع «آية وحدة»: يقرأ الآية بصوت الشيخ ويوقف بعدها (ما يغيّر «وين وقفت») */
    var singleSurah = 0
        private set
    var singleAyah = 0
        private set
    fun isSingle(s: Int, a: Int) = singleAyah == a && singleSurah == s && (playing || preparing)

    fun playAyah(ctx: Context, s: Int, a: Int) {
        val app = ctx.applicationContext
        stopInternal()
        singleSurah = s; singleAyah = a
        preparing = true; notifyUi()
        val cached = timingCache[s]
        if (cached != null) { play(app, s, a); singleSurah = s; singleAyah = a; watchSingle(app); return }
        Thread {
            val t = fetchTimings(s)
            handler.post {
                if (singleAyah != a || singleSurah != s) return@post
                if (t != null) timingCache[s] = t
                play(app, s, a); singleSurah = s; singleAyah = a
                watchSingle(app)
            }
        }.start()
    }

    private val SINGLE = Any()
    private fun watchSingle(app: Context) {
        handler.removeCallbacksAndMessages(SINGLE)
        handler.postAtTime({
            if (singleAyah > 0) {
                val end = timings?.firstOrNull { it.ayah == singleAyah }?.end
                if (!preparing && !playing && mp == null) { singleAyah = 0; return@postAtTime }
                if (end != null && end > 0 && playing && positionMs() >= end - 60) {
                    singleAyah = 0; singleSurah = 0
                    stopInternal(); QuranAudioService.stop(app); notifyUi()
                } else watchSingle(app)
            }
        }, SINGLE, android.os.SystemClock.uptimeMillis() + 120)
    }

    /** يبدأ سورة؛ لو fromAyah > 1 يقفز لها بعد ما يجهز التوقيت */
    fun play(ctx: Context, s: Int, fromAyah: Int = 1, fromMs: Int = -1) {
        val app = ctx.applicationContext
        stopInternal()
        singleAyah = 0; singleSurah = 0
        surah = s
        preparing = true
        usedBackup = false
        timings = timingCache[s]
        notifyUi()
        QuranAudioService.ensure(app)
        if (timings == null) Thread {
            val t = fetchTimings(s)
            handler.post {
                if (surah == s) {
                    timings = t
                    if (t != null) timingCache[s] = t
                    if (!preparing && fromAyah > 1 && fromMs < 0) startOfAyah(fromAyah)?.let { seek(it) }
                    notifyUi()
                }
            }
        }.start()
        val local = QuranDownloads.file(app, s)
        if (local != null && QuranDownloads.isReady(app, s)) { usedBackup = true; startPlayer(app, local.absolutePath, s, fromAyah, fromMs) }
        else startPlayer(app, PRIMARY + pad(s) + ".mp3", s, fromAyah, fromMs)
    }

    private fun startPlayer(app: Context, url: String, s: Int, fromAyah: Int, fromMs: Int) {
        val p = MediaPlayer()
        mp = p
        try {
            p.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            p.setWakeMode(app, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(url)
            p.setOnPreparedListener {
                if (mp !== it) return@setOnPreparedListener
                preparing = false
                val target = when {
                    fromMs > 0 -> fromMs
                    fromAyah > 1 -> startOfAyah(fromAyah) ?: 0
                    else -> 0
                }
                if (target > 0) it.seekTo(target)
                it.start()
                playing = true
                QuranAudioService.refresh(app)
                notifyUi()
                tick(app)
            }
            p.setOnCompletionListener {
                playing = false
                if (singleAyah > 0) { singleAyah = 0; singleSurah = 0; QuranAudioService.stop(app); notifyUi() }
                else {
                    QuranPrefs.setListen(app, s, 0)
                    if (autoNext && s < 114) play(app, s + 1) else { QuranAudioService.stop(app); notifyUi() }
                }
            }
            p.setOnErrorListener { _, _, _ ->
                if (!usedBackup) {
                    usedBackup = true
                    try { p.release() } catch (e: Exception) { }
                    startPlayer(app, BACKUP + pad(s) + ".mp3", s, fromAyah, fromMs)
                } else {
                    preparing = false; playing = false
                    QuranAudioService.stop(app)
                    notifyUi()
                }
                true
            }
            p.prepareAsync()
        } catch (e: Exception) {
            preparing = false
            notifyUi()
        }
    }

    /** نحفظ مكان الاستماع كل ثانيتين («وين وقفت») */
    private fun tick(app: Context) {
        handler.removeCallbacksAndMessages(TICK)
        handler.postAtTime({
            if (playing) {
                if (singleAyah == 0) QuranPrefs.setListen(app, surah, positionMs())
                notifyUi()
                tick(app)
            }
        }, TICK, android.os.SystemClock.uptimeMillis() + 1000)
    }
    private val TICK = Any()

    fun toggle(ctx: Context) {
        val p = mp ?: return
        if (playing) { p.pause(); playing = false; QuranPrefs.setListen(ctx, surah, positionMs()) }
        else { p.start(); playing = true; tick(ctx.applicationContext) }
        QuranAudioService.refresh(ctx.applicationContext)
        notifyUi()
    }

    fun seek(ms: Int) { try { mp?.seekTo(ms) } catch (e: Exception) { } }

    fun stop(ctx: Context) {
        if (surah > 0 && mp != null && singleAyah == 0) QuranPrefs.setListen(ctx, surah, positionMs())
        singleAyah = 0; singleSurah = 0
        stopInternal()
        QuranAudioService.stop(ctx.applicationContext)
        notifyUi()
    }

    private fun stopInternal() {
        handler.removeCallbacksAndMessages(TICK)
        try { mp?.stop() } catch (e: Exception) { }
        try { mp?.release() } catch (e: Exception) { }
        mp = null
        playing = false
        preparing = false
    }

    private fun fetchTimings(s: Int): List<Timing>? = try {
        val c = URL("https://mp3quran.net/api/v3/ayat_timing?surah=$s&read=$TIMING_READ").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/json")
        val body = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val arr = JSONArray(body)
        val out = ArrayList<Timing>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val st = o.optDouble("start_time", Double.NaN)
            if (!st.isNaN()) out.add(Timing(o.optInt("ayah"), st.toInt(), o.optDouble("end_time", st).toInt()))
        }
        out.sortBy { it.start }
        out.ifEmpty { null }
    } catch (e: Exception) { null }
}

/** خدمة في المقدمة عشان التلاوة تكمل والشاشة مقفلة، مع أزرار في الإشعار */
class QuranAudioService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACT_TOGGLE -> QuranPlayer.toggle(this)
            ACT_STOP -> { QuranPlayer.stop(this); return START_NOT_STICKY }
        }
        val n = build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(ID, n)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val ID = 90
        private const val CH = "quran_audio_v1"
        const val ACT_TOGGLE = "com.alawi.mawaqit.QURAN_TOGGLE"
        const val ACT_STOP = "com.alawi.mawaqit.QURAN_STOP"

        fun ensure(ctx: Context) {
            try { ctx.startForegroundService(Intent(ctx, QuranAudioService::class.java)) } catch (e: Exception) { }
        }

        fun refresh(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            try { nm.notify(ID, build(ctx)) } catch (e: SecurityException) { }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, QuranAudioService::class.java))
        }

        fun build(ctx: Context): Notification {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CH, "تلاوة القرآن", NotificationManager.IMPORTANCE_LOW))
            val s = QuranPlayer.surah
            val name = if (s > 0) Quran.surah(ctx, s).name else ""
            fun act(label: String, action: String, code: Int, icon: Int) = Notification.Action.Builder(
                Icon.createWithResource(ctx, icon), label,
                PendingIntent.getService(ctx, code, Intent(ctx, QuranAudioService::class.java).setAction(action),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
            val open = PendingIntent.getActivity(ctx, 91, Intent(ctx, QuranReaderActivity::class.java)
                .putExtra("s", if (s > 0) s else 1).putExtra("follow", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            return Notification.Builder(ctx, CH)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(if (name.isNotEmpty()) "سورة $name" else "تلاوة القرآن")
                .setContentText(QuranPlayer.RECITER)
                .setContentIntent(open)
                .setOngoing(QuranPlayer.playing)
                .setColor(C_GOLD)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(act(if (QuranPlayer.playing) "إيقاف مؤقت" else "تشغيل", ACT_TOGGLE, 92,
                    if (QuranPlayer.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play))
                .addAction(act("إغلاق", ACT_STOP, 93, android.R.drawable.ic_menu_close_clear_cancel))
                .setStyle(Notification.MediaStyle().setShowActionsInCompactView(0, 1))
                .build()
        }
    }
}
