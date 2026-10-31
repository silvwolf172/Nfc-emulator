package com.sillage.gpx.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Format {
    private val locale: Locale = Locale.FRANCE
    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.FRENCH)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy 'à' HH:mm", Locale.FRENCH)

    fun distance(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToInt()} m"
        meters < 100_000 -> String.format(locale, "%.1f km", meters / 1000)
        else -> String.format(locale, "%,d km", (meters / 1000).roundToLong())
    }

    fun elevation(meters: Double): String = String.format(locale, "%,d m", meters.roundToLong())

    fun signedElevation(meters: Double, positive: Boolean): String =
        (if (positive) "+" else "−") + elevation(meters)

    fun duration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h >= 24 -> "${h / 24} j ${h % 24} h"
            h > 0 -> String.format(locale, "%d h %02d", h, m)
            m > 0 -> "$m min"
            else -> "$s s"
        }
    }

    fun speed(ms: Double): String = String.format(locale, "%.1f km/h", ms * 3.6)

    fun pace(ms: Double): String {
        if (ms <= 0.01) return "—"
        val secPerKm = (1000 / ms).roundToLong()
        return String.format(locale, "%d'%02d\" /km", secPerKm / 60, secPerKm % 60)
    }

    fun date(millis: Long): String = dateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun dateTime(millis: Long): String = dateTimeFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    fun coordinates(lat: Double, lon: Double): String =
        String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)

    fun count(n: Int, singular: String, plural: String = singular + "s"): String =
        "${String.format(locale, "%,d", n)} ${if (n > 1) plural else singular}"
}
