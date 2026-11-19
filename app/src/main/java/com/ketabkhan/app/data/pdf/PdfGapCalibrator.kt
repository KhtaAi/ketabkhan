package com.ketabkhan.app.data.pdf

import kotlin.math.abs

enum class SeparatorDecision(val separatorText: String) {
  JOIN(""),
  ZWNJ("\u200C"),
  SPACE(" "),
}

data class BoundaryDecision(
  val decision: SeparatorDecision,
  val gapPt: Float,
  val referenceSpaceWidthPt: Float,
  val reason: String,
)

/**
 * Pure Kotlin, test-friendly calibrator for PDF inter-glyph horizontal gaps and word separators.
 *
 * Enforces the Phase 6 conservative asymmetric rule:
 * - Bucket 1 (gap <= tightKerningLimit): Intra-word kerning -> JOIN ("").
 * - Bucket 2 (tightKerningLimit < gap < fullSpaceThreshold): ZWNJ only if an unmistakable Persian morphological affix pattern matches.
 * - Bucket 3 (gap >= fullSpaceThreshold): Full word space (" ").
 * - In ANY ambiguous or unconfirmed condition: defaults to FULL SPACE (" "), NEVER ZWNJ and NEVER JOIN.
 */
object PdfGapCalibrator {

  private const val ZWNJ_CHAR = '\u200C'

  data class CalibratedReference(
    val spaceWidthPt: Float,
    val source: SpaceReferenceSource,
  )

  fun computeReferenceSpaceWidth(
    fontSpaceWidths: List<Float>,
    interUnitGaps: List<Float>,
    fontSizes: List<Float>,
  ): CalibratedReference {
    val avgFont = if (fontSizes.isNotEmpty()) median(fontSizes).coerceAtLeast(6f) else 12f

    // 1. Check if font space widths are valid and reasonable
    val validFontSpaces = fontSpaceWidths.filter { it.isFinite() && it in (avgFont * 0.16f)..(avgFont * 0.68f) }
    if (validFontSpaces.size >= maxOf(2, (fontSpaceWidths.size * 0.15f).toInt())) {
      return CalibratedReference(
        spaceWidthPt = median(validFontSpaces),
        source = SpaceReferenceSource.FONT_SPACE_CHAR,
      )
    }

    // 2. Check for dominant large gap cluster on the page
    val candidateGaps = interUnitGaps.filter { it.isFinite() && it in (avgFont * 0.18f)..(avgFont * 0.72f) }
    if (candidateGaps.size >= 3) {
      return CalibratedReference(
        spaceWidthPt = median(candidateGaps),
        source = SpaceReferenceSource.DOMINANT_GAP_CLUSTER,
      )
    }

    // 3. Fallback: standard typographic space ratio of median font size
    return CalibratedReference(
      spaceWidthPt = avgFont * 0.28f,
      source = SpaceReferenceSource.FONT_SIZE_FRACTION,
    )
  }

  fun calculateRtlHorizontalGap(
    rightPredecessorMinX: Float,
    leftSuccessorMaxX: Float,
  ): Float {
    return rightPredecessorMinX - leftSuccessorMaxX
  }

  fun calculateLtrHorizontalGap(
    leftPredecessorMaxX: Float,
    rightSuccessorMinX: Float,
  ): Float {
    return rightSuccessorMinX - leftPredecessorMaxX
  }

  fun decideSeparator(
    prevText: String,
    currText: String,
    visualGapPt: Float,
    referenceSpaceWidthPt: Float,
    avgFontSizePt: Float,
    prevIsLtr: Boolean,
    currIsLtr: Boolean,
    currIsSingleTrailingLetter: Boolean = false,
  ): BoundaryDecision {
    if (currText == ZWNJ_CHAR.toString() || prevText == ZWNJ_CHAR.toString()) {
      return BoundaryDecision(SeparatorDecision.ZWNJ, visualGapPt, referenceSpaceWidthPt, "explicit_zwnj_unit")
    }

    val safeFont = avgFontSizePt.coerceAtLeast(6f)
    val safeSpaceRef = referenceSpaceWidthPt.coerceIn(safeFont * 0.16f, safeFont * 0.68f)

    // LTR/Digit to RTL/Letter transition or vice-versa
    if (prevIsLtr != currIsLtr) {
      val digitAdjacencyThreshold = safeFont * 0.08f
      return if (visualGapPt >= digitAdjacencyThreshold) {
        BoundaryDecision(SeparatorDecision.SPACE, visualGapPt, safeSpaceRef, "bidi_boundary_space")
      } else {
        BoundaryDecision(SeparatorDecision.SPACE, visualGapPt, safeSpaceRef, "bidi_boundary_glued_separation")
      }
    }

    val prevEndsWithLetter = prevText.lastOrNull()?.let { isPersianLetter(it) } == true
    val currStartsWithLetter = currText.firstOrNull()?.let { isPersianLetter(it) } == true
    val currIsPunct = currText.length == 1 && !currText[0].isLetterOrDigit() && currText[0] != ZWNJ_CHAR
    val prevIsStandaloneOpenQuote = prevText.lastOrNull() in setOf('«', '(', '[')

    if (prevIsStandaloneOpenQuote) {
      return BoundaryDecision(SeparatorDecision.JOIN, visualGapPt, safeSpaceRef, "after_open_quote")
    }

    val tightKerningLimit = minOf(safeSpaceRef * 0.38f, safeFont * 0.12f)
    val fullSpaceThreshold = safeSpaceRef * 0.55f

    if (currIsPunct && visualGapPt < fullSpaceThreshold) {
      return BoundaryDecision(SeparatorDecision.JOIN, visualGapPt, safeSpaceRef, "punctuation_join")
    }

    // Trailing detached final Persian letters (e.g. "پسر", "دین") with narrow gap
    if (currIsSingleTrailingLetter && visualGapPt < safeSpaceRef * 0.72f) {
      return BoundaryDecision(SeparatorDecision.JOIN, visualGapPt, safeSpaceRef, "detached_final_letter_join")
    }

    // Bucket 1: Tight negative or minimal intra-word kerning
    if (visualGapPt <= tightKerningLimit) {
      return BoundaryDecision(SeparatorDecision.JOIN, visualGapPt, safeSpaceRef, "tight_kerning")
    }

    // Bucket 3: Clear space threshold
    if (visualGapPt >= fullSpaceThreshold) {
      return BoundaryDecision(SeparatorDecision.SPACE, visualGapPt, safeSpaceRef, "full_space_gap")
    }

    // Bucket 2 (Intermediate zone): Only allow ZWNJ if unmistakable morphological affix pattern
    if (prevEndsWithLetter && currStartsWithLetter && isAffixPattern(prevText, currText)) {
      return BoundaryDecision(SeparatorDecision.ZWNJ, visualGapPt, safeSpaceRef, "morphological_affix_zwnj")
    }

    // Conservative Asymmetry: In any ambiguous state, default to FULL SPACE!
    return BoundaryDecision(SeparatorDecision.SPACE, visualGapPt, safeSpaceRef, "asymmetric_default_space")
  }

  private fun isAffixPattern(prevText: String, currText: String): Boolean {
    // 1. Prefix می / نمی
    if (prevText == "می" || prevText == "نمی" || prevText.endsWith("‌می") || prevText.endsWith("‌نمی")) {
      return true
    }
    // 2. Plural / superlative suffixes
    if (currText in setOf("ها", "های", "هایی", "هایمان", "هایتان", "هایشان", "تر", "ترین", "ترم", "ترش", "تری")) {
      return true
    }
    // 3. Silent-he endings before ezafe / enclitics
    if (prevText.endsWith('ه') && currText in setOf("ام", "ای", "است", "ایم", "اید", "اند", "ی", "ای")) {
      return true
    }
    // 4. High-frequency compound prefixes
    if (prevText in setOf("نوش", "خوش", "دل", "هم", "کم", "پر", "پاک", "بی", "پیش", "پس")) {
      return true
    }
    return false
  }

  fun isPersianLetter(ch: Char): Boolean {
    val code = ch.code
    return code in 0x0621..0x064A ||
      code in 0x066E..0x06D5 ||
      code in 0x0750..0x077F ||
      code in 0x08A0..0x08FF ||
      code in 0xFB50..0xFDFF ||
      code in 0xFE70..0xFEFF
  }

  fun median(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
  }
}
