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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
        lastNextT = nextT
        cityTv.text = "📍 ${prefs.city}"
        dateTv.text = "${Hijri.format(now)}  •  ${Fmt.date(now)}"
        nextNameTv.text = next.displayName(nextT)
        nextTimeTv.text = "الأذان الساعة ${Fmt.time(nextT)}"
        for (p in Prayer.values()) {
            val t = today[p.ordinal]
            rowName[p]?.text = p.displayName(t)
            rowTime[p]?.text = Fmt.time(t)
            rowIqama[p]?.text = if (p.hasAdhan && prefs.iqamaOn)
                "الإقامة ${Fmt.time(t + prefs.iqamaMin(p) * 60_000L)}" else ""
            val isNext = p == next && t == nextT
            rowBox[p]?.background = rounded(if (isNext) 0x33D9B44A else Color.TRANSPARENT, 14f)
            rowName[p]?.setTextColor(if (isNext) GOLD else TEXT)
        }
        soundTv.text = "الصوت الحالي: " + (prefs.adhanName ?: "صوت المنبه الافتراضي (اختر ملف أذان)")
        tick()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        if (lastNextT != 0L && now >= lastNextT) { refresh(); return }
        countdownTv.text = Fmt.countdown(lastNextT - now)
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
        if (requestCode == REQ_SOUND && resultCode == RESULT_OK) {
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
            prefs.adhanUri = uri.toString()
            prefs.adhanName = name ?: "ملف أذان"
            refresh()
            toast("تم اختيار صوت الأذان ✅")
        }
    }

    // ---------------- الواجهة ----------------

    private fun buildUi(): View {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(BG)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(20), dp(16), dp(32))
        }
        scroll.addView(root)

        // رأس الصفحة
        root.addView(tv("مواقيت أحمد", 28f, GOLD, true))
        cityTv = tv("", 15f, TEXT)
        dateTv = tv("", 13f, MUTED)
        root.addView(cityTv)
        root.addView(dateTv)

        // بطاقة الصلاة القادمة
        val nextCard = card()
        nextCard.gravity = Gravity.CENTER_HORIZONTAL
        nextCard.addView(tv("الصلاة القادمة", 14f, MUTED).also { it.gravity = Gravity.CENTER })
        nextNameTv = tv("", 34f, GOLD, true).also { it.gravity = Gravity.CENTER }
        nextTimeTv = tv("", 15f, TEXT).also { it.gravity = Gravity.CENTER }
        countdownTv = tv("00:00:00", 46f, TEXT, true).also {
            it.gravity = Gravity.CENTER
            it.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            it.layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        nextCard.addView(nextNameTv)
        nextCard.addView(countdownTv)
        nextCard.addView(nextTimeTv)
        root.addView(nextCard)

        // بطاقة أوقات اليوم
        val timesCard = card()
        timesCard.addView(header("أوقات اليوم"))
        for (p in Prayer.values()) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val name = tv(p.ar, 18f, TEXT, true)
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
            val time = tv("", 18f, TEXT, true).also { it.gravity = Gravity.END }
            val iq = tv("", 12f, MUTED).also { it.gravity = Gravity.END }
            col.addView(time); col.addView(iq)
            row.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(col)
            rowName[p] = name; rowTime[p] = time; rowIqama[p] = iq; rowBox[p] = row
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

        // الصوت
        val sound = card()
        sound.addView(header("صوت الأذان"))
        soundTv = tv("", 13f, MUTED)
        sound.addView(soundTv)
        sound.addView(button("اختيار ملف الأذان (MP3) من الجوال") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_SOUND)
        })
        val testRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        testRow.addView(button("▶ تجربة") {
            AdhanService.start(this, Prayer.DHUHR, System.currentTimeMillis(), true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginEnd = dp(8) })
        testRow.addView(button("■ إيقاف", outline = true) { AdhanService.stop(this) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sound.addView(testRow)
        sound.addView(tv("الأذان يطلع على صوت المنبه، تأكد إن صوت المنبه مو مقفّل.", 12f, MUTED))
        root.addView(sound)

        // الموقع وتعديل الأوقات
        val loc = card()
        loc.addView(header("الموقع وطريقة الحساب"))
        loc.addView(tv("طريقة الحساب: تقويم أم القرى (الفجر 18.5°، العشاء بعد المغرب بساعة ونص، وساعتين في رمضان)", 13f, MUTED))
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
        return scroll
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
        background = rounded(CARD, 20f)
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
    }
}
