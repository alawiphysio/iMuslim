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
    private lateinit var landmarkImg: ImageView
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
        refresh()
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
    }

    private fun refresh() {
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
            figureIv.setImageResource(Pictures.figure(this, st.iqamaPhase))
            figureIv.alpha = 0f; figureIv.scaleX = 0.8f; figureIv.scaleY = 0.8f
            figureIv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(700)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
        if (st.iqamaPhase) {
            nextLabelTv.text = "حان وقت الصلاة • استعد للإقامة"
            nextNameTv.text = "صلاة $stName"
            nextTimeTv.text = "أذّن ${Fmt.time(st.adhanT)}  •  الإقامة ${Fmt.time(st.iqamaT)}"
            ringCaptionTv.text = "للإقامة"
        } else {
            nextLabelTv.text = "الصلاة القادمة"
            nextNameTv.text = stName
            nextTimeTv.text = "الأذان ${Fmt.time(st.adhanT)}  •  الإقامة ${Fmt.time(st.iqamaT)}"
            ringCaptionTv.text = "للأذان"
        }
        val mp = Moon.phase(now)
        moonTv.text = "${mp.emoji}  ${mp.name}"
        moonView.frac = Moon.frac(now)
        val lm = Landmarks.detect(prefs.lat, prefs.lng)
        landmarkView.type = lm
        val lmImg = Pictures.landmark(this, lm)
        if (lmImg != 0) {
            landmarkImg.setImageResource(lmImg)
            landmarkImg.visibility = View.VISIBLE
            landmarkView.visibility = View.GONE
        } else {
            landmarkImg.visibility = View.GONE
            landmarkView.visibility = View.VISIBLE
        }
        landmarkTv.text = lm.title
        landmarkTv.visibility = if (lm.title.isEmpty()) View.GONE else View.VISIBLE
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
        minutesTv.text = "باقي " + Fmt.minutesLeft(lastNextT - now) + " " + ringCaptionTv.text
        val mins = ((lastNextT - now + 59_999L) / 60_000L).toInt().coerceAtLeast(0)
        minutesBigTv.text = Fmt.num(mins)
        minutesUnitTv.text = when { mins == 1 -> "دقيقة"; mins == 2 -> "دقيقتين"; mins in 3..10 -> "دقائق"; else -> "دقيقة" }
        if (now / 1000 % 30 == 0L) ringView.setProgress(Times.state(this, now).progress, false)
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
            REQ_LOC -> if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) fetchLocation()
            else { prefs.firstRun = false; toast("بنستخدم أوقات مكة المكرمة") }
        }
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == REQ_SOUND || requestCode == REQ_SOUND_FAJR) && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            var name: String? = null
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) name = it.getString(0)
                }
            } catch (e: Exception) { }
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

    // ---------------- الواجهة ----------------

    private fun buildUi(): View {
        val frame = FrameLayout(this)
        frame.addView(SkyView(this), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
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
        root.addView(tv("مواقيت أحمد", 30f, GOLD, true).also {
            it.gravity = Gravity.CENTER
            it.setShadowLayer(dp(10).toFloat(), 0f, 0f, 0x88D9B44A.toInt())
        })
        cityTv = tv("", 15f, TEXT).also { it.gravity = Gravity.CENTER }
        dateTv = tv("", 13f, MUTED).also { it.gravity = Gravity.CENTER }
        root.addView(cityTv)
        root.addView(dateTv)

        // رسمة أشهر مسجد في مدينتك
        landmarkView = LandmarkView(this)
        root.addView(landmarkView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(190)).also { it.topMargin = dp(10) })
        landmarkImg = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = rounded(0x22000000, 22f)
            visibility = View.GONE
        }
        root.addView(landmarkImg, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)).also { it.topMargin = dp(10) })
        landmarkTv = tv("", 14f, GOLD, true).also { it.gravity = Gravity.CENTER }
        root.addView(landmarkTv)

        // بطاقة الصلاة القادمة: حلقة ذهبية والعدّاد داخلها
        val nextCard = card()
        nextCard.gravity = Gravity.CENTER_HORIZONTAL
        figureIv = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            background = rounded(0x22000000, 24f)
        }
        nextCard.addView(figureIv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)).also { it.bottomMargin = dp(10) })
        nextLabelTv = tv("الصلاة القادمة", 14f, MUTED).also { it.gravity = Gravity.CENTER }
        nextCard.addView(nextLabelTv)
        nextNameTv = tv("", 32f, GOLD, true).also { it.gravity = Gravity.CENTER }
        nextCard.addView(nextNameTv)

        val ringBox = FrameLayout(this)
        ringView = RingView(this)
        ringBox.addView(ringView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        minutesBigTv = tv("", 54f, TEXT, true).also {
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

        minutesTv = tv("", 17f, GOLD, true).also { it.gravity = Gravity.CENTER }
        nextTimeTv = tv("", 15f, TEXT).also { it.gravity = Gravity.CENTER }
        nextCard.addView(minutesTv)
        nextCard.addView(nextTimeTv)
        root.addView(nextCard)

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
            rowName[p] = name; rowTime[p] = time; rowIqama[p] = iq; rowBox[p] = row; rowIcon[p] = icon
            timesCard.addView(row)
        }
        root.addView(timesCard)

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
        voice.addView(textField("يناديك باسم", prefs.userName) { if (it.isNotBlank()) prefs.userName = it.trim() })
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

        root.addView(tv("أضف ويدجت «مواقيت الصلاة» من الشاشة الرئيسية: اضغط مطوّلاً ← الأدوات ← مواقيت أحمد", 12f, MUTED).also {
            it.gravity = Gravity.CENTER; it.setPadding(0, dp(16), 0, 0)
        })
        animateEntrance(root)
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

    private fun pickSound(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, req)
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
        background = rounded(0xCC0F2E27.toInt(), 22f, 0x33D9B44A)
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
    }
}
