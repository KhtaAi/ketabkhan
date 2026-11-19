package com.ketabkhan.app.domain.text

data class LigatureRepairResult(
  val text: String,
  val ligatureFixesCount: Int,
  val detachedFinalLetterFixesCount: Int,
)

/**
 * High-precision integrity restorer for Persian "لا" ligatures and detached trailing letters.
 *
 * Design contract:
 * 1. Primary extraction in [RtlLinePdfStripper] treats each PDF `TextPosition` (including "لا")
 *    as an atomic unit, so faithful extractions should yield 0 ligature repairs here.
 * 2. Never blindly replace "ال" -> "لا" globally, because Arabic definite prefixes ("ال...") and
 *    words like "الله"، "الف"، "الان"، "الم"، "سالم"، "مثال" are legitimate Persian/Arabic tokens.
 * 3. Repairs only occur under explicit glyph-inversion metadata or strictly unambiguous tokens.
 */
object PersianLigatureRestorer {

  private val protectedAlTokens = setOf(
    "الله", "الف", "الان", "الم", "البته", "الگو", "الهام", "الماس", "التزام",
    "الزام", "الزاما", "الکتریکی", "الکترونیک", "البرز", "الوند", "المپیک",
    "سالم", "حلال", "جلال", "کمال", "جمال", "خیال", "مثال", "امثال", "احوال",
    "اموال", "اطفال", "اقوال", "اشکال", "استقلال", "استدلال", "انحلال", "اخلال",
    "انتقال", "استقبال", "ائتلاف", "حوالی", "توالی", "محال", "زوال", "سوال",
    "شال", "سال", "مال", "حال", "فال", "قال", "بال", "دال", "نال", "خال",
    "اسالم", "تالش",
  )

  private val unambiguousInvertedLigatureTokens = mapOf(
    "میالنی" to "میلانی",
    "آالشت" to "آلاشت",
    "االشت" to "آلاشت",
    "اصالح" to "اصلاح",
    "اصالحی" to "اصلاحی",
    "اصالحات" to "اصلاحات",
    "انقالب" to "انقلاب",
    "انقالبی" to "انقلابی",
    "انقالبیون" to "انقلابیون",
    "اخالق" to "اخلاق",
    "اخالقی" to "اخلاقی",
    "اطالع" to "اطلاع",
    "اطالعات" to "اطلاعات",
    "اطالعاتی" to "اطلاعاتی",
    "اعالم" to "اعلام",
    "اعالمیه" to "اعلامیه",
    "استعالم" to "استعلام",
    "اقالم" to "اقلام",
    "اسالمی" to "اسلامی",
    "بالغت" to "بلاغت",
    "خالفت" to "خلافت",
    "خالصه" to "خلاصه",
    "مالحظه" to "ملاحظه",
    "مالحظات" to "ملاحظات",
    "سالطین" to "سلاطین",
    "عالج" to "علاج",
    "معالجه" to "معالجه",
    "تالیف" to "تالیف", // legitimate; not inverted!
    "سالست" to "سلاست",
    "بالفاصله" to "بلافاصله",
    "ابالغ" to "ابلاغ",
    "امالک" to "املاک",
    "ممالکت" to "مملکت",
  ).filter { it.key != it.value }

  // Words that can safely invert internal "ال" ONLY when glyph metadata explicitly flagged inversion
  private val glyphMetadataExtraInversions = mapOf(
    "سالم" to "سلام",
    "تالش" to "تلاش",
    "طالب" to "طلاب",
    "دالل" to "دلال",
  )

  private val persianTokenRegex = Regex("[\\u0621-\\u06D5\\u200C]+")
  private val detachedSingleFinalLetterRegex = Regex(
    "([\\u0621-\\u06D5]{2,})\\s+([بپتثجچحخدذرزژسشصضطظعغفقکگلمنهی])(?![\\u0621-\\u06D5\\u200C])"
  )

  private val validStandaloneSingleLetters = setOf("و")

  fun restore(
    text: String,
    hasGlyphInversionSignal: Boolean = false,
  ): LigatureRepairResult {
    if (text.isBlank()) {
      return LigatureRepairResult(text = text, ligatureFixesCount = 0, detachedFinalLetterFixesCount = 0)
    }

    var ligatureFixes = 0
    val replaced = persianTokenRegex.replace(text) { match ->
      val token = match.value
      if (token in protectedAlTokens && !hasGlyphInversionSignal) {
        return@replace token
      }
      if (token.startsWith("ال") && token !in unambiguousInvertedLigatureTokens) {
        return@replace token
      }

      val mapped = unambiguousInvertedLigatureTokens[token]
        ?: if (hasGlyphInversionSignal) glyphMetadataExtraInversions[token] else null

      if (mapped != null && mapped != token) {
        ligatureFixes++
        mapped
      } else {
        token
      }
    }

    return LigatureRepairResult(
      text = replaced,
      ligatureFixesCount = ligatureFixes,
      detachedFinalLetterFixesCount = 0,
    )
  }

  /**
   * Repairs a single trailing detached Persian letter when horizontal spacing metadata confirms
   * the gap is narrower than a normal word space (e.g. "پس ر" -> "پسر", "دی ن" -> "دین").
   */
  fun repairDetachedTrailingLetterWithGap(
    leftWord: String,
    rightSingleLetter: String,
    horizontalGapPt: Float,
    referenceSpaceWidthPt: Float,
  ): Boolean {
    if (rightSingleLetter.length != 1) return false
    val ch = rightSingleLetter[0]
    if (!isPersianLetter(ch)) return false
    if (rightSingleLetter in validStandaloneSingleLetters) return false
    if (leftWord.length < 2 || !leftWord.all { isPersianLetter(it) || it == '\u200C' }) return false

    // Only join if visual horizontal gap is significantly narrower than a true word space
    return horizontalGapPt < referenceSpaceWidthPt * 0.68f
  }

  /**
   * Fallback text-only repair only for unmistakable non-word detached single trailing letters
   * (never touches standalone "و" or real short suffixes).
   */
  fun repairUnmistakableDetachedTrailingChar(text: String): LigatureRepairResult {
    if (text.isBlank()) {
      return LigatureRepairResult(text, 0, 0)
    }
    var detachedFixes = 0
    val repaired = detachedSingleFinalLetterRegex.replace(text) { match ->
      val stem = match.groupValues[1]
      val trailing = match.groupValues[2]
      if (trailing in validStandaloneSingleLetters) {
        match.value
      } else if (stem.length >= 2 && isKnownSplitFinalCandidate(stem + trailing)) {
        detachedFixes++
        stem + trailing
      } else {
        match.value
      }
    }
    return LigatureRepairResult(
      text = repaired,
      ligatureFixesCount = 0,
      detachedFinalLetterFixesCount = detachedFixes,
    )
  }

  private fun isKnownSplitFinalCandidate(combined: String): Boolean {
    return combined in knownSplitWords
  }

  private val knownSplitWords = setOf(
    "پسر", "دین", "تحرک", "نمایه", "پدر", "مادر", "برادر", "خواهر",
    "کتاب", "قلم", "مرد", "زن", "شهر", "روز", "شب", "خانه", "نامه",
    "سخن", "کار", "دست", "چشم", "دل", "سر", "پا", "جان", "نام", "زمان",
    "ایران", "تهران", "تاریخ", "فرهنگ", "ادبیات", "دانش", "علم", "هنر",
    "بخش", "فصل", "صفحه", "سطر", "کلمه", "جمله", "پاورقی", "فهرست",
  )

  private fun isPersianLetter(ch: Char): Boolean {
    val code = ch.code
    return code in 0x0621..0x064A || code in 0x066E..0x06D5
  }
}
