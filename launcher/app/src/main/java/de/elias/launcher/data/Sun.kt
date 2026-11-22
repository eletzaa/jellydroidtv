package de.elias.launcher.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Sonnenauf- und -untergang (NOAA-Näherung, auf wenige Minuten genau), ohne Internet. */
object Sun {
    /** Minuten seit Mitternacht (lokale Zeit) für Aufgang und Untergang, null bei Polartag/-nacht. */
    fun times(lat: Double, lon: Double, cal: Calendar = Calendar.getInstance()): Pair<Int, Int>? {
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val tzOffsetH = TimeZone.getDefault().getOffset(cal.timeInMillis) / 3_600_000.0
        fun calc(rising: Boolean): Double? {
            val lngHour = lon / 15.0
            val t = day + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
            val m = 0.9856 * t - 3.289
            var l = m + 1.916 * sin(Math.toRadians(m)) + 0.020 * sin(Math.toRadians(2 * m)) + 282.634
            l = (l + 360) % 360
            var ra = Math.toDegrees(kotlin.math.atan(0.91764 * tan(Math.toRadians(l))))
            ra = (ra + 360) % 360
            ra += floor(l / 90) * 90 - floor(ra / 90) * 90
            ra /= 15.0
            val sinDec = 0.39782 * sin(Math.toRadians(l))
            val cosDec = cos(asin(sinDec))
            val cosH = (cos(Math.toRadians(90.833)) - sinDec * sin(Math.toRadians(lat))) / (cosDec * cos(Math.toRadians(lat)))
            if (cosH > 1 || cosH < -1) return null
            var h = if (rising) 360 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
            h /= 15.0
            val localT = h + ra - 0.06571 * t - 6.622
            val ut = ((localT - lngHour) % 24 + 24) % 24
            return ((ut + tzOffsetH) % 24 + 24) % 24
        }
        val rise = calc(true) ?: return null
        val set = calc(false) ?: return null
        return (rise * 60).toInt() to (set * 60).toInt()
    }

    /** Hell von 1 Stunde nach Sonnenaufgang bis 1 Stunde vor Sonnenuntergang. */
    fun isDaylight(lat: Double, lon: Double, cal: Calendar = Calendar.getInstance()): Boolean {
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val (rise, set) = times(lat, lon, cal) ?: return cal.get(Calendar.HOUR_OF_DAY) in 7..18
        return now in (rise + 60) until (set - 60)
    }

    fun fmt(min: Int) = String.format("%02d:%02d", min / 60, min % 60)
}
