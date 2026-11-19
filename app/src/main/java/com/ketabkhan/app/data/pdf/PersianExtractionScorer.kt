package com.ketabkhan.app.data.pdf

import java.text.Normalizer

data class ExtractionCandidateScore(
  val totalScore: Float,
  val persianLetterRatio: Float,
  val functionWordCount: Int,
  val wordCount: Int,
  val fullSpaceCount: Int,
  val zwnjCount: Int,
  val brokenPrefixCount: Int,
  val guillemetIssueCount: Int,
  val digitAdjacencyCount: Int,
  val corruptedCharCount: Int,
  val ligatureDefectCount: Int = 0,
  val detachedFinalLetterCount: Int = 0,
  val abnormallyLongTokenCount: Int = 0,
  val gluedFunctionWordCount: Int = 0,
  val hasCorruptionSignals: Boolean = false,
)

object PersianExtractionScorer {

  private const val FALLBACK_SCORE_THRESHOLD = 45f

  private val independentFunctionTokens = setOf(
    "از", "به", "که", "را", "در", "و", "است", "بود", "با", "برای", "این", "آن", "تا", "بر", "هم", "نیز",
  )

  private val commonGluedFunctionPrefixRegex = Regex(
    "(?<![\\u0621-\\u06D5])(?:در|برای|را|به|هنوز|بیستون|هر|این|آن)(?:آن|این|اجرای|اجباری|رغم|روزه|شناسنامه|بسیاری|[\\u0621-\\u06D5]{4,})(?![\\u0621-\\u06D5])"
  )

  private val brokenShortTokens = setOf("م", "نم", "نو")
  private val brokenMiSplitRegex = Regex("(?<![\\u0621-\\u06D5])(?:نم|م)\\s+ی[\\u0621-\\u06D5]{2,}")
  private val shiftedAlifMaddaRegex = Regex("[\\u0621-\\u06D5]{2,}\\s+[بپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی]آ[\\u0621-\\u06D5]{2,}")
  private val digitGluedToLetterRegex = Regex(
    "(?:[0-9۰-۹٠-٩][\\u0621-\\u064A\\u066E-\\u06D5]|[\\u0621-\\u064A\\u066E-\\u06D5][0-9۰-۹٠-٩])"
  )
  private val suspectedInvertedLigatureRegex = Regex(
    "(?:میالنی|آالشت|االشت|اصالح|انقالب|اخالق|اطالع|اعالم|اقالم|اسالمی|مالحظه|خالصه)"
  )
  private val detachedFinalSingleLetterRegex = Regex(
    "[\\u0621-\\u06D5]{2,}\\s+[بپتثجچحخدذرزژسشصضطظعغفقکگلمنهی](?![\\u0621-\\u06D5\\u200C])"
  )

  fun scoreCandidate(rawText: String): ExtractionCandidateScore {
    if (rawText.isBlank()) {
      return ExtractionCandidateScore(
        totalScore = 0f,
        persianLetterRatio = 0f,
        functionWordCount = 0,
        wordCount = 0,
        fullSpaceCount = 0,
        zwnjCount = 0,
        brokenPrefixCount = 0,
        guillemetIssueCount = 0,
        digitAdjacencyCount = 0,
        corruptedCharCount = 0,
        ligatureDefectCount = 0,
        detachedFinalLetterCount = 0,
        abnormallyLongTokenCount = 0,
        gluedFunctionWordCount = 0,
        hasCorruptionSignals = false,
      )
    }

    val normalized = Normalizer.normalize(rawText, Normalizer.Form.NFKC)
      .replace('\u064A', '\u06CC')
      .replace('\u0649', '\u06CC')
      .replace('\u0643', '\u06A9')
      .replace("\u0640", "")

    var totalLetters = 0
    var persianLetters = 0
    var totalNonWhitespace = 0
    var corruptedCount = 0
    var zwnjCount = 0
    var fullSpaceCount = 0

    for (ch in normalized) {
      if (ch == '\u200C') {
        zwnjCount++
        continue
      }
      if (ch == ' ' || ch == '\t') {
        fullSpaceCount++
        continue
      }
      if (ch.isWhitespace()) continue
      totalNonWhitespace++
      val code = ch.code
      val isBad = ch == '\uFFFD' ||
        code in 0xE000..0xF8FF ||
        code in 0x00C0..0x00FF ||
        Character.getType(ch) == Character.CONTROL.toInt() ||
        Character.getType(ch) == Character.PRIVATE_USE.toInt() ||
        Character.getType(ch) == Character.UNASSIGNED.toInt()
      if (isBad) {
        corruptedCount++
      }
      if (ch.isLetter()) {
        totalLetters++
        if (isPersianOrArabic(ch)) {
          persianLetters++
        }
      }
    }

    val persianRatio = if (totalLetters > 0) persianLetters.toFloat() / totalLetters.toFloat() else 0f
    val rawTokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
    val strippedTokens = rawTokens.map { tok ->
      tok.trim { !it.isLetterOrDigit() && it != '\u200C' }
    }.filter { it.isNotEmpty() }

    val functionWordCount = strippedTokens.count { it in independentFunctionTokens }
    val brokenShortCount = strippedTokens.count { it in brokenShortTokens }
    val brokenPatternCount = brokenMiSplitRegex.findAll(normalized).count() +
      shiftedAlifMaddaRegex.findAll(normalized).count()
    val brokenPrefixTotal = brokenShortCount + brokenPatternCount

    val guillemetIssues = countGuillemetIssues(normalized)
    val digitAdjacencyCount = digitGluedToLetterRegex.findAll(normalized).count()
    val ligatureDefectCount = suspectedInvertedLigatureRegex.findAll(normalized).count()
    val detachedFinalLetterCount = detachedFinalSingleLetterRegex.findAll(normalized).count()

    // Phase 6 Spacing Defect Detections:
    // 1. Abnormally long tokens without spaces (> 16 chars or >= 12 with glued function words)
    var abnormallyLongTokens = 0
    for (token in strippedTokens) {
      val pureChars = token.filter { it != '\u200C' }
      if (pureChars.length >= 17) {
        abnormallyLongTokens++
      } else if (pureChars.length >= 12 && commonGluedFunctionPrefixRegex.containsMatchIn(pureChars)) {
        abnormallyLongTokens++
      }
    }

    // 2. Glued function word count (e.g. درآن, برایاجرای, وبسیاری, بهرغم)
    val gluedFunctionWords = commonGluedFunctionPrefixRegex.findAll(normalized).count()

    val wordCount = strippedTokens.size
    val functionDensity = if (wordCount > 0) {
      (functionWordCount.toFloat() / wordCount.toFloat()).coerceAtMost(0.40f) / 0.40f
    } else {
      0f
    }

    var score = 0f
    // 1. Persian letter ratio (0..35 pts)
    score += persianRatio * 35f
    // 2. Presence of independent function words (0..30 pts)
    score += (functionDensity * 20f) + minOf(functionWordCount, 5) * 2f
    // 3. Intact ligatures vs inverted
    if (normalized.contains("لا") || normalized.contains("لآ")) {
      score += 6f
    }
    score -= ligatureDefectCount * 18f
    // 4. Detached final letters ("پس ر", "دی ن")
    score -= detachedFinalLetterCount * 14f
    // 5. Broken prefixes ("م یزد")
    score -= brokenShortCount * 12f + brokenPatternCount * 16f
    // 6. Guillemet and digit adjacency
    score -= guillemetIssues * 10f
    score -= digitAdjacencyCount * 6f

    // 7. Phase 6 Penalties for Over-Glued Text & Space Collapse:
    score -= abnormallyLongTokens * 24f
    score -= gluedFunctionWords * 18f

    // Ratio of ZWNJ to full space check (prose with > 40% ZWNJs is heavily over-collapsed)
    if (fullSpaceCount > 0) {
      val zwnjRatio = zwnjCount.toFloat() / fullSpaceCount.toFloat()
      if (zwnjRatio > 0.42f) {
        score -= (zwnjRatio - 0.42f) * 45f
      }
    } else if (wordCount >= 3 && zwnjCount >= 2) {
      // Multiple words without a single space!
      score -= 35f
    }

    // 8. Natural spacing and word length reward
    val avgWordLen = if (wordCount > 0) strippedTokens.sumOf { it.length }.toFloat() / wordCount.toFloat() else 0f
    if (avgWordLen in 3.4f..6.8f && abnormallyLongTokens == 0 && gluedFunctionWords == 0 && fullSpaceCount >= 2) {
      score += 14f
    }

    // 9. Character corruption
    val corruptedRatio = if (totalNonWhitespace > 0) corruptedCount.toFloat() / totalNonWhitespace.toFloat() else 0f
    score -= corruptedRatio * 85f + corruptedCount * 3f

    val hasCorruptionSignals = brokenPrefixTotal > 0 ||
      ligatureDefectCount > 0 ||
      detachedFinalLetterCount > 0 ||
      abnormallyLongTokens > 0 ||
      gluedFunctionWords > 0 ||
      guillemetIssues > 0 ||
      digitAdjacencyCount > 0 ||
      corruptedCount > 0 ||
      (wordCount >= 6 && functionWordCount == 0 && persianRatio > 0.4f) ||
      (totalLetters >= 12 && persianRatio < 0.5f)

    return ExtractionCandidateScore(
      totalScore = score,
      persianLetterRatio = persianRatio,
      functionWordCount = functionWordCount,
      wordCount = wordCount,
      fullSpaceCount = fullSpaceCount,
      zwnjCount = zwnjCount,
      brokenPrefixCount = brokenPrefixTotal,
      guillemetIssueCount = guillemetIssues,
      digitAdjacencyCount = digitAdjacencyCount,
      corruptedCharCount = corruptedCount,
      ligatureDefectCount = ligatureDefectCount,
      detachedFinalLetterCount = detachedFinalLetterCount,
      abnormallyLongTokenCount = abnormallyLongTokens,
      gluedFunctionWordCount = gluedFunctionWords,
      hasCorruptionSignals = hasCorruptionSignals,
    )
  }

  fun shouldEvaluateFallback(primaryScore: ExtractionCandidateScore, rawText: String): Boolean {
    if (rawText.isBlank()) return false
    return primaryScore.totalScore < FALLBACK_SCORE_THRESHOLD || primaryScore.hasCorruptionSignals
  }

  private fun countGuillemetIssues(text: String): Int {
    if (!text.contains('«') && !text.contains('»')) return 0
    var openCount = 0
    var closeCount = 0
    var depth = 0
    var inverted = 0
    for (ch in text) {
      when (ch) {
        '«' -> {
          openCount++
          depth++
        }
        '»' -> {
          closeCount++
          if (depth > 0) {
            depth--
          } else {
            inverted++
          }
        }
      }
    }
    val imbalance = kotlin.math.abs(openCount - closeCount)
    return imbalance + inverted
  }

  private fun isPersianOrArabic(ch: Char): Boolean {
    val code = ch.code
    return code in 0x0621..0x064A ||
      code in 0x066E..0x06D5 ||
      code in 0x0750..0x077F ||
      code in 0x08A0..0x08FF ||
      code in 0xFB50..0xFDFF ||
      code in 0xFE70..0xFEFF
  }
}
