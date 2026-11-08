package com.alawi.mawaqit

import android.icu.util.IslamicCalendar

object Hijri {
    private val months = arrayOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
    )

    private fun cal(millis: Long): IslamicCalendar {
        val c = IslamicCalendar()
        c.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
        c.timeInMillis = millis
        return c
    }

    fun format(millis: Long): String {
        val c = cal(millis)
        val d = c.get(android.icu.util.Calendar.DAY_OF_MONTH)
        val m = c.get(android.icu.util.Calendar.MONTH)
        val y = c.get(android.icu.util.Calendar.YEAR)
        return "${Fmt.num(d)} ${months[m.coerceIn(0, 11)]} ${Fmt.num(y)} هـ"
    }

    fun isRamadan(millis: Long): Boolean =
        cal(millis).get(android.icu.util.Calendar.MONTH) == IslamicCalendar.RAMADAN
}
