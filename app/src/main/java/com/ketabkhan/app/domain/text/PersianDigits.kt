package com.ketabkhan.app.domain.text

import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import java.util.Date
import java.util.Locale

object PersianDigits {
  private val persianDigitChars = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

  fun formatInt(value: Int): String = toPersianDigits(value.toString())

  fun formatLong(value: Long): String = toPersianDigits(value.toString())

  fun formatFloat(value: Float, decimals: Int = 1): String {
    val formatted = String.format(Locale.US, "%.${decimals}f", value).replace('.', '٫')
    return toPersianDigits(formatted)
  }

  fun toPersianDigits(input: String): String {
    if (input.isEmpty()) return input
    val sb = StringBuilder(input.length)
    for (ch in input) {
      when (ch) {
        in '0'..'9' -> sb.append(persianDigitChars[ch - '0'])
        in '\u0660'..'\u0669' -> sb.append(persianDigitChars[ch - '\u0660'])
        '.' -> sb.append('.')
        else -> sb.append(ch)
      }
    }
    return sb.toString()
  }

  fun normalizeToAsciiDigits(input: String): String {
    if (input.isEmpty()) return input
    val sb = StringBuilder(input.length)
    for (ch in input) {
      when (ch) {
        in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
        in '\u0660'..'\u0669' -> sb.append(('0'.code + (ch.code - '\u0660'.code)).toChar())
        else -> sb.append(ch)
      }
    }
    return sb.toString()
  }

  fun parseFlexibleInt(input: String): Int? {
    val normalized = normalizeToAsciiDigits(input).trim()
    if (normalized.isEmpty()) return null
    return normalized.toIntOrNull()
  }

  fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "${formatLong(bytes)} بایت"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
      val rounded = String.format(Locale.US, "%.0f", kb)
      return "${toPersianDigits(rounded)} کیلوبایت"
    }
    val mb = kb / 1024.0
    val formatted = String.format(Locale.US, "%.1f", mb).replace('.', '٫')
    return "${toPersianDigits(formatted)} مگابایت"
  }

  fun formatPersianDate(epochMillis: Long): String {
    return try {
      val locale = ULocale("fa_IR@calendar=persian")
      val formatter = SimpleDateFormat("d MMMM yyyy", locale)
      toPersianDigits(formatter.format(Date(epochMillis)))
    } catch (_: Throwable) {
      val fallback = java.text.SimpleDateFormat("yyyy/MM/dd", Locale("fa", "IR"))
      toPersianDigits(fallback.format(Date(epochMillis)))
    }
  }
}
