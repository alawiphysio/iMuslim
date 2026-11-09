package com.alawi.mawaqit

import android.app.Activity
import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * التحديث التلقائي:
 * التطبيق يفحص آخر إصدار على GitHub (مرة كل ٦ ساعات تقريباً)، ولو فيه إصدار أحدث
 * يحمّله في الخلفية، ويطلع لك إشعار «ثبّت التحديث» تضغطه وتوافق، وخلاص.
 */
object Updater {
    private const val API = "https://api.github.com/repos/alawiphysio/iMuslim/releases/latest"
    private const val CH = "updates_v1"
    private const val NID = 700
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("updater", Context.MODE_PRIVATE)

    fun currentVersion(ctx: Context): Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (e: Exception) { 0L }

    fun currentName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) { "" }

    fun apkFile(ctx: Context, v: Long): File? = ctx.getExternalFilesDir("update")?.let { File(it, "iMuslim-$v.apk") }

    /** فحص في الخلفية (مع حد أدنى بين كل فحص) */
    fun checkInBackground(ctx: Context, force: Boolean = false, onResult: ((String) -> Unit)? = null) {
        val app = ctx.applicationContext
        val last = sp(app).getLong("last_check", 0L)
        if (!force && System.currentTimeMillis() - last < 3_600_000L) return
        Thread {
            val msg = try { check(app) } catch (e: Exception) { "ما قدرت أتحقق من التحديث (تأكد من الإنترنت)" }
            sp(app).edit().putLong("last_check", System.currentTimeMillis()).apply()
            if (onResult != null) android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(msg) }
        }.start()
    }

    /** آخر إصدار على GitHub: (رقم، رابط) أو null */
    fun fetchLatest(): Pair<Long, String>? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "iMuslim-app")
        if (c.responseCode != 200) { c.disconnect(); return null }
        val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() }); c.disconnect()
        val v = o.optString("tag_name").substringAfterLast('.').toLongOrNull() ?: return null
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) return v to a.optString("browser_download_url")
        }
        return null
    }

    /** يبدأ التحميل (أو يرجع رقم التحميل الشغال) */
    fun startDownload(app: Context, ver: Long, url: String): Long {
        val dm = app.getSystemService(DownloadManager::class.java)
        val s = sp(app)
        if (s.getLong("dl_ver", -1L) == ver) {
            val id = s.getLong("dl_id", -1L)
            if (dmStatus(app, id)?.first in setOf(DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_SUCCESSFUL)) return id
            try { dm.remove(id) } catch (e: Exception) { }
        }
        apkFile(app, ver)?.parentFile?.listFiles()?.forEach { it.delete() }
        val id = dm.enqueue(DownloadManager.Request(Uri.parse(url))
            .setTitle("تحديث i.Muslim").setDescription("الإصدار 1.0.$ver")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(app, "update", "iMuslim-$ver.apk"))
        s.edit().putLong("dl_id", id).putLong("dl_ver", ver).apply()
        return id
    }

    /** (الحالة، المحمّل، الحجم) */
    fun dmStatus(app: Context, id: Long): Triple<Int, Long, Long>? {
        val dm = app.getSystemService(DownloadManager::class.java) ?: return null
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) return Triple(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)))
        }
        return null
    }

    private fun check(app: Context): String {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "iMuslim-app")
        if (c.responseCode != 200) { c.disconnect(); return "ما قدرت أتحقق من التحديث" }
        val body = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val o = JSONObject(body)
        // tag مثل v1.0.12 → رقم الإصدار 12
        val latest = o.optString("tag_name").substringAfterLast('.').toLongOrNull() ?: return "ما فيه تحديث"
        if (latest <= currentVersion(app)) return "عندك آخر إصدار ✅ (${currentName(app)})"
        val assets = o.optJSONArray("assets")
        var url: String? = null
        for (i in 0 until (assets?.length() ?: 0)) {
            val a = assets!!.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) url = a.optString("browser_download_url")
        }
        if (url == null) return "ما فيه تحديث"
        val f = apkFile(app, latest) ?: return "ما فيه مساحة للتحديث"
        if (f.exists() && f.length() > 1_000_000) { autoInstall(app, latest); return "التحديث جاهز، جاري التثبيت…" }
        val dm = app.getSystemService(DownloadManager::class.java) ?: return "ما قدرت أحمّل التحديث"
        if (sp(app).getLong("dl_ver", -1L) == latest) {
            // نتأكد إن التحميل السابق فعلاً شغال (ما علّق أو فشل)
            val oldId = sp(app).getLong("dl_id", -1L)
            var st = -1
            dm.query(DownloadManager.Query().setFilterById(oldId))?.use { c ->
                if (c.moveToFirst()) st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
            when (st) {
                DownloadManager.STATUS_SUCCESSFUL -> { onDownloaded(app, oldId); return "التحديث جاهز للتثبيت" }
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> return "التحديث يتحمّل الحين…"
                else -> dm.remove(oldId)   // فشل أو انمسح: نعيد التحميل
            }
        }
        f.parentFile?.listFiles()?.forEach { it.delete() }
        val id = dm.enqueue(DownloadManager.Request(Uri.parse(url))
            .setTitle("تحديث i.Muslim")
            .setDescription("الإصدار 1.0.$latest")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(app, "update", "iMuslim-$latest.apk"))
        sp(app).edit().putLong("dl_id", id).putLong("dl_ver", latest).apply()
        return "فيه تحديث جديد (1.0.$latest)، جاري التحميل…"
    }

    /** لما يخلص التحميل */
    fun onDownloaded(ctx: Context, id: Long) {
        val s = sp(ctx)
        if (s.getLong("dl_id", -1L) != id) return
        val ver = s.getLong("dl_ver", -1L)
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return
        var ok = false
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) ok = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
        s.edit().remove("dl_id").remove("dl_ver").apply()
        if (ok) autoInstall(ctx, ver)
    }

    fun readyVersion(ctx: Context): Long {
        val dir = ctx.getExternalFilesDir("update") ?: return -1
        val v = dir.listFiles()?.mapNotNull { it.name.removePrefix("iMuslim-").removeSuffix(".apk").toLongOrNull() }?.maxOrNull() ?: return -1
        return if (v > currentVersion(ctx)) v else -1
    }

    private fun notifyReady(ctx: Context, ver: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH, "تحديثات التطبيق", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(ctx, 701, Intent(ctx, UpdateActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("🎁 تحديث جديد لـ i.Muslim")
            .setContentText("الإصدار 1.0.$ver جاهز • اضغط للتثبيت")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setColor(C_GOLD)
            .build()
        try { nm.notify(NID, n) } catch (e: SecurityException) { }
    }

    /** تثبيت تلقائي بدون ما تسوي شي (أندرويد 12+ بعد أول تحديث من داخل التطبيق).
     *  لو أندرويد طلب تأكيد، يطلع لك إشعار «ثبّت التحديث». */
    fun autoInstall(ctx: Context, ver: Long) {
        if (!ctx.packageManager.canRequestPackageInstalls()) { notifyReady(ctx, ver); return }
        try { if (!commit(ctx, ver, true)) notifyReady(ctx, ver) } catch (e: Exception) { notifyReady(ctx, ver) }
    }

    /** بعد ما يتحدث التطبيق لحاله: إشعار لطيف */
    fun notifyUpdated(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CH, "تحديثات التطبيق", NotificationManager.IMPORTANCE_DEFAULT))
        nm.cancel(NID)
        val pi = PendingIntent.getActivity(ctx, 704, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, CH).setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("✨ تم تحديث i.Muslim").setContentText("الإصدار ${currentName(ctx)} — اضغط تشوف الجديد")
            .setContentIntent(pi).setAutoCancel(true).setColor(C_GOLD).build()
        try { nm.notify(NID + 1, n) } catch (e: SecurityException) { }
    }

    /** مرة وحدة: نطلب إذن «تثبيت التحديثات» عشان التحديث يصير تلقائي */
    fun askAutoPermissionOnce(a: Activity) {
        if (a.packageManager.canRequestPackageInstalls()) return
        val s = sp(a); if (s.getBoolean("asked_perm", false)) return
        s.edit().putBoolean("asked_perm", true).apply()
        android.app.AlertDialog.Builder(a)
            .setTitle("🔄 التحديث التلقائي")
            .setMessage("عشان i.Muslim يتحدث لحاله بدون ما تسوي شي، اسمح له بتثبيت التحديثات (مرة وحدة بس).")
            .setPositiveButton("اسمح") { _, _ ->
                try { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))) } catch (e: Exception) { }
            }
            .setNegativeButton("بعدين", null).show()
    }

    fun notifyReadyPublic(ctx: Context, ver: Long) = notifyReady(ctx, ver)

    @Volatile var lastSid = -1
    private fun commit(ctx: Context, ver: Long, auto: Boolean): Boolean {
        val f = apkFile(ctx, ver) ?: return false
        if (!f.exists()) return false
        val pkg = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        // نلغي أي جلسة تثبيت قديمة معلّقة (كانت تسبب «انلغى التثبيت»)
        for (old in pkg.mySessions) try { pkg.abandonSession(old.sessionId) } catch (e: Exception) { }
        val sid = pkg.createSession(params)
        lastSid = sid
        pkg.openSession(sid).use { session ->
            session.openWrite("iMuslim.apk", 0, f.length()).use { out ->
                f.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            // من شاشة التحديث: النتيجة ترجع للشاشة نفسها (عشان أندرويد يسمح بفتح نافذة التأكيد)
            val pi = if (!auto && ctx is UpdateActivity) PendingIntent.getActivity(ctx, 702,
                Intent(ctx, UpdateActivity::class.java).setAction(UpdateActivity.ACTION_RESULT).putExtra("ver", ver).putExtra("sid", sid)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            else PendingIntent.getBroadcast(ctx, if (auto) 703 else 702,
                Intent(ctx, InstallResultReceiver::class.java).putExtra("auto", auto).putExtra("ver", ver).putExtra("sid", sid),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pi.intentSender)
        }
        return true
    }

    /** التثبيت من شاشة التحديث (بضغطة) */
    fun install(a: Activity, ver: Long): Boolean {
        val f = apkFile(a, ver) ?: return false
        if (!f.exists()) return false
        if (!a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "اسمح لـ i.Muslim بتثبيت التحديثات، وارجع", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return false
        }
        return commit(a, ver, false)
    }
}

/** نتيجة التثبيت: نعرض نافذة التأكيد حق أندرويد */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // في التثبيت التلقائي ما نفتح نافذة فجأة: نطلع إشعار بدلها
                if (intent.getBooleanExtra("auto", false)) {
                    Updater.notifyReadyPublic(ctx, intent.getLongExtra("ver", -1L)); return
                }
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { ctx.startActivity(confirm) } catch (e: Exception) { }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> if (!intent.getBooleanExtra("auto", false)) Toast.makeText(ctx, "ما تم التحديث: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""}", Toast.LENGTH_LONG).show()
        }
    }
}

/** لما يخلص تحميل التحديث */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            Updater.onDownloaded(ctx, intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L))
    }
}

/** شاشة التحديث: تتحقق، تحمّل مع نسبة، وتفتح التثبيت مباشرة */
class UpdateActivity : Activity() {
    companion object { const val ACTION_RESULT = "com.alawi.mawaqit.INSTALL_RESULT" }

    /** نتيجة التثبيت ترجع هنا */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != ACTION_RESULT) return
        // نتيجة جلسة قديمة؟ نتجاهلها
        if (intent.getIntExtra("sid", -1) != Updater.lastSid) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) try { startActivity(confirm) } catch (e: Exception) {
                    done("ما انفتحت نافذة التثبيت", e.message ?: "", "حاول مرة ثانية") { ready() }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> { title.text = "تم التحديث ✅"; sub.text = "" }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // سكّرت نافذة التأكيد: نرجّع زر التثبيت بهدوء
                installing = false
                title.text = "التحديث جاهز 🎁"; sub.text = "اضغط «ثبّت» ثم «تحديث» في النافذة"
                btn.visibility = android.view.View.VISIBLE; btn.text = "ثبّت التحديث الحين"
                btn.setOnClickListener { doInstall() }
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Diag.log(this, "فشل تثبيت التحديث: $status $msg")
                done("ما تم التثبيت", if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "انلغى التثبيت" else msg.take(120), "حاول مرة ثانية") { ready() }
            }
        }
    }

    private val h = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var title: TextView
    private lateinit var sub: TextView
    private lateinit var bar: android.widget.ProgressBar
    private lateinit var btn: TextView
    private var ver = -1L
    private var dlId = -1L
    private var closed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xFF071814.toInt())
            setPadding((28 * d).toInt(), 0, (28 * d).toInt(), 0)
        }
        fun tv(s: String, size: Float, color: Int) = TextView(this).apply { text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER }
        col.addView(tv("🔄", 60f, C_TEXT))
        title = tv("جاري التحقق من التحديث…", 22f, C_GOLD); col.addView(title)
        sub = tv("الإصدار الحالي: ${Updater.currentName(this)}", 14f, C_TEXT).apply { setPadding(0, (8 * d).toInt(), 0, 0) }
        col.addView(sub)
        bar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; isIndeterminate = true
            progressTintList = android.content.res.ColorStateList.valueOf(C_GOLD)
        }
        col.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (10 * d).toInt()).also { it.topMargin = (20 * d).toInt() })
        btn = tv("", 18f, 0xFF0B231E.toInt()).apply {
            setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt()); visibility = android.view.View.GONE
            background = android.graphics.drawable.GradientDrawable().apply { setColor(C_GOLD); cornerRadius = 24 * d }
        }
        col.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (24 * d).toInt() })
        col.addView(tv("إعداداتك وصلواتك وختمتك تبقى كما هي", 12f, 0xFFA9BDB4.toInt()).apply { setPadding(0, (12 * d).toInt(), 0, 0) })
        col.addView(tv("→ رجوع", 15f, 0xFFA9BDB4.toInt()).apply {
            setPadding(0, (24 * d).toInt(), 0, (8 * d).toInt()); setOnClickListener { finish() }
        })
        setContentView(col)
        check()
    }

    private fun check() {
        Thread {
            val r = try { Updater.fetchLatest() } catch (e: Exception) { null }
            h.post {
                if (closed) return@post
                if (r == null) { done("ما قدرت أتحقق", "تأكد من الإنترنت وجرب مرة ثانية", "إعادة المحاولة") { check() }; return@post }
                val (v, url) = r
                if (v <= Updater.currentVersion(this)) { done("عندك آخر إصدار ✅", "الإصدار ${Updater.currentName(this)}", null, null); return@post }
                ver = v
                val f = Updater.apkFile(this, v)
                if (f != null && f.exists() && f.length() > 1_000_000 && Updater.dmStatus(this, getSharedPreferences("updater", 0).getLong("dl_id", -1))?.first != DownloadManager.STATUS_RUNNING) { ready(); return@post }
                title.text = "جاري تحميل الإصدار 1.0.$v…"
                dlId = try { Updater.startDownload(applicationContext, v, url) } catch (e: Exception) { -1 }
                if (dlId < 0) { done("ما قدرت أحمّل", "جرب مرة ثانية", "إعادة المحاولة") { check() }; return@post }
                poll()
            }
        }.start()
    }

    private fun poll() {
        if (closed) return
        val st = Updater.dmStatus(this, dlId)
        when (st?.first) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                getSharedPreferences("updater", 0).edit().remove("dl_id").remove("dl_ver").apply()
                ready(); return
            }
            DownloadManager.STATUS_FAILED, null -> { done("فشل التحميل", "تأكد من الإنترنت وجرب مرة ثانية", "إعادة المحاولة") { check() }; return }
            else -> {
                val (_, got, total) = st
                if (total > 0) { bar.isIndeterminate = false; bar.progress = (got * 100 / total).toInt()
                    sub.text = "${got / 1_048_576} من ${total / 1_048_576} ميجا" }
            }
        }
        h.postDelayed({ poll() }, 400)
    }

    private fun ready() {
        bar.isIndeterminate = false; bar.progress = 100
        title.text = "التحديث جاهز 🎁"
        sub.text = "من ${Updater.currentName(this)} إلى 1.0.$ver"
        btn.text = "ثبّت التحديث الحين"; btn.visibility = android.view.View.VISIBLE
        btn.setOnClickListener { doInstall() }
        if (!autoTried) { autoTried = true; doInstall() }   // يفتح نافذة التثبيت مباشرة (مرة وحدة)
    }

    private var installing = false
    private var autoTried = false
    private fun doInstall() {
        if (installing) return
        installing = true
        sub.text = "جاري فتح نافذة التثبيت…"
        h.post {
            val ok = try { Updater.install(this, ver) } catch (e: Exception) { false }
            if (!ok) installing = false else h.postDelayed({ installing = false }, 4000)
        }
    }

    private fun done(t: String, s: String, b: String?, act: (() -> Unit)?) {
        bar.isIndeterminate = false; bar.progress = if (b == null) 100 else 0
        title.text = t; sub.text = s
        if (b != null && act != null) { btn.text = b; btn.visibility = android.view.View.VISIBLE; btn.setOnClickListener { btn.visibility = android.view.View.GONE; title.text = "جاري التحقق…"; bar.isIndeterminate = true; act() } }
        else btn.visibility = android.view.View.GONE
    }

    override fun onDestroy() { closed = true; h.removeCallbacksAndMessages(null); super.onDestroy() }
}
