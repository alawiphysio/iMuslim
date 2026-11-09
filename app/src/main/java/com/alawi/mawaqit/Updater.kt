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
        if (!force && System.currentTimeMillis() - last < 6 * 3_600_000L) return
        Thread {
            val msg = try { check(app) } catch (e: Exception) { "ما قدرت أتحقق من التحديث (تأكد من الإنترنت)" }
            sp(app).edit().putLong("last_check", System.currentTimeMillis()).apply()
            if (onResult != null) android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(msg) }
        }.start()
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
        if (f.exists() && f.length() > 1_000_000) { notifyReady(app, latest); return "التحديث جاهز للتثبيت" }
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
        if (ok) notifyReady(ctx, ver)
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

    /** التثبيت عن طريق مثبّت أندرويد (يطلب منك تأكيد بضغطة) */
    fun install(a: Activity, ver: Long): Boolean {
        val f = apkFile(a, ver) ?: return false
        if (!f.exists()) return false
        if (!a.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(a, "اسمح لـ i.Muslim بتثبيت التحديثات، وارجع", Toast.LENGTH_LONG).show()
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return false
        }
        val pkg = a.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(a.packageName)
        val sid = pkg.createSession(params)
        pkg.openSession(sid).use { session ->
            session.openWrite("iMuslim.apk", 0, f.length()).use { out ->
                f.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val pi = PendingIntent.getBroadcast(a, 702, Intent(a, InstallResultReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pi.intentSender)
        }
        return true
    }
}

/** نتيجة التثبيت: نعرض نافذة التأكيد حق أندرويد */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { ctx.startActivity(confirm) } catch (e: Exception) { }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Toast.makeText(ctx, "ما تم التحديث: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""}", Toast.LENGTH_LONG).show()
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

/** شاشة التحديث */
class UpdateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val ver = Updater.readyVersion(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xFF071814.toInt())
            setPadding((28 * d).toInt(), 0, (28 * d).toInt(), 0)
        }
        fun tv(s: String, size: Float, color: Int) = TextView(this).apply { text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER }
        col.addView(tv("🎁", 64f, C_TEXT))
        if (ver > 0) {
            col.addView(tv("تحديث جديد جاهز", 26f, C_GOLD))
            col.addView(tv("من ${Updater.currentName(this)} إلى 1.0.$ver", 15f, C_TEXT))
            val b = tv("ثبّت التحديث الحين", 18f, 0xFF0B231E.toInt()).apply {
                setPadding(0, (14 * d).toInt(), 0, (14 * d).toInt())
                background = android.graphics.drawable.GradientDrawable().apply { setColor(C_GOLD); cornerRadius = 24 * d }
                setOnClickListener { if (!Updater.install(this@UpdateActivity, ver)) Unit }
            }
            col.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (24 * d).toInt() })
            col.addView(tv("إعداداتك وصلواتك وختمتك تبقى كما هي", 12f, 0xFFA9BDB4.toInt()).apply { setPadding(0, (12 * d).toInt(), 0, 0) })
        } else {
            col.addView(tv("عندك آخر إصدار ✅", 22f, C_GOLD))
            col.addView(tv(Updater.currentName(this), 14f, C_TEXT))
        }
        setContentView(col)
    }
}
