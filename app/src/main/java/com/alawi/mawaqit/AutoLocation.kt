package com.alawi.mawaqit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import java.util.Locale

/**
 * تحديث الموقع تلقائياً في الخلفية: لو انتقلت لمدينة ثانية (أكثر من 5 كم)
 * تتغير الأوقات على حسب المنطقة الجديدة بدون ما تضغط شي.
 */
object AutoLocation {
    private const val MIN_MOVE_METERS = 5_000f

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** يستخدم آخر موقع معروف عند الجوال (ما يستهلك بطارية). يرجع true لو تغيّر الموقع */
    @SuppressLint("MissingPermission")
    fun updateFromLastKnown(ctx: Context): Boolean {
        if (!hasPermission(ctx)) return false
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return false
        val last = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: Exception) { null } }
            .maxByOrNull { it.time } ?: return false
        return apply(ctx, last)
    }

    /** يحفظ الموقع الجديد لو ابتعد أكثر من 5 كم. يرجع true لو تغيّر */
    fun apply(ctx: Context, loc: Location): Boolean {
        val prefs = Prefs(ctx)
        val dist = FloatArray(1)
        Location.distanceBetween(prefs.lat, prefs.lng, loc.latitude, loc.longitude, dist)
        if (dist[0] < MIN_MOVE_METERS) return false
        prefs.lat = loc.latitude
        prefs.lng = loc.longitude
        prefs.city = String.format(Locale.US, "%.3f، %.3f", loc.latitude, loc.longitude)
        // اسم المدينة في الخلفية
        Thread {
            val name = cityName(ctx, loc.latitude, loc.longitude)
            if (name != null) prefs.city = name
            PrayerWidget.updateAll(ctx)
        }.start()
        return true
    }

    fun cityName(ctx: Context, lat: Double, lng: Double): String? = try {
        @Suppress("DEPRECATION")
        val a = Geocoder(ctx, Locale("ar")).getFromLocation(lat, lng, 1)?.firstOrNull()
        a?.locality ?: a?.subAdminArea ?: a?.adminArea
    } catch (e: Exception) { null }
}
