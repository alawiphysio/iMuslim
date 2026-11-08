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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * خدمة الصوت: تشغّل الأذان، وتكلّمك بصوت عربي (تذكير قبل الأذان، وقت الأذان، وقت الإقامة).
 */
class AdhanService : Service() {

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
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
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_ADHAN
        val test = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false
        val speech = intent?.getStringExtra(EXTRA_SPEECH)
        val prefs = Prefs(this)

        Notifier.ensureChannels(this)
        val title = when (mode) {
            MODE_SPEAK -> intent?.getStringExtra(EXTRA_TITLE) ?: "تذكير الصلاة"
            else -> if (test) "تجربة ${if (p == Prayer.FAJR) "أذان الفجر" else "الأذان"}" else "حان الآن موعد أذان ${p.displayName(t)}"
        }
        val fullScreen = if (mode == MODE_ADHAN && prefs.fullScreenOn) PendingIntent.getActivity(
            this, 8, Intent(this, AdhanActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Scheduler.EXTRA_PRAYER, p.key)
                putExtra(Scheduler.EXTRA_TIME, t)
            }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ) else null
        val n = buildNotification(title, "${prefs.city} • ${Fmt.time(t)}", fullScreen)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, n)
        }

        release()
        when (mode) {
            MODE_SPEAK -> {
                if (prefs.voiceOn && speech != null) speak(speech) { stopSelf() } else stopSelf()
                handler.postDelayed(stopper, 60_000L)
            }
            else -> {
                val silent = isPhoneSilent()
                if (silent || isDndOn()) {
                    if (prefs.flashOn) Effects.startFlash(this)
                    Effects.vibrate(this)
                }
                if (isDndOn() && prefs.adhanInDnd) Effects.allowAlarmsThroughDnd(this)
                if (silent && !prefs.adhanInSilent && !test) {
                    // اخترت إن الأذان ما يطلع صوت في الصامت: ألوان + فلاش + اهتزاز فقط
                    handler.postDelayed(stopper, 60_000L)
                    return START_NOT_STICKY
                }
                // قبل الأذان يقول جملة قصيرة ثم يبدأ الأذان
                val intro = if (test) null else Voice.adhanIntro(prefs.userName, p, t)
                if (prefs.voiceOn && intro != null) speak(intro) { play(p) } else play(p)
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, text: String, fullScreen: PendingIntent? = null): Notification {
        val stopPi = PendingIntent.getService(
            this, 5, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 6, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, Notifier.CH_ADHAN)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setDeleteIntent(stopPi)
            .setCategory(Notification.CATEGORY_ALARM)
            .setColor(0xFFD9B44A.toInt())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .also { b -> if (fullScreen != null) b.setFullScreenIntent(fullScreen, true) }
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "إيقاف", stopPi
                ).build()
            )
            .build()
    }

    // ---------------- الكلام ----------------

    private fun speak(text: String, onDone: () -> Unit) {
        var finished = false
        val done = {
            handler.post {
                if (!finished) { finished = true; onDone() }
            }
        }
        // لو محرك الصوت ما ردّ خلال 15 ثانية نكمل بدونه
        handler.postDelayed({ done() }, 15_000L)
        tts = TextToSpeech(applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) { done(); return@TextToSpeech }
            val lang = engine.setLanguage(Locale("ar", "SA"))
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale("ar"))
            }
            // نختار صوت رجل عربي لو متوفر في الجوال (Google: ar-xa-x-ard / are)
            try {
                val ar = engine.voices?.filter { it.locale.language == "ar" && !it.isNetworkConnectionRequired } ?: emptyList()
                val male = ar.firstOrNull { it.name.contains("male", true) && !it.name.contains("female", true) }
                    ?: ar.firstOrNull { it.name.contains("-ard") || it.name.contains("-are") }
                if (male != null) engine.voice = male
            } catch (e: Exception) { }
            engine.setSpeechRate(0.9f)
            engine.setPitch(0.92f)
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { done() }
                @Deprecated("old API")
                override fun onError(utteranceId: String?) { done() }
                override fun onError(utteranceId: String?, errorCode: Int) { done() }
            })
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f) }
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "mawaqit") != TextToSpeech.SUCCESS) done()
        }
    }

    // ---------------- الأذان ----------------

    private fun play(p: Prayer) {
        try { player?.release() } catch (e: Exception) { }
        player = null
        val prefs = Prefs(this)
        // الفجر له أذانه الخاص، ولو ما اخترت له ملف يستخدم الأذان العادي
        val chosen = if (p == Prayer.FAJR) (prefs.fajrUri ?: prefs.adhanUri) else prefs.adhanUri
        val custom = chosen?.let { Uri.parse(it) }
        val ok = custom != null && tryPlay(custom, false)
        if (!ok) {
            val def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (def == null || !tryPlay(def, true)) { stopSelf(); return }
            handler.postDelayed(stopper, 45_000L)
        } else {
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
        handler.removeCallbacksAndMessages(null)
        try { player?.stop() } catch (e: Exception) { }
        player?.release()
        player = null
        try { tts?.stop(); tts?.shutdown() } catch (e: Exception) { }
        tts = null
    }

    private fun isPhoneSilent(): Boolean {
        val am = getSystemService(android.media.AudioManager::class.java) ?: return false
        return am.ringerMode != android.media.AudioManager.RINGER_MODE_NORMAL
    }

    private fun isDndOn(): Boolean {
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return false
        return nm.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL
    }

    override fun onDestroy() {
        Effects.stopFlash(this)
        Effects.restoreDnd(this)
        release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.alawi.mawaqit.STOP_ADHAN"
        const val EXTRA_TEST = "test"
        const val EXTRA_MODE = "mode"
        const val EXTRA_SPEECH = "speech"
        const val EXTRA_TITLE = "title"
        const val MODE_ADHAN = "adhan"
        const val MODE_SPEAK = "speak"
        const val NOTIF_ID = 77

        fun start(ctx: Context, p: Prayer, t: Long, test: Boolean = false) {
            val i = Intent(ctx, AdhanService::class.java).apply {
                putExtra(Scheduler.EXTRA_PRAYER, p.key)
                putExtra(Scheduler.EXTRA_TIME, t)
                putExtra(EXTRA_TEST, test)
                putExtra(EXTRA_MODE, MODE_ADHAN)
            }
            ctx.startForegroundService(i)
        }

        /** يكلّمك بجملة (تذكير) */
        fun say(ctx: Context, p: Prayer, t: Long, title: String, speech: String) {
            val i = Intent(ctx, AdhanService::class.java).apply {
                putExtra(Scheduler.EXTRA_PRAYER, p.key)
                putExtra(Scheduler.EXTRA_TIME, t)
                putExtra(EXTRA_MODE, MODE_SPEAK)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SPEECH, speech)
            }
            ctx.startForegroundService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** مؤثرات قوية: فلاش، اهتزاز، تجاوز عدم الإزعاج */
object Effects {
    private val handler = Handler(Looper.getMainLooper())
    private var flashing = false
    private var savedFilter: Int? = null

    fun startFlash(ctx: Context) {
        val cm = ctx.getSystemService(android.hardware.camera2.CameraManager::class.java) ?: return
        val id = try {
            cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) { null } ?: return
        flashing = true
        var on = false
        var count = 0
        val r = object : Runnable {
            override fun run() {
                if (!flashing || count > 60) { try { cm.setTorchMode(id, false) } catch (e: Exception) { }; return }
                on = !on; count++
                try { cm.setTorchMode(id, on) } catch (e: Exception) { flashing = false; return }
                handler.postDelayed(this, if (on) 250L else 650L)
            }
        }
        handler.post(r)
    }

    fun stopFlash(ctx: Context) {
        if (!flashing) return
        flashing = false
        val cm = ctx.getSystemService(android.hardware.camera2.CameraManager::class.java) ?: return
        try { cm.cameraIdList.forEach { try { cm.setTorchMode(it, false) } catch (e: Exception) { } } } catch (e: Exception) { }
    }

    fun vibrate(ctx: Context) {
        val pattern = longArrayOf(0, 900, 500, 900, 500, 900, 500, 1400)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = ctx.getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                v?.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (e: Exception) { }
    }

    /** لو وضع «صمت تام» مانع المنبهات: نسمح بالمنبهات مؤقتاً ونرجعه بعد الأذان (يحتاج إذن) */
    fun allowAlarmsThroughDnd(ctx: Context) {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
        if (!nm.isNotificationPolicyAccessGranted) return
        if (nm.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE) {
            savedFilter = nm.currentInterruptionFilter
            try { nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS) } catch (e: Exception) { savedFilter = null }
        }
    }

    fun restoreDnd(ctx: Context) {
        val f = savedFilter ?: return
        savedFilter = null
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java) ?: return
        try { nm.setInterruptionFilter(f) } catch (e: Exception) { }
    }
}

/** الجمل اللي يقولها التطبيق، تتغير عشان ما تكون مملة */
object Voice {
    private fun pick(vararg s: String) = s[(System.currentTimeMillis() / 60_000L % s.size).toInt()]

    fun before(name: String, p: Prayer, adhanT: Long, minutes: Int): String {
        val pn = p.displayName(adhanT)
        return if (p == Prayer.FAJR) pick(
            "يا $name، باقي $minutes دقائق على أذان الفجر. قم وتوضأ، الصلاة خير من النوم.",
            "صباح الخير يا $name، أذان الفجر بعد $minutes دقائق. ركعتا الفجر خير من الدنيا وما فيها."
        ) else pick(
            "يا $name، باقي $minutes دقائق على أذان $pn. توضأ واستعد للصلاة.",
            "تذكير يا $name: أذان $pn بعد $minutes دقائق. خلّنا نجهز للصلاة.",
            "يا $name، قرّب أذان $pn، باقي $minutes دقائق."
        )
    }

    fun adhanIntro(name: String, p: Prayer, t: Long): String =
        "يا $name، حان الآن موعد أذان ${p.displayName(t)}."

    fun iqama(name: String, p: Prayer, t: Long): String {
        val pn = p.displayName(t)
        return pick(
            "يا $name، حان وقت إقامة صلاة $pn. قم إلى الصلاة.",
            "يا $name، أقيمت صلاة $pn. حي على الصلاة.",
            "يا $name، الإقامة الآن لصلاة $pn، لا تفوتك الجماعة."
        )
    }
}
