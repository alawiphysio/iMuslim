package com.alawi.mawaqit

import android.app.DownloadManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.json.JSONObject
import java.io.File

// =================== التفسير الميسر ===================
/** التفسير الميسر — مجمع الملك فهد لطباعة المصحف الشريف */
object Tafsir {
    @Volatile private var data: List<List<String>>? = null
    var source = ""
        private set

    @Synchronized
    private fun load(ctx: Context): List<List<String>> {
        data?.let { return it }
        val root = JSONObject(ctx.assets.open("tafsir_muyassar.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        source = root.optString("source")
        val t = root.getJSONArray("t")
        val out = List(t.length()) { i -> val a = t.getJSONArray(i); List(a.length()) { a.getString(it) } }
        data = out
        return out
    }

    fun of(ctx: Context, s: Int, a: Int): String =
        load(ctx).getOrNull(s - 1)?.getOrNull(a - 1)?.trim() ?: ""
}

// =================== الختمة ===================
object Khatma {
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("khatma", Context.MODE_PRIVATE)
    const val PAGES = 604

    fun active(ctx: Context) = sp(ctx).getInt("days", 0) > 0
    fun days(ctx: Context) = sp(ctx).getInt("days", 0)
    fun done(ctx: Context) = sp(ctx).getInt("done", 0)          // عدد الأوراد المكتملة
    fun lastDoneDay(ctx: Context) = sp(ctx).getString("last_done", "") ?: ""

    fun start(ctx: Context, days: Int) {
        sp(ctx).edit().putInt("days", days).putInt("done", 0).putString("last_done", "")
            .putString("start", Tracker.dayKey(System.currentTimeMillis())).apply()
    }

    fun stop(ctx: Context) = sp(ctx).edit().clear().apply()

    fun pagesPerDay(ctx: Context) = (PAGES + days(ctx) - 1) / days(ctx).coerceAtLeast(1)

    /** صفحات الورد القادم (من، إلى) */
    fun nextRange(ctx: Context): Pair<Int, Int> {
        val ppd = pagesPerDay(ctx)
        val from = done(ctx) * ppd + 1
        return Pair(from.coerceAtMost(PAGES), (from + ppd - 1).coerceAtMost(PAGES))
    }

    fun finished(ctx: Context) = active(ctx) && done(ctx) * pagesPerDay(ctx) >= PAGES
    fun doneToday(ctx: Context) = lastDoneDay(ctx) == Tracker.dayKey(System.currentTimeMillis())

    fun markDone(ctx: Context) {
        sp(ctx).edit().putInt("done", done(ctx) + 1)
            .putString("last_done", Tracker.dayKey(System.currentTimeMillis())).apply()
    }

    fun percent(ctx: Context): Int = if (!active(ctx)) 0 else ((done(ctx) * pagesPerDay(ctx)).coerceAtMost(PAGES) * 100 / PAGES)

    /** وصف الورد: من سورة كذا آية كذا إلى … */
    fun describe(ctx: Context): String {
        val (p1, p2) = nextRange(ctx)
        val (s1, a1) = Quran.pageStart(ctx, p1)
        val (s2, a2) = Quran.pageEnd(ctx, p2)
        val n1 = Quran.surah(ctx, s1).name
        val n2 = Quran.surah(ctx, s2).name
        return "الصفحات ${Fmt.num(p1)}–${Fmt.num(p2)}: من سورة $n1 آية ${Fmt.num(a1)} إلى سورة $n2 آية ${Fmt.num(a2)}"
    }
}

// =================== تحميل التلاوة ===================
object QuranDownloads {
    private const val DIR = "quran_audio"
    private const val URL = "https://server11.mp3quran.net/a_jbr/"
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences("qdl", Context.MODE_PRIVATE)
    private fun pad(n: Int) = n.toString().padStart(3, '0')

    fun file(ctx: Context, s: Int): File? = ctx.getExternalFilesDir(DIR)?.let { File(it, "${pad(s)}.mp3") }

    /** محمّلة بالكامل؟ */
    fun isReady(ctx: Context, s: Int): Boolean {
        val f = file(ctx, s) ?: return false
        if (!f.exists() || f.length() < 10_000) return false
        val id = sp(ctx).getLong("id_$s", -1L)
        if (id < 0) return true
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return false
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (c.moveToFirst()) {
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (st == DownloadManager.STATUS_SUCCESSFUL) { sp(ctx).edit().remove("id_$s").apply(); return true }
                return false
            }
        }
        sp(ctx).edit().remove("id_$s").apply()
        return true
    }

    fun isDownloading(ctx: Context, s: Int) = sp(ctx).getLong("id_$s", -1L) >= 0 && !isReady(ctx, s)

    fun download(ctx: Context, surahs: Iterable<Int>): Int {
        val dm = ctx.getSystemService(DownloadManager::class.java) ?: return 0
        var n = 0
        for (s in surahs) {
            if (isReady(ctx, s) || sp(ctx).getLong("id_$s", -1L) >= 0) continue
            file(ctx, s)?.delete()
            val name = Quran.surah(ctx, s).name
            val req = DownloadManager.Request(Uri.parse(URL + pad(s) + ".mp3"))
                .setTitle("سورة $name • ${QuranPlayer.RECITER}")
                .setDescription("i.Muslim — تحميل التلاوة")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(ctx, DIR, "${pad(s)}.mp3")
                .setAllowedOverMetered(true)
            sp(ctx).edit().putLong("id_$s", dm.enqueue(req)).apply()
            n++
        }
        return n
    }

    fun readyCount(ctx: Context) = (1..114).count { isReady(ctx, it) }

    fun sizeMb(ctx: Context): Int = ((ctx.getExternalFilesDir(DIR)?.listFiles()?.sumOf { it.length() } ?: 0L) / 1_048_576L).toInt()

    fun deleteAll(ctx: Context) {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        for (s in 1..114) {
            val id = sp(ctx).getLong("id_$s", -1L)
            if (id >= 0) try { dm?.remove(id) } catch (e: Exception) { }
        }
        sp(ctx).edit().clear().apply()
        ctx.getExternalFilesDir(DIR)?.listFiles()?.forEach { it.delete() }
    }
}

// =================== آية اليوم ===================
object AyahOfDay {
    /** آية تتغير كل يوم (من الآيات المتوسطة الطول عشان تكون مكتملة المعنى غالباً) */
    fun pick(ctx: Context, now: Long = System.currentTimeMillis()): Pair<Int, Int> {
        val all = Quran.load(ctx)
        val cands = ArrayList<Pair<Int, Int>>()
        for (s in all) for (i in s.verses.indices) {
            val len = Quran.verseText(s, i).length
            if (len in 45..170) cands.add(Pair(s.n, i + 1))
        }
        val day = (now + 3 * 3_600_000L) / 86_400_000L   // بتوقيت مكة تقريباً
        val idx = ((day * 2654435761L) % cands.size).toInt().let { if (it < 0) it + cands.size else it }
        return cands[idx]
    }
}

/** ويدجت آية اليوم */
class AyahWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = updateAll(ctx)

    companion object {
        fun updateAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, AyahWidget::class.java))
            if (ids == null || ids.isEmpty()) return
            val (s, a) = AyahOfDay.pick(ctx)
            val su = Quran.surah(ctx, s)
            val rv = RemoteViews(ctx.packageName, R.layout.widget_ayah)
            rv.setTextViewText(R.id.a_text, "﴿${Quran.verseText(su, a - 1)}﴾")
            rv.setTextViewText(R.id.a_ref, "سورة ${su.name} • آية ${Fmt.num(a)}")
            rv.setOnClickPendingIntent(R.id.a_root, PendingIntent.getActivity(ctx, 600,
                Intent(ctx, QuranReaderActivity::class.java).putExtra("s", s).putExtra("a", a),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            mgr.updateAppWidget(ids, rv)
        }
    }
}
