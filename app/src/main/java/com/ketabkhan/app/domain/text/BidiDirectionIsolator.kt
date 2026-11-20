package com.ketabkhan.app.domain.text

import com.ketabkhan.app.data.pdf.BlockLanguage

/**
 * Pure Kotlin helper for block script-language classification and UI-only Unicode Bidi
 * directional isolation (LRI/PDI) without mutating underlying logical text.
 */
object BidiDirectionIsolator {

  const val LRI = '\u2066'
  const val PDI = '\u2069'

  private val embeddedLatinRunRegex = Regex(
    "(?<![A-Za-z0-9\\u00C0-\\u024F\\u2066])[A-Za-z\\u00C0-\\u024F][A-Za-z0-9\\u00C0-\\u024F._:/+#-]*(?:\\s+[A-Za-z0-9\\u00C0-\\u024F._:/+#-]+)*"
  )

  fun classifyBlockLanguage(text: String): BlockLanguage {
    if (text.isBlank()) return BlockLanguage.PERSIAN

    var persianArabicLetters = 0
    var latinLetters = 0

    for (ch in text) {
      if (!ch.isLetter()) continue
      when {
        isPersianOrArabicLetter(ch) -> persianArabicLetters++
        isLatinLetter(ch) -> latinLetters++
      }
    }

    val total = persianArabicLetters + latinLetters
    if (total == 0) return BlockLanguage.PERSIAN

    val latinRatio = latinLetters.toFloat() / total.toFloat()
    val persianRatio = persianArabicLetters.toFloat() / total.toFloat()

    return when {
      latinRatio >= 0.80f -> BlockLanguage.LATIN
      persianRatio >= 0.85f -> BlockLanguage.PERSIAN
      else -> BlockLanguage.MIXED
    }
  }

  /**
   * Formats text strictly for presentation in Compose without changing the stored logical text.
   * For [BlockLanguage.LATIN], returns [text] untouched because the UI sets TextDirection.Ltr.
   * For [BlockLanguage.PERSIAN] and [BlockLanguage.MIXED], wraps embedded Latin spans in
   * Unicode LRI/PDI isolates so surrounding punctuation stays anchored cleanly.
   */
  fun formatForDisplay(text: String, language: BlockLanguage): String {
    if (text.isBlank()) return text
    return when (language) {
      BlockLanguage.LATIN -> text
      BlockLanguage.PERSIAN,
      BlockLanguage.MIXED -> isolateEmbeddedLtr(text)
    }
  }

  /**
   * Formats multi-paragraph plain/raw page text for RTL presentation, wrapping any Latin-only
   * paragraphs as well as embedded Latin spans in LRI/PDI isolates.
   */
  fun formatMultilineForDisplay(text: String): String {
    if (text.isBlank()) return text
    if (classifyBlockLanguage(text) == BlockLanguage.LATIN) return text

    return text.split('\n').joinToString("\n") { line ->
      val trimmed = line.trim()
      if (trimmed.isEmpty()) {
        line
      } else if (classifyBlockLanguage(trimmed) == BlockLanguage.LATIN) {
        "$LRI$line$PDI"
      } else {
        isolateEmbeddedLtr(line)
      }
    }
  }

  fun isolateEmbeddedLtr(text: String): String {
    if (text.isBlank() || !text.any { isLatinLetter(it) }) return text
    if (text.contains(LRI) && text.contains(PDI)) return text

    return embeddedLatinRunRegex.replace(text) { match ->
      val raw = match.value
      val trimmedCore = raw.trimEnd('.', ',', ':', ';', '-', '/')
      if (trimmedCore.isEmpty()) {
        raw
      } else {
        val trailing = raw.substring(trimmedCore.length)
        "$LRI$trimmedCore$PDI$trailing"
      }
    }
  }

  private fun isPersianOrArabicLetter(ch: Char): Boolean {
    val code = ch.code
    return code in 0x0621..0x064A ||
      code in 0x066E..0x06D5 ||
      code in 0x0750..0x077F ||
      code in 0x08A0..0x08FF ||
      code in 0xFB50..0xFDFF ||
      code in 0xFE70..0xFEFF
  }

  private fun isLatinLetter(ch: Char): Boolean {
    return ch in 'A'..'Z' || ch in 'a'..'z' || ch in '\u00C0'..'\u024F'
  }
}
