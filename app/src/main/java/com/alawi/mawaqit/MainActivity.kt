package com.alawi.mawaqit

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private val GOLD = 0xFFD9B44A.toInt()
    private val TEXT = 0xFFF4EFE1.toInt()
    private val MUTED = 0xFFA9BDB4.toInt()
    private val CARD = 0xFF143A31.toInt()
    private val BG = 0xFF0B231E.toInt()

    private lateinit var cityTv: TextView
    private lateinit var dateTv: TextView
    private lateinit var nextNameTv: TextView
    private lateinit var nextTimeTv: TextView
    private lateinit var countdownTv: TextView
    private lateinit var soundTv: TextView
    private lateinit var fajrSoundTv: TextView
    private lateinit var nextLabelTv: TextView
    private lateinit var figureIv: ImageView
    private var splashShown = false
    private lateinit var landmarkImg: ImageView
    private lateinit var landmarkFrame: KenBurnsFrame
    private lateinit var figureFrame: KenBurnsFrame
    private lateinit var betweenBtn: Button
    private val rowMark = HashMap<Prayer, TextView>()
    private lateinit var statsTv: TextView
    private lateinit var weekDots: LinearLayout
    private lateinit var extraTimesTv: TextView
    private lateinit var ramadanCard: LinearLayout
    private lateinit var ramadanTv: TextView
    private lateinit var travelCard: LinearLayout
    private lateinit var travelTv: TextView
    private lateinit var seasonCard: LinearLayout
    private lateinit var timeline: TimelineView
    private val chipBox = arrayOfNulls<LinearLayout>(3)
    private val chipTitle = arrayOfNulls<TextView>(3)
    private val chipTime = arrayOfNulls<TextView>(3)
    private lateinit var historyCard: LinearLayout
    private var shownSeason: Season? = null
    private lateinit var contentRoot: LinearLayout
    private val muezzinCards = HashMap<String, LinearLayout>()
    private var lastIqamaPhase: Boolean? = null
    private lateinit var ringCaptionTv: TextView
    private lateinit var minutesTv: TextView
    private lateinit var moonTv: TextView
    private lateinit var moonInfoTv: TextView
    private lateinit var minutesBigTv: TextView
    private lateinit var minutesUnitTv: TextView
    private lateinit var landmarkTv: TextView
    private lateinit var landmarkView: LandmarkView
    private lateinit var ringView: RingView
    private lateinit var moonView: MoonView
    private val rowIcon = HashMap<Prayer, PrayerIconView>()
    private var pulseAnim: ObjectAnimator? = null
    private var pulsedPrayer: Prayer? = null
    private var ringIntroDone = false
    private val rowName = HashMap<Prayer, TextView>()
    private val rowTime = HashMap<Prayer, TextView>()
    private val rowIqama = HashMap<Prayer, TextView>()
    private val rowBox = HashMap<Prayer, LinearLayout>()
    private var lastNextT = 0L

    private var shownLook: Look.Mode? = null
    private var updateBanner: TextView? = null
    private fun showUpdateBanner() {
        val b = updateBanner ?: return
        val v = Updater.readyVersion(this)
        val hidden = getSharedPreferences("updater", 0).getLong("banner_hidden", -1L) == v
        if (v > 0 && !hidden) {
            b.text = "🎁 الإصدار 1.0.$v جاهز • اضغط للتثبيت   ✕"
            b.visibility = View.VISIBLE
        } else b.visibility = View.GONE
    }
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        Notifier.ensureChannels(this)
        setContentView(buildUi())
        window.decorView.postDelayed({ if (!isFinishing) Sadaqa.askNameOnce(this) }, 3500)
        window.decorView.postDelayed({ if (!isFinishing) Updater.askAutoPermissionOnce(this) }, 9000)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        } else if (prefs.firstRun) {
            fetchLocation()
        }
        checkExactAlarm()
        reschedule()
    }

    override fun onResume() {
        super.onResume()
        minutesBigTv.animate().cancel(); minutesBigTv.alpha = 1f; minutesBigTv.translationY = 0f
        refresh()
        // حارس: نتأكد إن منبه الصلاة الجاي مسجّل دائماً
        try { Scheduler.scheduleNext(this) } catch (e: Exception) { }
        Updater.checkInBackground(this)
        // تحديث جاهز؟ شريط صغير هادئ فوق (بدون ما يقاطعك)
        showUpdateBanner()
        handler.post(ticker)
        silentLocationCheck()
    }

    /** كل ما تفتح التطبيق: لو انتقلت لمدينة ثانية تتحدث الأوقات تلقائياً */
    @SuppressLint("MissingPermission")
    private fun silentLocationCheck() {
        if (!AutoLocation.hasPermission(this)) return
        val lm = getSystemService(LocationManager::class.java) ?: return
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { try { lm.isProviderEnabled(it) } catch (e: Exception) { false } } ?: return
        val handle = { loc: Location? ->
            val changed = if (loc != null) AutoLocation.apply(this, loc) else AutoLocation.updateFromLastKnown(this)
            if (changed) {
                Travel.check(this)
                refresh(); reschedule()
                handler.postDelayed({ refresh(); toast("تم تحديث الأوقات حسب موقعك: ${prefs.city}") }, 2500)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { lm.getCurrentLocation(provider, null, mainExecutor) { loc -> handle(loc) } } catch (e: Exception) { handle(null) }
        } else {
            handle(null)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    // ---------------- منطق ----------------

    private fun reschedule() {
        Scheduler.scheduleNext(this)
        PrayerWidget.updateAll(this)
        LiveNotif.update(this)
        AyahWidget.updateAll(this)
    }

    private fun refresh() {
        // الوضع التلقائي: لما يجي الشروق أو المغرب نبدّل الألوان
        val lk = Look.effective(this)
        if (shownLook != null && shownLook != lk) { shownLook = lk; recreate(); return }
        shownLook = lk
        val now = System.currentTimeMillis()
        val today = Times.forDay(this, 0, now)
        val (next, nextT) = Times.next(this, now)
        val st = Times.state(this, now)
        lastNextT = st.target
        cityTv.text = "📍 ${prefs.city}"
        dateTv.text = "${Hijri.format(now)}  •  ${Fmt.date(now)}"
        val stName = st.prayer.displayName(st.adhanT)
        if (lastIqamaPhase != st.iqamaPhase) {
            lastIqamaPhase = st.iqamaPhase
            val figRes = Pictures.figure(this, st.iqamaPhase)
            figureIv.setImageResource(figRes)
            // الرسمة المرسومة بالكود تنعرض كاملة، وصور Canva تملأ الإطار
            figureIv.scaleType = if (figRes == R.drawable.fig_walker || figRes == R.drawable.fig_praying)
                ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            figureFrame.showTitle(if (st.iqamaPhase) "استعدّ للإقامة" else "حيّ على الصلاة")
            figureFrame.alpha = 0f; figureFrame.scaleX = 0.85f; figureFrame.scaleY = 0.85f
            figureFrame.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
        betweenBtn.text = if (st.iqamaPhase) "🤲 الدعاء بين الأذان والإقامة لا يُرد" else "📿 أذكار الأذان"
        // شريط الوقت والخانات
        val after = Times.next(this, st.adhanT + 60_000L)
        if (st.iqamaPhase) {
            timeline.startLabel = "الأذان"; timeline.startTime = Fmt.time(st.adhanT)
            timeline.endLabel = "الإقامة"; timeline.endTime = Fmt.time(st.iqamaT)
        } else {
            val (pp, pt) = Times.prevPair(this, now)
            timeline.startLabel = pp.displayName(pt); timeline.startTime = Fmt.time(pt)
            timeline.endLabel = "أذان $stName"; timeline.endTime = Fmt.time(st.adhanT)
        }
        timeline.progress = st.progress
        val chipData = listOf(
            Triple("الأذان", Fmt.time(st.adhanT), !st.iqamaPhase),
            Triple("الإقامة", Fmt.time(st.iqamaT), st.iqamaPhase),
            Triple("الصلاة بعدها", "${after.first.displayName(after.second)} ${Fmt.time(after.second).replace(" ص", "").replace(" م", "")}", false)
        )
        chipData.forEachIndexed { i, (a, b, on) ->
            chipTitle[i]?.text = a; chipTime[i]?.text = b
            chipTime[i]?.setTextColor(if (on) GOLD else TEXT)
            chipBox[i]?.background = rounded(if (on) 0x2ED9B44A else 0x0DFFFFFF, 14f, if (on) GOLD else 0x40D9B44A)
        }
        if (st.iqamaPhase) {
            nextLabelTv.text = "حان وقت الصلاة • استعد للإقامة"
            nextNameTv.text = "صلاة $stName"
            nextTimeTv.text = "أذّن ${Fmt.time(st.adhanT)}  •  الإقامة ${Fmt.time(st.iqamaT)}"
            ringCaptionTv.text = "باقي على الإقامة"
        } else {
            nextLabelTv.text = "الصلاة القادمة"
            nextNameTv.text = stName
            nextTimeTv.text = "الأذان ${Fmt.time(st.adhanT)}  •  الإقامة ${Fmt.time(st.iqamaT)}"
            ringCaptionTv.text = "باقي على الأذان"
        }
        val mp = Moon.phase(now)
        moonTv.text = "${mp.emoji}  ${mp.name}"
        moonView.frac = Moon.frac(now)
        val lm = Landmarks.detect(prefs.lat, prefs.lng)
        landmarkView.type = lm
        val lmImg = Pictures.landmark(this, lm)
        if (lmImg != 0) {
            landmarkImg.setImageResource(lmImg)
            landmarkFrame.visibility = View.VISIBLE
            landmarkFrame.showTitle(lm.title)
            landmarkView.visibility = View.GONE
            landmarkTv.visibility = View.GONE
        } else {
            landmarkFrame.visibility = View.GONE
            landmarkView.visibility = View.VISIBLE
            landmarkTv.text = lm.title
            landmarkTv.visibility = if (lm.title.isEmpty()) View.GONE else View.VISIBLE
        }
        ringView.setProgress(st.progress, !ringIntroDone)
        ringIntroDone = true
        moonInfoTv.text = "الإضاءة ${Fmt.num(mp.illumination)}٪  •  عمر القمر ${Fmt.num(mp.ageDays.toInt() + 1)} يوم"
        for (p in Prayer.values()) {
            val t = today[p.ordinal]
            rowName[p]?.text = p.displayName(t)
            rowTime[p]?.text = Fmt.time(t)
            rowIqama[p]?.text = if (p.hasAdhan && prefs.iqamaOn)
                "الإقامة ${Fmt.time(t + prefs.iqamaMin(p) * 60_000L)}" else ""
            val isNext = p == st.prayer && t == st.adhanT
            rowBox[p]?.background = if (isNext) rounded(0x33D9B44A, 14f, 0x66D9B44A) else rounded(Color.TRANSPARENT, 14f)
            rowName[p]?.setTextColor(if (isNext) GOLD else TEXT)
            rowTime[p]?.setTextColor(if (isNext) GOLD else TEXT)
            rowIcon[p]?.active = isNext
            if (isNext && pulsedPrayer != p) { pulsedPrayer = p; pulse(rowIcon[p]) }
        }
        // المواسم: الجمعة، رمضان، العيدين، عشر ذي الحجة
        val season = Season.current(now)
        if (season != shownSeason) { shownSeason = season; buildSeasonCard(season, now) }
        // في مثل هذا اليوم
        val hist = IslamicHistory.on(Hijri.date(now))
        historyCard.removeAllViews()
        if (hist.isNotEmpty()) {
            historyCard.visibility = View.VISIBLE
            historyCard.addView(header("⭐ في مثل هذا اليوم"))
            hist.forEach { historyCard.addView(tv("• $it", 14f, TEXT).also { t -> t.setLineSpacing(0f, 1.25f); t.setPadding(0, dp(4), 0, 0) }) }
        } else historyCard.visibility = View.GONE
        // متابعة الصلوات
        val todayKey = Tracker.dayKey(now)
        for (p in Prayer.FIVE) {
            rowMark[p]?.text = when (Tracker.get(this, todayKey, p)) { 1 -> "✓"; 2 -> "👥"; else -> if (today[p.ordinal] <= now) "○" else "" }
        }
        refreshStats(now)
        // الضحى والثلث الأخير
        val (dS, dE) = Night.duha(this, now)
        extraTimesTv.text = "☀️ الضحى: ${Fmt.time(dS)} – ${Fmt.time(dE)}\n🌌 الثلث الأخير من الليل: ${Fmt.time(Night.lastThird(this, 0, now))}"
        // رمضان
        if (Hijri.isRamadan(now)) {
            ramadanCard.visibility = View.VISIBLE
            val fajr = today[Prayer.FAJR.ordinal]; val maghrib = today[Prayer.MAGHRIB.ordinal]
            ramadanTv.text = if (now < maghrib && now >= fajr - 10 * 60_000L)
                "🌅 الإفطار ${Fmt.time(maghrib)} • باقي ${Fmt.minutesLeft(maghrib - now)}\nالإمساك كان ${Fmt.time(fajr - 10 * 60_000L)}"
            else {
                val f2 = if (now < fajr) fajr else Times.forDay(this, 1, now)[Prayer.FAJR.ordinal]
                "🍽 الإمساك ${Fmt.time(f2 - 10 * 60_000L)} • الفجر ${Fmt.time(f2)}\nباقي على الفجر ${Fmt.minutesLeft(f2 - now)}"
            }
        } else ramadanCard.visibility = View.GONE
        // السفر
        if (Travel.isTraveling(this)) {
            travelCard.visibility = View.VISIBLE
            travelTv.text = "تبعد ${Fmt.num(Travel.distanceKm(prefs).toInt())} كم عن ${prefs.homeCity}. يجوز لك قصر الصلاة الرباعية ركعتين، والجمع بين الظهر والعصر، والمغرب والعشاء. (المسافة المعتبرة عند جمهور العلماء نحو ٨٠ كم)"
        } else travelCard.visibility = View.GONE
        soundTv.text = "الصوت الحالي: " + (prefs.adhanName ?: "صوت المنبه الافتراضي (اختر ملف أذان)")
        for ((id, card) in muezzinCards) {
            val sel = id == prefs.muezzinId
            card.background = rounded(if (sel) 0x40D9B44A else 0x1AFFFFFF, 16f, if (sel) GOLD else 0x22FFFFFF)
        }
        fajrSoundTv.text = "الصوت الحالي: " + (prefs.fajrName ?: "نفس الأذان العادي (اختر أذان الفجر)")
        tick()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (lastNextT != 0L && now >= lastNextT) { refresh(); return }
        countdownTv.text = Fmt.countdown(lastNextT - now)
        minutesTv.text = Fmt.minutesLeft(lastNextT - now)
        val mins = ((lastNextT - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
        val newText = Fmt.num(mins)
        if (minutesBigTv.text.toString() != newText) {
            minutesBigTv.animate().cancel()
            if (minutesBigTv.text.isNullOrEmpty() || !minutesBigTv.isShown) {
                minutesBigTv.text = newText; minutesBigTv.alpha = 1f; minutesBigTv.translationY = 0f
            } else {
                // الرقم القديم يطلع لفوق ويختفي، والجديد ينزل مكانه
                minutesBigTv.animate().translationY(-dp(18).toFloat()).alpha(0f).setDuration(180).withEndAction {
                    minutesBigTv.text = newText
                    minutesBigTv.translationY = dp(18).toFloat()
                    minutesBigTv.animate().translationY(0f).alpha(1f).setDuration(260)
                        .setInterpolator(DecelerateInterpolator(1.6f))
                        .withEndAction { minutesBigTv.alpha = 1f; minutesBigTv.translationY = 0f }.start()
                }.start()
            }
        }
        ringView.urgent = mins <= 10
        minutesUnitTv.text = when { mins == 1 -> "دقيقة"; mins == 2 -> "دقيقتين"; mins in 3..10 -> "دقائق"; else -> "دقيقة" }
        if (now / 1000 % 30 == 0L) {
            val p = Times.state(this, now).progress
            ringView.setProgress(p, false)
            timeline.progress = p
        }
    }

    private fun checkExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) {
                toast("فعّل صلاحية المنبهات الدقيقة عشان الأذان يجي على وقته")
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                } catch (e: Exception) { }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOC
            )
            return
        }
        prefs.firstRun = false
        val lm = getSystemService(LocationManager::class.java) ?: return
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { try { lm.isProviderEnabled(it) } catch (e: Exception) { false } }
        if (providers.isEmpty()) {
            toast("شغّل خدمة الموقع (GPS) من الجوال وجرب مرة ثانية")
            return
        }
        toast("جاري تحديد موقعك…")
        val fallback = {
            val last = providers.mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
                .maxByOrNull { it.time }
            if (last != null) onLocation(last) else toast("ما قدرت أحدد الموقع، جرب برّا المبنى")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                lm.getCurrentLocation(providers[0], null, mainExecutor) { loc ->
                    if (loc != null) onLocation(loc) else fallback()
                }
            } catch (e: Exception) { fallback() }
        } else {
            fallback()
        }
    }

    private fun onLocation(loc: Location) {
        prefs.lat = loc.latitude
        prefs.lng = loc.longitude
        Thread {
            val name = try {
                @Suppress("DEPRECATION")
                val a = Geocoder(this, Locale("ar")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                a?.locality ?: a?.subAdminArea ?: a?.adminArea
            } catch (e: Exception) { null }
            runOnUiThread {
                prefs.city = name ?: String.format(Locale.US, "%.3f، %.3f", loc.latitude, loc.longitude)
                Travel.check(this)
                refresh()
                reschedule()
                toast("تم تحديث الموقع: ${prefs.city}")
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_NOTIF -> if (prefs.firstRun) fetchLocation()
            REQ_AUDIO_PERM -> if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) showDeviceAudio(pendingAudioReq)
            else { toast("بدون الإذن نفتح لك الملفات"); openPicker(pendingAudioReq) }
            REQ_LOC -> if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) fetchLocation()
            else { prefs.firstRun = false; toast("بنستخدم أوقات مكة المكرمة") }
        }
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == REQ_EXPORT || requestCode == REQ_IMPORT) && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            if (requestCode == REQ_EXPORT) toast(if (Backup.export(this, uri)) "تم حفظ النسخة الاحتياطية ✅" else "ما قدرت أحفظ النسخة")
            else {
                if (Backup.import(this, uri)) { toast("تم استرجاع إعداداتك ✅"); recreate() } else toast("الملف غير صالح")
            }
            return
        }
        if ((requestCode == REQ_SOUND || requestCode == REQ_SOUND_FAJR) && resultCode == RESULT_OK) {
            val src = data?.data ?: return
            var name: String? = null
            try {
                contentResolver.query(src, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) name = it.getString(0)
                }
            } catch (e: Exception) { }
            setSound(requestCode, src, name)
        }
    }

    /** ننسخ الصوت داخل التطبيق: يشتغل حتى بدون نت (حتى لو كان من Drive) وما يضيع */
    private fun setSound(requestCode: Int, src: Uri, name: String?) {
        run {
            val f = java.io.File(filesDir, if (requestCode == REQ_SOUND_FAJR) "sound_fajr" else "sound_adhan")
            val ok = try {
                contentResolver.openInputStream(src)?.use { inp -> f.outputStream().use { inp.copyTo(it) } } != null && f.length() > 1000
            } catch (e: Exception) { false }
            if (!ok) { toast("ما قدرت أقرأ الملف، جرّب ملف ثاني"); return }
            val uri = Uri.fromFile(f)
            prefs.muezzinId = null
            if (requestCode == REQ_SOUND_FAJR) {
                prefs.fajrUri = uri.toString()
                prefs.fajrName = name ?: "أذان الفجر"
                toast("تم اختيار أذان الفجر ✅")
            } else {
                prefs.adhanUri = uri.toString()
                prefs.adhanName = name ?: "ملف أذان"
                toast("تم اختيار صوت الأذان ✅")
            }
            refresh()
        }
    }

    /** قائمة الأصوات اللي في جوالك، داخل التطبيق (بدون ما تطلع لتطبيق ثاني) */
    private fun showDeviceAudio(req: Int) {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            pendingAudioReq = req
            requestPermissions(arrayOf(perm), REQ_AUDIO_PERM); return
        }
        val items = ArrayList<Pair<String, Uri>>()
        try {
            contentResolver.query(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.Audio.Media._ID, android.provider.MediaStore.Audio.Media.TITLE, android.provider.MediaStore.Audio.Media.DURATION),
                "${android.provider.MediaStore.Audio.Media.DURATION} > 20000", null,
                "${android.provider.MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
                while (c.moveToNext() && items.size < 300) {
                    val id = c.getLong(0); val dur = c.getLong(2) / 1000
                    items.add("${c.getString(1)}  (${dur / 60}:${"%02d".format(dur % 60)})" to
                        android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                }
            }
        } catch (e: Exception) { }
        if (items.isEmpty()) { toast("ما لقيت ملفات صوت في جوالك. نزّل ملف الأذان أول، أو اختر من الملفات"); return }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (req == REQ_SOUND_FAJR) "اختر أذان الفجر" else "اختر صوت الأذان")
            .setItems(items.map { it.first }.toTypedArray()) { _, w -> setSound(req, items[w].second, items[w].first.substringBefore("  (")) }
            .setNegativeButton("إلغاء", null).show()
    }

    private var pendingAudioReq = 0

    // ---------------- الواجهة ----------------

    private fun buildUi(): View {
        val frame = FrameLayout(this)
        val sky = SkyView(this)
        frame.addView(sky, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.setOnScrollChangeListener { _, _, y, _, _ -> sky.parallax = y * 0.25f }
        frame.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }
        scroll.addView(root)
        contentRoot = root
        applyFoldPadding()

        // رأس الصفحة: العنوان + المدينة + التاريخ
        root.addView(tv("i.Muslim", 32f, GOLD, true).also {
            it.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
            it.gravity = Gravity.CENTER
            it.setShadowLayer(dp(10).toFloat(), 0f, 0f, 0x88D9B44A.toInt())
        })
        root.addView(tv("الصلاة يا مسلم  •  v${Updater.currentName(this)}", 13f, MUTED).also { it.gravity = Gravity.CENTER })
        updateBanner = tv("", 13f, 0xFF0B231E.toInt(), true).apply {
            gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(GOLD, 18f)
            setOnTouchListener { v, e ->
                if (e.action == android.view.MotionEvent.ACTION_UP) {
                    // ✕ على الطرف الأيسر = إخفاء لهذا الإصدار، غير كذا = تثبيت
                    if (e.x < v.width * 0.18f) {
                        getSharedPreferences("updater", 0).edit().putLong("banner_hidden", Updater.readyVersion(this@MainActivity)).apply()
                        v.visibility = View.GONE
                    } else startActivity(Intent(this@MainActivity, UpdateActivity::class.java))
                }
                true
            }
        }
        root.addView(updateBanner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(6) })
        cityTv = tv("", 15f, TEXT).also { it.gravity = Gravity.CENTER }
        dateTv = tv("", 13f, MUTED).also { it.gravity = Gravity.CENTER }
        root.addView(cityTv)
        root.addView(dateTv)

        // رسمة أشهر مسجد في مدينتك
        landmarkView = LandmarkView(this)
        root.addView(landmarkView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(190)).also { it.topMargin = dp(10) })
        landmarkFrame = KenBurnsFrame(this, 22f).apply { visibility = View.GONE }
        landmarkImg = landmarkFrame.image
        root.addView(landmarkFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).also { it.topMargin = dp(10) })
        landmarkTv = tv("", 14f, GOLD, true).also { it.gravity = Gravity.CENTER }
        seasonCard = card().apply { visibility = View.GONE }
        root.addView(landmarkTv)
        root.addView(seasonCard)

        // بطاقة الصلاة القادمة: حلقة ذهبية والعدّاد داخلها
        val nextCard = card()
        nextCard.gravity = Gravity.CENTER_HORIZONTAL
        figureFrame = KenBurnsFrame(this, 26f)
        figureIv = figureFrame.image
        nextCard.addView(figureFrame, LinearLayout.LayoutParams(dp(280), dp(280)).also { it.bottomMargin = dp(12); it.gravity = Gravity.CENTER_HORIZONTAL })
        nextLabelTv = tv("الصلاة القادمة", 14f, MUTED).also { it.gravity = Gravity.CENTER }
        nextCard.addView(nextLabelTv)
        nextNameTv = tv("", 32f, GOLD, true).also { it.gravity = Gravity.CENTER }
        nextCard.addView(nextNameTv)

        val ringBox = FrameLayout(this)
        ringView = RingView(this)
        ringBox.addView(ringView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        minutesBigTv = tv("", 62f, 0xFFFFFFFF.toInt(), true).also {
            it.gravity = Gravity.CENTER
            it.setShadowLayer(dp(12).toFloat(), 0f, 0f, 0x66F6DE8D)
        }
        minutesUnitTv = tv("دقيقة", 15f, GOLD).also { it.gravity = Gravity.CENTER }
        countdownTv = tv("00:00:00", 17f, MUTED, true).also {
            it.gravity = Gravity.CENTER
            it.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            it.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        ringCaptionTv = tv("للأذان", 13f, MUTED).also { it.gravity = Gravity.CENTER }
        inner.addView(ringCaptionTv)
        inner.addView(minutesBigTv); inner.addView(minutesUnitTv); inner.addView(countdownTv)
        ringBox.addView(inner, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        nextCard.addView(ringBox, LinearLayout.LayoutParams(dp(250), dp(250)).also {
            it.gravity = Gravity.CENTER_HORIZONTAL; it.topMargin = dp(6)
        })

        minutesTv = tv("", 17f, GOLD, true).also { it.gravity = Gravity.CENTER; it.visibility = View.GONE }
        nextTimeTv = tv("", 15f, TEXT).also { it.gravity = Gravity.CENTER; it.visibility = View.GONE }
        nextCard.addView(minutesTv)
        nextCard.addView(nextTimeTv)
        // شريط الوقت
        timeline = TimelineView(this)
        nextCard.addView(timeline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).also { it.topMargin = dp(8) })
        // ثلاث خانات: الأذان • الإقامة • الصلاة بعدها
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0..2) {
            val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(9), dp(4), dp(9)) }
            val t1 = tv("", 11f, MUTED).also { it.gravity = Gravity.CENTER }
            val t2 = tv("", 16f, TEXT, true).also { it.gravity = Gravity.CENTER }
            b.addView(t1); b.addView(t2)
            chipBox[i] = b; chipTitle[i] = t1; chipTime[i] = t2
            chips.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { if (i < 2) it.marginEnd = dp(8) })
        }
        nextCard.addView(chips, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(6) })
        betweenBtn = button("📿 أذكار الأذان والدعاء قبل الإقامة") { Adhkar.open(this, Adhkar.ADHAN) }
        nextCard.addView(betweenBtn)
        root.addView(nextCard)

        // رمضان (يظهر في رمضان بس)
        ramadanCard = card().apply { visibility = View.GONE }
        ramadanCard.addView(header("🌙 رمضان كريم"))
        ramadanTv = tv("", 16f, TEXT, true)
        ramadanCard.addView(ramadanTv)
        ramadanCard.addView(button("🤲 دعاء الإفطار") { Adhkar.open(this, 68) })
        root.addView(ramadanCard)

        // السفر (يظهر لما تكون مسافر)
        travelCard = card().apply { visibility = View.GONE }
        travelCard.addView(header("✈️ أنت مسافر"))
        travelTv = tv("", 13f, TEXT)
        travelCard.addView(travelTv)
        travelCard.addView(button("📍 هذي مدينتي الحين", outline = true) { Travel.setHomeHere(this); refresh(); toast("تم حفظ ${prefs.city} كمدينتك") })
        root.addView(travelCard)

        // اختصارات
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun tile(icon: String, label: String, onClick: () -> Unit): View = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(14), dp(6), dp(12))
            background = rounded(0xCC0F2E27.toInt(), 20f, 0x40D9B44A)
            addView(tv(icon, 28f, TEXT).also { it.gravity = Gravity.CENTER })
            addView(tv(label, 13f, GOLD, true).also { it.gravity = Gravity.CENTER })
            setOnClickListener { onClick() }
            Press.apply(this)
        }
        val tiles = listOf(
            tile("📖", "المصحف") { startActivity(Intent(this, QuranActivity::class.java)) },
            tile("🕋", "القبلة") { startActivity(Intent(this, QiblaActivity::class.java)) },
            tile("🏰", "الأذكار") { startActivity(Intent(this, AdhkarActivity::class.java)) },
            tile("📅", "التقويم") { startActivity(Intent(this, HijriCalendarActivity::class.java)) },
            tile("📿", "المسبحة") { startActivity(Intent(this, TasbihActivity::class.java)) },
            tile("🎧", "استمع") {
                val s = QuranPrefs.listenSurah(this).takeIf { it > 0 } ?: 18
                QuranPlayer.play(this, s, 1, if (QuranPrefs.listenSurah(this) == s) QuranPrefs.listenMs(this) else -1)
                startActivity(Intent(this, QuranReaderActivity::class.java).putExtra("s", s).putExtra("follow", true))
            }
        )
        val quick2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tiles.forEachIndexed { i, t ->
            val target = if (i < 3) quick else quick2
            target.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { if (i % 3 != 2) it.marginEnd = dp(8) })
        }
        root.addView(quick, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(14) })
        root.addView(quick2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8) })

        // آية اليوم
        val (aS, aA) = AyahOfDay.pick(this)
        val aSu = Quran.surah(this, aS)
        val ayahCard = card()
        ayahCard.addView(header("📖 آية اليوم"))
        ayahCard.addView(tv("﴿${Quran.verseText(aSu, aA - 1)}﴾", 30f, TEXT).also {
            it.typeface = resources.getFont(R.font.amiri_quran)
            it.gravity = Gravity.CENTER
            it.setLineSpacing(0f, 1.4f)
        })
        ayahCard.addView(tv("سورة ${aSu.name} • آية ${Fmt.num(aA)}", 12f, MUTED).also { it.gravity = Gravity.CENTER })
        // ▶ استماع للآية بصوت الشيخ عبدالله علي جابر
        lateinit var listenBtn: TextView
        val syncListen = object : Runnable {
            override fun run() {
                val on = QuranPlayer.isSingle(aS, aA)
                listenBtn.text = if (on) "⏹ إيقاف التلاوة" else "▶ استمع للآية بصوت ${QuranPlayer.RECITER}"
                if (on) handler.postDelayed(this, 400)
            }
        }
        listenBtn = button("▶ استمع للآية بصوت ${QuranPlayer.RECITER}") {
            if (QuranPlayer.isSingle(aS, aA)) QuranPlayer.stop(this)
            else { QuranPlayer.playAyah(this, aS, aA); listenBtn.text = "⏳ جاري التحميل…" }
            handler.removeCallbacks(syncListen); handler.postDelayed(syncListen, 300)
        }
        ayahCard.addView(listenBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.topMargin = dp(8); it.bottomMargin = dp(8) })
        val aRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        aRow.addView(button("📘 التفسير") {
            val t = Tafsir.of(this, aS, aA).replace("[التفسير]", "\n📘 التفسير:")
            android.app.AlertDialog.Builder(this).setTitle("سورة ${aSu.name} • آية ${Fmt.num(aA)}")
                .setMessage("$t\n\n— التفسير الميسر، مجمع الملك فهد لطباعة المصحف الشريف")
                .setPositiveButton("تم", null).show()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        aRow.addView(button("📖 في المصحف", outline = true) { Quran.open(this, aS, aA) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ayahCard.addView(aRow)
        root.addView(ayahCard)

        // في مثل هذا اليوم
        historyCard = card().apply { visibility = View.GONE }
        root.addView(historyCard)

        // حصن المسلم
        val hisn = card()
        hisn.addView(header("🏰 حصن المسلم"))
        hisn.addView(tv("الأذكار كاملة مع عدّاد، وتسمعها بصوت حمد الدريهم", 13f, MUTED))
        val hRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hRow1.addView(button("☀️ الصباح والمساء") { Adhkar.open(this, Adhkar.MORNING_EVENING) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        hRow1.addView(button("🤲 بعد الصلاة") { Adhkar.open(this, Adhkar.AFTER_PRAYER) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hisn.addView(hRow1)
        val hRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hRow2.addView(button("🌙 النوم", outline = true) { Adhkar.open(this, Adhkar.SLEEP) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        hRow2.addView(button("📖 كل الأذكار", outline = true) { startActivity(Intent(this, AdhkarActivity::class.java)) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hisn.addView(hRow2)
        hisn.addView(switch("ذكّرني بأذكار الصباح والمساء", prefs.adhkarRemind) { prefs.adhkarRemind = it; reschedule() })
        hisn.addView(switch("ذكّرني بالأذكار بعد كل صلاة", prefs.afterPrayerRemind) { prefs.afterPrayerRemind = it; reschedule() })
        root.addView(hisn)

        // بطاقة منازل القمر: رسمة القمر الحقيقية + المعلومات
        val moonCard = card()
        moonCard.addView(header("منازل القمر"))
        val moonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        moonView = MoonView(this)
        moonRow.addView(moonView, LinearLayout.LayoutParams(dp(120), dp(120)))
        val moonText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(12), 0) }
        moonTv = tv("", 22f, TEXT, true)
        moonInfoTv = tv("", 13f, MUTED)
        moonText.addView(moonTv); moonText.addView(moonInfoTv)
        moonRow.addView(moonText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        moonCard.addView(moonRow)
        root.addView(moonCard)

        // بطاقة أوقات اليوم مع أيقونة لكل صلاة
        val timesCard = card()
        timesCard.addView(header("أوقات اليوم"))
        for (p in Prayer.values()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val icon = PrayerIconView(this, p)
            row.addView(icon, LinearLayout.LayoutParams(dp(30), dp(30)).also { it.marginEnd = dp(12) })
            val name = tv(p.ar, 18f, TEXT, true)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
            val time = tv("", 18f, TEXT, true).also { it.gravity = Gravity.END }
            val iq = tv("", 12f, MUTED).also { it.gravity = Gravity.END }
            col.addView(time); col.addView(iq)
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(col)
            if (p.hasAdhan) {
                val mark = tv("", 18f, GOLD, true).also { it.gravity = Gravity.CENTER }
                row.addView(mark, LinearLayout.LayoutParams(dp(34), dp(34)).also { it.marginStart = dp(8) })
                rowMark[p] = mark
                row.setOnClickListener {
                    val v = Tracker.cycle(this, Tracker.dayKey(System.currentTimeMillis()), p)
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    toast(when (v) { 1 -> "✓ صليت ${p.ar}"; 2 -> "👥 صليت ${p.ar} في جماعة"; else -> "تم إلغاء التأشير" })
                    refresh()
                }
            }
            rowName[p] = name; rowTime[p] = time; rowIqama[p] = iq; rowBox[p] = row; rowIcon[p] = icon
            timesCard.addView(row)
        }
        extraTimesTv = tv("", 13f, MUTED).also { it.setPadding(dp(12), dp(10), dp(12), 0) }
        timesCard.addView(extraTimesTv)
        timesCard.addView(tv("اضغط على الصلاة بعد ما تصليها: مرة = صليت ✓، مرتين = جماعة 👥", 11f, MUTED).also { it.setPadding(dp(12), dp(6), dp(12), 0) })
        root.addView(timesCard)

        // إحصائيات صلواتي
        val stats = card()
        stats.addView(header("📊 صلواتي"))
        statsTv = tv("", 14f, TEXT)
        stats.addView(statsTv)
        weekDots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        stats.addView(weekDots)
        stats.addView(tv("🟢 صليت  🟡 جماعة  ⚪ ما تأشّرت", 11f, MUTED).also { it.setPadding(0, dp(8), 0, 0) })
        root.addView(stats)

        // المظهر
        val lookCard = card()
        lookCard.addView(header("🎨 المظهر"))
        lookCard.addView(tv("تلقائي: نهاري من الشروق للمغرب، وليلي بعده", 12f, MUTED))
        val lookRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val cur = Look.mode(this)
        for (m in Look.Mode.values()) {
            val sel = m == cur
            lookRow.addView(tv("${m.emoji}\n${m.title}", 13f, if (sel) 0xFF0B231E.toInt() else TEXT, true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10))
                background = if (sel) rounded(GOLD, 16f) else rounded(0x22FFFFFF, 16f, 0x44D9B44A)
                setOnClickListener { Look.setMode(this@MainActivity, m); recreate() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(6) })
        }
        lookCard.addView(lookRow)
        root.addView(lookCard)

        // التحديثات
        val up = card()
        up.addView(header("🔄 تحديث البرنامج • الإصدار ${Updater.currentName(this)}"))
        up.addView(tv("الإصدار الحالي: ${Updater.currentName(this)} • التطبيق يتحقق كل ساعة، ويحمّل التحديث ويثبّته لحاله.", 13f, MUTED))
        up.addView(button("🔄 تحديث البرنامج") {
            startActivity(Intent(this, UpdateActivity::class.java))
        })
        root.addView(up)

        // بطاقة التنبيهات
        val alerts = card()
        alerts.addView(header("الأذان لكل صلاة"))
        for (p in Prayer.FIVE) {
            alerts.addView(switch("أذان ${p.ar}", prefs.adhanOn(p)) { prefs.setAdhanOn(p, it); reschedule() })
        }
        alerts.addView(divider())
        alerts.addView(switch("تنبيه قبل الأذان", prefs.preOn) { prefs.preOn = it; reschedule() })
        alerts.addView(numberField("كم دقيقة قبل الأذان؟", prefs.preMin, false) { prefs.preMin = it.coerceIn(0, 120); reschedule() })
        alerts.addView(divider())
        alerts.addView(switch("تنبيه وقت الإقامة", prefs.iqamaOn) { prefs.iqamaOn = it; reschedule(); refresh() })
        for (p in Prayer.FIVE) {
            alerts.addView(numberField("الإقامة بعد أذان ${p.ar} (دقيقة)", prefs.iqamaMin(p), false) {
                prefs.setIqamaMin(p, it.coerceIn(0, 120)); reschedule(); refresh()
            })
        }
        root.addView(alerts)

        // الصوت: أذان الفجر + أذان باقي الصلوات
        val sound = card()
        sound.addView(header("المؤذن"))
        sound.addView(tv("اختر الشيخ اللي تحب تسمع أذانه:", 13f, MUTED))
        val mzScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val mzRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(8)) }
        for (m in Muezzins.ALL) {
            val ok = Muezzins.isAvailable(this, m)
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(14), dp(12), dp(14), dp(12))
                alpha = if (ok) 1f else 0.55f
                setOnClickListener {
                    if (Muezzins.select(this@MainActivity, m)) {
                        refresh()
                        toast("تم اختيار ${m.name} ✅")
                        AdhanService.start(this@MainActivity, Prayer.DHUHR, System.currentTimeMillis(), true)
                    } else {
                        toast("أذان ${m.name} غير مضاف للتطبيق بعد. اختر ملف من جوالك تحت.")
                    }
                }
            }
            c.addView(tv("🕌", 26f, TEXT).also { it.gravity = Gravity.CENTER })
            c.addView(tv(m.name, 13f, TEXT, true).also { it.gravity = Gravity.CENTER })
            c.addView(tv(if (ok) m.place else "${m.place} • غير متوفر", 11f, MUTED).also { it.gravity = Gravity.CENTER })
            muezzinCards[m.id] = c
            mzRow.addView(c, LinearLayout.LayoutParams(dp(130), ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(8) })
        }
        mzScroll.addView(mzRow)
        sound.addView(mzScroll)
        sound.addView(divider())
        sound.addView(header("أو ملفات من جوالك"))
        sound.addView(tv("أذان الظهر والعصر والمغرب والعشاء", 15f, TEXT, true))
        soundTv = tv("", 13f, MUTED)
        sound.addView(soundTv)
        sound.addView(button("🎵 اختيار ملف الأذان (MP3)") { pickSound(REQ_SOUND) })
        val testRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        testRow.addView(button("▶ تجربة") {
            AdhanService.start(this, Prayer.DHUHR, System.currentTimeMillis(), true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        testRow.addView(button("■ إيقاف", outline = true) { AdhanService.stop(this) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sound.addView(testRow)

        sound.addView(divider())
        sound.addView(tv("أذان الفجر 🌅", 15f, TEXT, true))
        sound.addView(tv("أذان الفجر مختلف (فيه «الصلاة خير من النوم»)، اختر له ملف خاص.", 12f, MUTED))
        fajrSoundTv = tv("", 13f, MUTED)
        sound.addView(fajrSoundTv)
        sound.addView(button("🎵 اختيار ملف أذان الفجر (MP3)") { pickSound(REQ_SOUND_FAJR) })
        val fajrRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fajrRow.addView(button("▶ تجربة الفجر") {
            AdhanService.start(this, Prayer.FAJR, System.currentTimeMillis(), true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        fajrRow.addView(button("■ إيقاف", outline = true) { AdhanService.stop(this) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sound.addView(fajrRow)
        sound.addView(tv("الأذان يطلع على صوت المنبه، تأكد إن صوت المنبه مو مقفّل.", 12f, MUTED))
        root.addView(sound)

        // الموقع وتعديل الأوقات
        val loc = card()
        loc.addView(header("الموقع وطريقة الحساب"))
        loc.addView(tv("طريقة الحساب: تقويم أم القرى (الفجر 18.5°، العشاء بعد المغرب بساعة ونص، وساعتين في رمضان)", 13f, MUTED))
        loc.addView(tv("الموقع يتحدث تلقائياً: لو سافرت لمدينة ثانية تتغير الأوقات على حسب المنطقة.", 13f, MUTED))
        loc.addView(numberField("تعديل التاريخ الهجري (± يوم) لو اختلفت الرؤية", prefs.hijriAdjust, true) {
            prefs.hijriAdjust = it.coerceIn(-2, 2); shownSeason = null; refresh(); reschedule()
        })
        loc.addView(button("📍 تحديث موقعي") { fetchLocation() })
        loc.addView(button("🕋 استخدام مكة المكرمة", outline = true) {
            prefs.lat = 21.4225; prefs.lng = 39.8262; prefs.city = "مكة المكرمة"
            refresh(); reschedule(); toast("تم")
        })
        loc.addView(divider())
        loc.addView(tv("تعديل يدوي بالدقائق (+ أو -) لو تبغى تطابق مسجدك:", 13f, MUTED))
        for (p in Prayer.values()) {
            loc.addView(numberField(p.ar, prefs.adjust(p), true) {
                prefs.setAdjust(p, it.coerceIn(-30, 30)); refresh(); reschedule()
            })
        }
        root.addView(loc)

        // وضع المسجد
        val mosque = card()
        mosque.addView(header("🕌 وضع المسجد"))
        mosque.addView(tv("الجوال يصير على الاهتزاز تلقائياً وقت الإقامة، ويرجع عادي بعد الصلاة. يوم الجمعة يبدأ من الأذان لمدة ساعة (الخطبة).", 13f, MUTED))
        mosque.addView(switch("تفعيل وضع المسجد", prefs.mosqueOn) { prefs.mosqueOn = it; reschedule() })
        mosque.addView(numberField("المدة (دقيقة)", prefs.mosqueMin, false) { prefs.mosqueMin = it.coerceIn(5, 90); reschedule() })
        mosque.addView(switch("صامت تام بدل الاهتزاز", prefs.mosqueSilent) { prefs.mosqueSilent = it })
        mosque.addView(tv("الصامت التام يحتاج إذن «عدم الإزعاج» من الوضع القوي تحت. ولما يكون وضع المسجد شغال، تنبيه الإقامة ما يتكلم بصوت.", 11f, MUTED))
        root.addView(mosque)

        // المناسبات والتذكيرات
        val occ = card()
        occ.addView(header("📅 المناسبات والتذكيرات"))
        occ.addView(switch("📌 عدّاد الصلاة في شريط الإشعارات", prefs.liveOn) { prefs.liveOn = it; LiveNotif.update(this) })
        occ.addView(switch("📖 الجمعة: الكهف وساعة الاستجابة", prefs.fridayOn) { prefs.fridayOn = it; reschedule() })
        occ.addView(switch("🌙 تذكير الصيام (الاثنين والخميس، الأيام البيض، عرفة، عاشوراء)", prefs.fastingOn) { prefs.fastingOn = it; reschedule() })
        occ.addView(switch("🍽 رمضان: السحور والإفطار", prefs.ramadanOn) { prefs.ramadanOn = it; reschedule() })
        occ.addView(numberField("تذكير السحور قبل الفجر بـ (دقيقة)", prefs.suhoorMin, false) { prefs.suhoorMin = it.coerceIn(10, 180); reschedule() })
        occ.addView(switch("🌌 قيام الليل (الثلث الأخير)", prefs.qiyamOn) { prefs.qiyamOn = it; reschedule() })
        root.addView(occ)


        // النسخ الاحتياطي والساعة
        val bk = card()
        bk.addView(header("☁️ النسخ الاحتياطي"))
        bk.addView(tv("إعداداتك تنحفظ تلقائياً في نسخة Google الاحتياطية للجوال. وتقدر كمان تحفظ ملف بنفسك (في Google Drive مثلاً) وتسترجعه لو غيّرت جوالك.", 13f, MUTED))
        val bRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bRow.addView(button("💾 حفظ نسخة") { backupPick(REQ_EXPORT) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        bRow.addView(button("📥 استرجاع", outline = true) { backupPick(REQ_IMPORT) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bk.addView(bRow)
        bk.addView(divider())
        bk.addView(tv("⌚ ساعة سامسونج: تنبيهات الأذان والإقامة والتذكيرات توصل لساعتك تلقائياً وتهتز فيها، بشرط إن تطبيق Galaxy Wearable مسموح له بإشعارات «الصلاة يا مسلم».", 13f, MUTED))
        root.addView(bk)

        // الوضع القوي: صامت + عدم إزعاج
        val strong = card()
        strong.addView(header("💪 الوضع القوي"))
        strong.addView(switch("الأذان يشتغل حتى لو الجوال صامت", prefs.adhanInSilent) { prefs.adhanInSilent = it })
        strong.addView(switch("الأذان يشتغل في وضع عدم الإزعاج", prefs.adhanInDnd) { prefs.adhanInDnd = it })
        strong.addView(switch("شاشة الأذان الملونة الكاملة", prefs.fullScreenOn) { prefs.fullScreenOn = it })
        strong.addView(switch("وميض الفلاش واهتزاز لما الجوال صامت", prefs.flashOn) { prefs.flashOn = it })
        strong.addView(button("🔕 السماح بتجاوز «عدم الإزعاج»") {
            try { startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (e: Exception) { }
        })
        if (Build.VERSION.SDK_INT >= 34) {
            strong.addView(button("📱 السماح بشاشة الأذان الكاملة", outline = true) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
                } catch (e: Exception) { }
            })
        }
        strong.addView(button("👁 معاينة شاشة الأذان", outline = true) {
            val (np, nt) = Times.next(this)
            startActivity(Intent(this, AdhanActivity::class.java)
                .putExtra(Scheduler.EXTRA_PRAYER, np.key).putExtra(Scheduler.EXTRA_TIME, nt))
        })
        strong.addView(tv("في سامسونج: فعّل كمان «إضاءة الحواف» من الإعدادات ← الشاشة، عشان تنوّر أطراف الشاشة وقت الأذان.", 12f, MUTED))
        root.addView(strong)

        // التذكير الصوتي
        val voice = card()
        voice.addView(header("🗣 التطبيق يكلّمك"))
        voice.addView(tv("يذكّرك بصوت عربي قبل الأذان، ويقول لك وقت الأذان، وينبهك وقت الإقامة.", 13f, MUTED))
        voice.addView(switch("التذكير الصوتي", prefs.voiceOn) { prefs.voiceOn = it })
        voice.addView(textField("اسمك (التطبيق يناديك فيه)", prefs.userName) { if (it.isNotBlank()) prefs.userName = it.trim() })
        val vRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        vRow.addView(button("▶ اسمع التذكير") {
            val (np, nt) = Times.next(this)
            AdhanService.say(this, np, nt, "تجربة التذكير الصوتي", Voice.before(prefs.userName, np, nt, prefs.preMin))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        vRow.addView(button("تحميل الصوت العربي", outline = true) {
            try {
                startActivity(Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
            } catch (e: Exception) {
                try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } catch (e2: Exception) { }
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        voice.addView(vRow)
        voice.addView(tv("لو ما سمعت صوت عربي: الإعدادات ← الإدارة العامة ← تحويل النص إلى كلام، واختر محرك Google ولغة العربية.", 12f, MUTED))
        root.addView(voice)

        // فحص الأذان
        val diag = card()
        diag.addView(header("🔧 فحص الأذان"))
        val diagBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        diag.addView(diagBody)
        fun fillDiag() {
            diagBody.removeAllViews()
            val iss = Diag.issues(this)
            if (iss.isEmpty()) diagBody.addView(tv("✅ كل الأذونات تمام", 14f, 0xFF7FE0A9.toInt()))
            for (i in iss) {
                diagBody.addView(tv("⚠️ ${i.text}", 14f, 0xFFFFB27A.toInt()))
                diagBody.addView(button("أصلحها", outline = true) { i.fix(this) })
            }
            diagBody.addView(tv(Diag.nextAlarmText(this), 13f, C_TEXT).also { it.setPadding(0, dp(8), 0, dp(4)) })
            Diag.recent(this, 6).forEach { diagBody.addView(tv(it, 11f, MUTED)) }
        }
        fillDiag()
        diag.addView(button("🔔 جرّب الأذان الحقيقي بعد دقيقة") {
            Diag.scheduleTest(this); toast("قفل الشاشة وانتظر دقيقة… لازم يطلع الأذان")
            handler.postDelayed({ fillDiag() }, 500)
        })
        diag.addView(button("تحديث الفحص", outline = true) { fillDiag() })
        root.addView(diag)

        // البطارية
        val bat = card()
        bat.addView(header("مهم لجوالات سامسونج"))
        bat.addView(tv("عشان ما يوقف النظام الأذان، استثنِ التطبيق من توفير البطارية (اختر: غير مقيّد).", 13f, MUTED))
        bat.addView(button("🔋 استثناء من توفير البطارية") {
            try {
                @SuppressLint("BatteryLife")
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                startActivity(i)
            } catch (e: Exception) {
                try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e2: Exception) { }
            }
        })
        bat.addView(button("⚙ إعدادات التطبيق", outline = true) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        root.addView(bat)

        root.addView(tv("i.Muslim • الإصدار ${Updater.currentName(this)}", 13f, GOLD, true).also {
            it.gravity = Gravity.CENTER; it.setPadding(0, dp(18), 0, 0)
        })
        root.addView(tv("أضف ويدجت «مواقيت الصلاة» من الشاشة الرئيسية: اضغط مطوّلاً ← الأدوات ← i.Muslim", 12f, MUTED).also {
            it.gravity = Gravity.CENTER; it.setPadding(0, dp(16), 0, 0)
        })
        animateEntrance(root)
        addSplash(frame)
        return frame
    }

    /** شاشة الـ Fold الكبيرة: المحتوى يتوسّط بعرض مريح بدل ما يتمدد */
    private fun applyFoldPadding() {
        val wDp = resources.configuration.screenWidthDp
        val side = if (wDp > 600) ((wDp - 560) / 2).coerceAtLeast(16) else 16
        contentRoot.setPadding(dp(side), dp(20), dp(side), dp(32))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::contentRoot.isInitialized) applyFoldPadding()
        PrayerWidget.updateAll(this)
    }

    private fun textField(label: String, value: String, onChange: (String) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val e = EditText(this).apply {
            setText(value)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(GOLD)
            textSize = 16f
            gravity = Gravity.CENTER
            background = rounded(0x22000000, 10f, 0x55D9B44A)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: "") }
            })
        }
        row.addView(tv(label, 15f, TEXT), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(e, LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    /** بطاقة الموسم: شكل ومحتوى مختلف يوم الجمعة ورمضان والعيدين وعشر ذي الحجة */
    private fun buildSeasonCard(season: Season, now: Long) {
        seasonCard.removeAllViews()
        if (season == Season.NORMAL) { seasonCard.visibility = View.GONE; return }
        seasonCard.visibility = View.VISIBLE
        val h = Hijri.date(now)
        seasonCard.background = rounded(if (season.festive) 0xCC0F3A3A.toInt() else 0xCC2A2410.toInt(), 22f, GOLD)
        seasonCard.addView(tv(season.title, 26f, GOLD, true).also {
            it.gravity = Gravity.CENTER
            it.typeface = resources.getFont(R.font.amiri_quran)
            it.setShadowLayer(dp(10).toFloat(), 0f, 0f, 0x88D9B44A.toInt())
        })
        seasonCard.addView(tv(SeasonContent.greeting(season, h), 15f, TEXT).also { it.gravity = Gravity.CENTER })
        if (season.festive || season == Season.DHUL_HIJJAH || season == Season.ARAFAH) {
            seasonCard.addView(tv(SeasonContent.TAKBIR, 17f, 0xFFF6DE8D.toInt(), true).also {
                it.gravity = Gravity.CENTER; it.setPadding(dp(8), dp(10), dp(8), dp(6))
                it.typeface = resources.getFont(R.font.amiri_quran)
            })
        }
        for (t in SeasonContent.tips(season, h)) seasonCard.addView(tv(t, 14f, TEXT).also { it.setPadding(0, dp(6), 0, 0); it.setLineSpacing(0f, 1.2f) })
        when (season) {
            Season.FRIDAY -> {
                val maghrib = Times.forDay(this, 0, now)[Prayer.MAGHRIB.ordinal]
                seasonCard.addView(tv("🤲 ساعة الاستجابة تقريباً من ${Fmt.time(maghrib - 60 * 60_000L)} إلى المغرب ${Fmt.time(maghrib)}", 13f, 0xFFF6DE8D.toInt()).also { it.setPadding(0, dp(8), 0, 0) })
                seasonCard.addView(button("📖 اقرأ سورة الكهف") { Quran.open(this, 18) })
                seasonCard.addView(button("🎧 استمع لسورة الكهف", outline = true) {
                    QuranPlayer.play(this, 18)
                    startActivity(Intent(this, QuranReaderActivity::class.java).putExtra("s", 18).putExtra("follow", true))
                })
            }
            Season.RAMADAN -> seasonCard.addView(button("📖 وردك اليومي من القرآن") {
                startActivity(Intent(this, QuranActivity::class.java))
            })
            Season.DHUL_HIJJAH, Season.ARAFAH, Season.EID_ADHA -> {
                seasonCard.addView(button("🕋 دليل المناسك") { showManasik() })
                seasonCard.addView(button("🤲 أدعية الحج والعمرة", outline = true) {
                    startActivity(Intent(this, AdhkarReaderActivity::class.java).putExtra("cat", 115)
                        .putExtra("title", "أدعية الحج والعمرة").putExtra("cats", (115..121).toList().toIntArray()))
                })
            }
            else -> {}
        }
    }

    private fun showManasik() {
        val text = SeasonContent.MANASIK.joinToString("\n\n") { "${it.first}\n${it.second}" } +
            "\n\nهذا ملخص مختصر، وللتفصيل راجع أهل العلم أو دليل الحاج الرسمي."
        android.app.AlertDialog.Builder(this)
            .setTitle("🕋 دليل مناسك الحج (التمتّع)")
            .setMessage(text)
            .setPositiveButton("تم", null)
            .show()
    }

    private fun refreshStats(now: Long) {
        val w = Tracker.week(this, now)
        val streak = Tracker.fajrStreak(this, now)
        statsTv.text = "آخر ٧ أيام: صليت ${Fmt.num(w.prayed)} من ${Fmt.num(w.total)} • في جماعة ${Fmt.num(w.jamaah)}\n🌅 الفجر متواصل: ${Fmt.num(streak)} ${if (streak in 3..10) "أيام" else "يوم"}" +
            if (streak >= 7) " ما شاء الله 🔥" else ""
        weekDots.removeAllViews()
        for (d in -6..0) {
            val c = java.util.Calendar.getInstance(); c.timeInMillis = now; c.add(java.util.Calendar.DAY_OF_MONTH, d)
            val key = Tracker.dayKey(c.timeInMillis)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            col.addView(tv(java.text.SimpleDateFormat("EEE", Locale("ar")).format(c.time), 10f, MUTED).also { it.gravity = Gravity.CENTER })
            for (p in Prayer.FIVE) {
                val v = Tracker.get(this, key, p)
                col.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(when (v) { 2 -> GOLD; 1 -> 0xFF34D399.toInt(); else -> 0x26FFFFFF })
                    }
                }, LinearLayout.LayoutParams(dp(12), dp(12)).also { it.topMargin = dp(4) })
            }
            weekDots.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun backupPick(req: Int) {
        val i = if (req == REQ_EXPORT) Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "mawaqit-backup-${Tracker.dayKey(System.currentTimeMillis())}.json")
        } else Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        @Suppress("DEPRECATION")
        startActivityForResult(i, req)
    }

    private fun pickSound(req: Int) {
        android.app.AlertDialog.Builder(this)
            .setTitle("من وين تختار الصوت؟")
            .setItems(arrayOf("🎵 من الأصوات اللي في جوالك (داخل التطبيق)", "📁 من الملفات أو Google Drive")) { _, w ->
                if (w == 0) showDeviceAudio(req) else {
                    toast("للرجوع للتطبيق بدون اختيار: اضغط ✕ أو زر الرجوع")
                    openPicker(req)
                }
            }.setNegativeButton("إلغاء", null).show()
    }

    private fun openPicker(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, req)
    }

    /** شاشة افتتاح قصيرة (صورة الكعبة من Canva) تختفي بنعومة */
    private fun addSplash(frame: FrameLayout) {
        val res = Pictures.splash(this)
        if (res == 0 || splashShown) return
        splashShown = true
        val box = FrameLayout(this).apply { setBackgroundColor(0xFF061512.toInt()); isClickable = true }
        box.addView(ImageView(this).apply { setImageResource(res); scaleType = ImageView.ScaleType.CENTER_CROP },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val title = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        title.addView(tv("i.Muslim", 44f, GOLD, true).also {
            it.typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
            it.setShadowLayer(dp(16).toFloat(), 0f, 0f, 0xCC000000.toInt())
        })
        title.addView(tv("الصلاة يا مسلم", 18f, TEXT).also { it.gravity = Gravity.CENTER; it.setShadowLayer(dp(10).toFloat(), 0f, 0f, 0xCC000000.toInt()) })
        box.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).also { it.topMargin = dp(140) })
        frame.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        title.alpha = 0f; title.translationY = dp(20).toFloat()
        title.animate().alpha(1f).translationY(0f).setDuration(700).setStartDelay(150).start()
        box.animate().alpha(0f).setStartDelay(1500).setDuration(600).withEndAction { frame.removeView(box) }.start()
    }

    /** دخول البطاقات بحركة متتابعة (تظهر وتطلع من تحت) */
    private fun animateEntrance(root: LinearLayout) {
        for (i in 0 until root.childCount) {
            val v = root.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(40).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(80L * i)
                .setDuration(650)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }
    }

    /** نبض خفيف لصف الصلاة القادمة */
    private fun pulse(v: View?) {
        pulseAnim?.cancel()
        v?.alpha = 1f
        if (v == null) return
        pulseAnim = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.55f).apply {
            duration = 1300
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radiusDp: Float, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * resources.displayMetrics.density
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(2), 0, dp(2))
    }

    private fun header(text: String) = tv(text, 17f, GOLD, true).also { it.setPadding(0, 0, 0, dp(8)) }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Look.cardColor(this@MainActivity), 22f, 0x33D9B44A)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(14) }
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(0x22FFFFFF)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).also {
            it.topMargin = dp(10); it.bottomMargin = dp(10)
        }
    }

    private fun switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        textSize = 16f
        setTextColor(TEXT)
        isChecked = checked
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    private fun button(label: String, outline: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        isAllCaps = false
        setTextColor(if (outline) GOLD else BG)
        background = if (outline) rounded(Color.TRANSPARENT, 14f, GOLD) else rounded(GOLD, 14f)
        setOnClickListener { onClick() }
        Press.apply(this)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dp(10) }
    }

    private fun numberField(label: String, value: Int, signed: Boolean, onChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val l = tv(label, 15f, TEXT)
        val e = EditText(this).apply {
            setText(value.toString())
            inputType = InputType.TYPE_CLASS_NUMBER or (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
            setTextColor(GOLD)
            textSize = 16f
            gravity = Gravity.CENTER
            background = rounded(0x22000000, 10f, 0x55D9B44A)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val v = s?.toString()?.trim()?.toIntOrNull() ?: return
                    onChange(v)
                }
            })
        }
        row.addView(l, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(e, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    companion object {
        const val REQ_NOTIF = 1
        const val REQ_LOC = 2
        const val REQ_SOUND = 3
        const val REQ_SOUND_FAJR = 4
        const val REQ_AUDIO_PERM = 41
        const val REQ_EXPORT = 5
        const val REQ_IMPORT = 6
    }
}
