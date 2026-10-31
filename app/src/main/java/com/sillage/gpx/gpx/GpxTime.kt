package com.sillage.gpx.gpx

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

internal object GpxTime {
    fun parse(text: String): Long? {
        val s = text.trim()
        if (s.isEmpty()) return null
        return try {
            Instant.parse(s).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(s).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    // Sans fuseau : on considère UTC (fréquent dans les vieux fichiers GPX 1.0).
                    java.time.LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun format(millis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
}
