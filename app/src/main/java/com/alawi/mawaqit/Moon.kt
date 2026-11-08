package com.alawi.mawaqit

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/** منازل (أطوار) القمر بالحساب الفلكي */
object Moon {
    private const val SYNODIC = 29.530588853          // طول الشهر القمري بالأيام
    private const val KNOWN_NEW_MOON = 947182440000L  // محاق 6 يناير 2000 الساعة 18:14 UTC

    data class Phase(val emoji: String, val name: String, val illumination: Int, val ageDays: Double)

    fun phase(millis: Long): Phase {
        val days = (millis - KNOWN_NEW_MOON) / 86_400_000.0
        var age = days % SYNODIC
        if (age < 0) age += SYNODIC
        val frac = age / SYNODIC
        val illum = ((1 - cos(2 * PI * frac)) / 2 * 100).roundToInt()
        val (emoji, name) = when {
            age < 1.0 -> "🌑" to "محاق"
            age < 6.4 -> "🌒" to "هلال متزايد"
            age < 8.4 -> "🌓" to "تربيع أول"
            age < 13.8 -> "🌔" to "أحدب متزايد"
            age < 15.8 -> "🌕" to "بدر"
            age < 21.1 -> "🌖" to "أحدب متناقص"
            age < 23.1 -> "🌗" to "تربيع أخير"
            age < 28.5 -> "🌘" to "هلال متناقص"
            else -> "🌑" to "محاق"
        }
        return Phase(emoji, name, illum, age)
    }

    /** عمر القمر كنسبة من الشهر (0 محاق، 0.5 بدر) للرسم */
    fun frac(millis: Long): Double = phase(millis).ageDays / SYNODIC

    fun label(millis: Long): String {
        val p = phase(millis)
        return "${p.emoji} ${p.name} • ${Fmt.num(p.illumination)}٪"
    }
}
