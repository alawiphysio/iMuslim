package com.alawi.mawaqit

import android.icu.util.IslamicCalendar

/**
 * التاريخ الهجري حسب تقويم أم القرى الرسمي (جداول أم القرى مضمّنة في أندرويد حتى ١٥٠٠هـ تقريباً).
 * adjustDays: تعديل يدوي (±) لو أُعلنت رؤية الهلال بخلاف التقويم، مثل بداية رمضان أو العيد.
 */
object Hijri {
    @Volatile var adjustDays: Int = 0

    val MONTHS = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    private fun cal(millis: Long): IslamicCalendar {
        val c = IslamicCalendar()
        c.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
        c.timeInMillis = millis + adjustDays * 86_400_000L
        return c
    }

    fun date(millis: Long): HDate {
        val c = cal(millis)
        return HDate(c.get(android.icu.util.Calendar.YEAR), c.get(android.icu.util.Calendar.MONTH).coerceIn(0, 11),
            c.get(android.icu.util.Calendar.DAY_OF_MONTH))
    }

    fun format(millis: Long): String {
        val d = date(millis)
        return "${Fmt.num(d.day)} ${MONTHS[d.month]} ${Fmt.num(d.year)} هـ"
    }

    fun isRamadan(millis: Long): Boolean = date(millis).month == IslamicCalendar.RAMADAN

    /** بداية يوم ١ من شهر هجري (بالتوقيت المحلي، الساعة ١٢ ظهراً) */
    fun monthStart(year: Int, month: Int): Long {
        val c = IslamicCalendar()
        c.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
        c.clear()
        c.set(year, month, 1, 12, 0, 0)
        return c.timeInMillis - adjustDays * 86_400_000L
    }

    fun monthLength(year: Int, month: Int): Int {
        val c = IslamicCalendar()
        c.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
        c.clear()
        c.set(year, month, 1, 12, 0, 0)
        return c.getActualMaximum(android.icu.util.Calendar.DAY_OF_MONTH)
    }
}
