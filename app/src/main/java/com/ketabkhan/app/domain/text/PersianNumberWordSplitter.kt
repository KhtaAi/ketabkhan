package com.ketabkhan.app.domain.text

/**
 * Pure Kotlin parser & splitter for glued Persian letter-written numbers
 * (e.g., "بیستونهروزه" -> "بیست‌ونه روزه", "صدوبیست" -> "صد‌وبیست", "دوهزار" -> "دو هزار").
 * Leaves tokens unchanged unless they strictly match a valid Persian number-word sequence.
 */
object PersianNumberWordSplitter {

  private const val ZWNJ = '\u200C'

  private val units = listOf(
    "چهار", "هشت", "پنج", "هفت", "یک", "دو", "سه", "شش", "نه",
  )

  private val teens = listOf(
    "چهارده", "دوازده", "شانزده", "یازده", "سیزده", "پانزده", "هفده", "هجده", "نوزده", "ده",
  )

  private val tens = listOf(
    "هفتاد", "هشتاد", "پنجاه", "بیست", "چهل", "شصت", "نود", "سی",
  )

  private val hundreds = listOf(
    "چهارصد", "دویست", "هفتصد", "هشتصد", "سیصد", "پانصد", "ششصد", "یکصد", "نهصد", "صد",
  )

  private val scales = listOf(
    "میلیارد", "میلیون", "هزار",
  )

  private val knownCountableTails = setOf(
    "روزه", "ساله", "ماهه", "هفته‌ای", "هفته", "ساعته", "دقیقه‌ای", "دقیقه",
    "نفره", "نفر", "صفحه", "متری", "متر", "کیلومتری", "کیلومتر", "درصدی", "درصد",
    "جلد", "سال", "ماه", "روز", "تومان", "تومانی", "ریال", "ریالی", "گانه", "بار", "مرتبه",
  )

  private val excludedTokens = setOf(
    "بیستونهرروزه", "بیستون", "سیستم", "سیستمها", "سیستمهای", "سیستمهایی",
    "دولت", "دوست", "دوران", "دوباره", "دهان", "صدا", "نهاد", "نهایت",
    "چهارم", "چهارشنبه", "پنجره", "هفته", "یکتا", "یکسان", "یکدیگر",
  )

  private val persianTokenRegex = Regex(
    "(?<![\\u0621-\\u06D5\\u200C])[\\u0621-\\u06D5]+(?![\\u0621-\\u06D5\\u200C])"
  )

  private data class ParseResult(
    val formattedNumber: String,
    val endIndex: Int,
    val componentCount: Int,
  )

  fun splitInText(text: String): String {
    if (text.isBlank()) return text
    return persianTokenRegex.replace(text) { match ->
      splitToken(match.value)
    }
  }

  fun splitToken(token: String): String {
    if (token.length < 3 || token in excludedTokens) return token
    if (PersianCompoundDictionary.containsKey(token)) return token

    val parsed = parseNumberSequence(token) ?: return token
    val tail = token.substring(parsed.endIndex)

    if (tail.isEmpty()) {
      return if (parsed.componentCount >= 2) parsed.formattedNumber else token
    }

    if (parsed.componentCount >= 2) {
      if (tail == "م" || tail == "مین") {
        return parsed.formattedNumber + tail
      }
      if (tail.length >= 2) {
        return "${parsed.formattedNumber} $tail"
      }
      return token
    }

    // Single number word followed by a known countable/unit word
    if (tail in knownCountableTails) {
      return "${parsed.formattedNumber} $tail"
    }

    return token
  }

  private fun parseNumberSequence(token: String): ParseResult? {
    var cursor = 0
    val out = StringBuilder()
    var totalComponents = 0

    // Parse from highest scale (میلیارد -> میلیون -> هزار) down to sub-1000 remainder
    var scaleIndex = 0
    while (scaleIndex < scales.size) {
      val scaleWord = scales[scaleIndex]
      val savedCursor = cursor
      val savedLen = out.length
      val savedCount = totalComponents

      var needsConnectorVav = false
      if (totalComponents > 0) {
        if (cursor < token.length && token[cursor] == 'و') {
          needsConnectorVav = true
          cursor++
        } else {
          scaleIndex++
          continue
        }
      }

      val multiplier = parseSub1000(token, cursor)
      if (multiplier != null && token.startsWith(scaleWord, multiplier.endIndex)) {
        if (needsConnectorVav) {
          out.append(ZWNJ).append('و')
        }
        out.append(multiplier.formattedNumber).append(' ').append(scaleWord)
        cursor = multiplier.endIndex + scaleWord.length
        totalComponents += multiplier.componentCount + 1
        scaleIndex++
        continue
      } else if (totalComponents == 0 && token.startsWith(scaleWord, cursor)) {
        out.append(scaleWord)
        cursor += scaleWord.length
        totalComponents += 1
        scaleIndex++
        continue
      }

      cursor = savedCursor
      out.setLength(savedLen)
      totalComponents = savedCount
      scaleIndex++
    }

    // Parse trailing sub-1000 part
    if (totalComponents > 0) {
      if (cursor < token.length && token[cursor] == 'و') {
        val sub = parseSub1000(token, cursor + 1)
        if (sub != null) {
          out.append(ZWNJ).append('و').append(sub.formattedNumber)
          cursor = sub.endIndex
          totalComponents += sub.componentCount
        }
      }
    } else {
      val sub = parseSub1000(token, cursor) ?: return null
      out.append(sub.formattedNumber)
      cursor = sub.endIndex
      totalComponents += sub.componentCount
    }

    if (totalComponents == 0 || cursor == 0) return null
    return ParseResult(
      formattedNumber = out.toString(),
      endIndex = cursor,
      componentCount = totalComponents,
    )
  }

  private fun parseSub1000(token: String, start: Int): ParseResult? {
    if (start >= token.length) return null

    // 1. Hundreds + optional "و" + (teens | tens_with_unit | units)
    val hundred = matchPrefix(token, start, hundreds)
    if (hundred != null) {
      var cursor = start + hundred.length
      val sb = StringBuilder(hundred)
      var count = 1

      if (cursor < token.length && token[cursor] == 'و') {
        val afterHundred = parseUnder100(token, cursor + 1)
        if (afterHundred != null) {
          sb.append(ZWNJ).append('و').append(afterHundred.formattedNumber)
          cursor = afterHundred.endIndex
          count += afterHundred.componentCount
        }
      }
      return ParseResult(sb.toString(), cursor, count)
    }

    // 2. Under 100 directly
    return parseUnder100(token, start)
  }

  private fun parseUnder100(token: String, start: Int): ParseResult? {
    if (start >= token.length) return null

    // 1. Teens (10..19) - must check before units so "چهارده" isn't read as "چهار" + "ده"
    val teen = matchPrefix(token, start, teens)
    if (teen != null) {
      return ParseResult(teen, start + teen.length, 1)
    }

    // 2. Tens (20..90) + optional "و" + units (1..9)
    val ten = matchPrefix(token, start, tens)
    if (ten != null) {
      var cursor = start + ten.length
      val sb = StringBuilder(ten)
      var count = 1

      if (cursor < token.length && token[cursor] == 'و') {
        val unit = matchPrefix(token, cursor + 1, units)
        if (unit != null) {
          sb.append(ZWNJ).append('و').append(unit)
          cursor = cursor + 1 + unit.length
          count += 1
        }
      }
      return ParseResult(sb.toString(), cursor, count)
    }

    // 3. Units (1..9)
    val unit = matchPrefix(token, start, units)
    if (unit != null) {
      return ParseResult(unit, start + unit.length, 1)
    }

    return null
  }

  private fun matchPrefix(token: String, start: Int, candidates: List<String>): String? {
    for (candidate in candidates) {
      if (token.startsWith(candidate, start)) {
        return candidate
      }
    }
    return null
  }
}
