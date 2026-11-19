package com.ketabkhan.app.data.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.StringWriter
import java.text.Normalizer
import kotlin.math.abs

data class ExtractedLine(
  val text: String,
  val rawText: String,
  val baselineY: Float,
  val minX: Float,
  val maxX: Float,
  val maxFontSize: Float,
  val medianFontSize: Float,
  val isBold: Boolean,
  val charCount: Int,
  val zone: PageZone,
  val pageWidth: Float,
  val pageHeight: Float,
  val hasGlyphInversionSignal: Boolean = false,
) {
  val width: Float
    get() = (maxX - minX).coerceAtLeast(0f)

  val centerX: Float
    get() = (minX + maxX) / 2f
}

data class ExtractedPageLayout(
  val pageNumber: Int,
  val lines: List<ExtractedLine>,
  val rawText: String,
  val pageWidth: Float,
  val pageHeight: Float,
)

/**
 * Custom line-based, RTL-aware PDF text stripper that builds lines directly from raw [TextPosition]
 * events received via [writeString] instead of relying on PDFBox LTR X-sorting or ICU Bidi reordering.
 *
 * Key invariants:
 * 1. Each [TextPosition] (including multi-character ligatures like "لا", "لآ", "الله") is treated as
 *    an atomic, indivisible [GlyphUnit]; characters inside a unit are NEVER reversed.
 * 2. Lines are clustered by vertical baseline (`yDirAdj`) and adaptive font-size tolerance.
 * 3. Dominant line direction is detected per line; RTL units are ordered descending by X while
 *    contiguous LTR/digit runs preserve ascending X visual order.
 * 4. Inter-unit horizontal gaps distinguish tight kerning (join), narrow ZWNJ gap (`\u200C`),
 *    and word spaces while protecting trailing single letters ("پسر", "دین") from detaching.
 */
class RtlLinePdfStripper : PDFTextStripper() {

  internal data class GlyphUnit(
    val unicode: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val fontSize: Float,
    val spaceWidth: Float,
    val isBold: Boolean,
  ) {
    val minX: Float get() = x
    val maxX: Float get() = x + width
    val centerX: Float get() = x + width / 2f
  }

  private val currentPageUnits = mutableListOf<GlyphUnit>()
  private var currentPageWidth: Float = 595f
  private var currentPageHeight: Float = 842f
  private val layoutsByPage = mutableMapOf<Int, ExtractedPageLayout>()

  init {
    sortByPosition = false
    suppressDuplicateOverlappingText = true
  }

  fun extractPageLayout(document: PDDocument, pageNumber: Int): ExtractedPageLayout {
    layoutsByPage.remove(pageNumber)
    startPage = pageNumber
    endPage = pageNumber
    val writer = StringWriter()
    writeText(document, writer)
    return layoutsByPage[pageNumber]
      ?: ExtractedPageLayout(
        pageNumber = pageNumber,
        lines = emptyList(),
        rawText = "",
        pageWidth = currentPageWidth,
        pageHeight = currentPageHeight,
      )
  }

  override fun startPage(page: PDPage?) {
    super.startPage(page)
    currentPageUnits.clear()
    val box = page?.cropBox ?: page?.mediaBox
    currentPageWidth = box?.width?.takeIf { it > 0f } ?: 595f
    currentPageHeight = box?.height?.takeIf { it > 0f } ?: 842f
  }

  override fun writeString(text: String?, textPositions: List<TextPosition>?) {
    if (textPositions.isNullOrEmpty()) return
    for (tp in textPositions) {
      val unit = toGlyphUnit(tp) ?: continue
      currentPageUnits.add(unit)
    }
  }

  override fun writeWordSeparator() {
    // Intentionally handled from exact horizontal geometry in endPage
  }

  override fun writeLineSeparator() {
    // Intentionally handled from vertical baseline clustering in endPage
  }

  override fun writeParagraphStart() {
    // No-op: DocumentStructureAnalyzer reconstructs paragraphs from vertical spacing
  }

  override fun writeParagraphEnd() {
    // No-op
  }

  override fun endPage(page: PDPage?) {
    val lines = assembleLines(
      units = currentPageUnits,
      pageWidth = currentPageWidth,
      pageHeight = currentPageHeight,
    )
    val rawText = lines.joinToString("\n") { it.rawText }
    layoutsByPage[currentPageNo] = ExtractedPageLayout(
      pageNumber = currentPageNo,
      lines = lines,
      rawText = rawText,
      pageWidth = currentPageWidth,
      pageHeight = currentPageHeight,
    )
    if (rawText.isNotEmpty()) {
      output.write(rawText)
    }
    currentPageUnits.clear()
    super.endPage(page)
  }

  companion object {

    private const val ZWNJ = '\u200C'

    private fun toGlyphUnit(tp: TextPosition): GlyphUnit? {
      val raw = tp.unicode ?: return null
      if (raw.isEmpty()) return null

      // NFKC resolves presentation forms like \uFEFB -> "لا" and \uFEF5 -> "لآ" without reversing characters
      val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .replace('\u064A', '\u06CC')
        .replace('\u0649', '\u06CC')
        .replace('\u0643', '\u06A9')
        .replace("\u0640", "")
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u200B\\u200E\\u200F\\uFEFF\\u00AD]"), "")

      if (normalized.isEmpty()) return null
      if (normalized.all { it == ' ' || it == '\t' || it == '\n' || it == '\r' }) return null

      val fontSize = maxOf(tp.fontSizeInPt, tp.heightDir, 1f)
      val width = tp.widthDirAdj.takeIf { it.isFinite() && it >= 0f } ?: 0f
      val x = tp.xDirAdj.takeIf { it.isFinite() } ?: 0f
      val y = tp.yDirAdj.takeIf { it.isFinite() } ?: 0f
      val spaceW = tp.widthOfSpace.takeIf { it.isFinite() && it > 0f } ?: (fontSize * 0.28f)
      val fontName = tp.font?.name.orEmpty()
      val descriptor = runCatching { tp.font?.fontDescriptor }.getOrNull()
      val isBold = isBoldFont(
        fontName = fontName,
        isForceBold = descriptor?.isForceBold == true,
        fontWeight = descriptor?.fontWeight ?: 0f,
      )

      return GlyphUnit(
        unicode = normalized,
        x = x,
        y = y,
        width = width,
        height = tp.heightDir.takeIf { it.isFinite() && it > 0f } ?: fontSize,
        fontSize = fontSize,
        spaceWidth = spaceW,
        isBold = isBold,
      )
    }

    internal fun assembleLines(
      units: List<GlyphUnit>,
      pageWidth: Float,
      pageHeight: Float,
    ): List<ExtractedLine> {
      if (units.isEmpty()) return emptyList()

      val sortedByY = units.sortedBy { it.y }
      val lineClusters = mutableListOf<MutableList<GlyphUnit>>()

      for (unit in sortedByY) {
        val lastCluster = lineClusters.lastOrNull()
        if (lastCluster == null) {
          lineClusters.add(mutableListOf(unit))
        } else {
          val anchorY = medianFloat(lastCluster.map { it.y })
          val anchorFont = medianFloat(lastCluster.map { it.fontSize })
          val tolerance = maxOf(2.5f, minOf(anchorFont, unit.fontSize) * 0.44f)
          if (abs(unit.y - anchorY) <= tolerance) {
            lastCluster.add(unit)
          } else {
            lineClusters.add(mutableListOf(unit))
          }
        }
      }

      val sortedClusters = lineClusters.sortedBy { cluster -> medianFloat(cluster.map { it.y }) }
      return sortedClusters.mapNotNull { cluster ->
        buildExtractedLine(
          cluster = cluster,
          pageWidth = pageWidth,
          pageHeight = pageHeight,
        )
      }
    }

    private data class VisualSegment(
      val text: String,
      val minX: Float,
      val maxX: Float,
      val fontSize: Float,
      val spaceWidth: Float,
      val isLtrSegment: Boolean,
    )

    private fun buildExtractedLine(
      cluster: List<GlyphUnit>,
      pageWidth: Float,
      pageHeight: Float,
    ): ExtractedLine? {
      if (cluster.isEmpty()) return null

      var rtlStrongCount = 0
      var ltrStrongCount = 0
      for (unit in cluster) {
        for (ch in unit.unicode) {
          when {
            isRtlLetter(ch) -> rtlStrongCount++
            isAsciiLatinLetter(ch) -> ltrStrongCount++
          }
        }
      }

      val isRtlDominant = rtlStrongCount > 0 && rtlStrongCount >= ltrStrongCount

      val orderedSegments = if (isRtlDominant) {
        buildRtlSegments(cluster)
      } else {
        cluster.sortedBy { it.x }.map { unit ->
          VisualSegment(
            text = unit.unicode,
            minX = unit.minX,
            maxX = unit.maxX,
            fontSize = unit.fontSize,
            spaceWidth = unit.spaceWidth,
            isLtrSegment = true,
          )
        }
      }

      if (orderedSegments.isEmpty()) return null

      val rawLineText = joinSegmentsWithGapInference(
        segments = orderedSegments,
        isRtlDominant = isRtlDominant,
      ).trim()

      if (rawLineText.isEmpty()) return null

      val baselineY = medianFloat(cluster.map { it.y })
      val minX = cluster.minOf { it.minX }
      val maxX = cluster.maxOf { it.maxX }
      val maxFontSize = cluster.maxOf { it.fontSize }
      val medianFontSize = medianFloat(cluster.map { it.fontSize })
      val boldChars = cluster.filter { it.isBold }.sumOf { it.unicode.length }
      val totalChars = cluster.sumOf { it.unicode.length }.coerceAtLeast(1)
      val isBold = boldChars.toFloat() / totalChars.toFloat() >= 0.45f
      val zone = classifyZone(baselineY = baselineY, pageHeight = pageHeight)

      return ExtractedLine(
        text = rawLineText,
        rawText = rawLineText,
        baselineY = baselineY,
        minX = minX,
        maxX = maxX,
        maxFontSize = maxFontSize,
        medianFontSize = medianFontSize,
        isBold = isBold,
        charCount = rawLineText.length,
        zone = zone,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
      )
    }

    private fun buildRtlSegments(cluster: List<GlyphUnit>): List<VisualSegment> {
      // Order RTL units strictly from rightmost to leftmost without breaking atomic ligatures
      val rtlSorted = cluster.sortedWith(
        compareByDescending<GlyphUnit> { it.centerX }
          .thenByDescending { it.maxX }
      )

      val segments = mutableListOf<VisualSegment>()
      var idx = 0
      while (idx < rtlSorted.size) {
        val current = rtlSorted[idx]
        if (isStrongLtrOrDigitUnit(current.unicode)) {
          val run = mutableListOf(current)
          var j = idx + 1
          while (j < rtlSorted.size) {
            val next = rtlSorted[j]
            val gap = run.last().minX - next.maxX
            val maxRunGap = maxOf(current.fontSize * 0.6f, 6f)
            val isFlankedSeparator = j + 1 < rtlSorted.size &&
              isLtrRunContinuationUnit(next.unicode, run.last().unicode) &&
              isStrongLtrOrDigitUnit(rtlSorted[j + 1].unicode) &&
              (next.minX - rtlSorted[j + 1].maxX) <= maxRunGap
            if ((isStrongLtrOrDigitUnit(next.unicode) || isFlankedSeparator) && gap <= maxRunGap) {
              run.add(next)
              j++
            } else {
              break
            }
          }
          // Order the LTR/digit sub-run ascending by X so "1299" or "PDF" stays left-to-right;
          // internal chars of any multi-char unit stay untouched.
          val ltrOrderedRun = run.sortedBy { it.x }
          val joinedRun = buildString {
            for (k in ltrOrderedRun.indices) {
              val part = ltrOrderedRun[k]
              if (k > 0) {
                val prevPart = ltrOrderedRun[k - 1]
                val intraGap = part.minX - prevPart.maxX
                val spaceRef = medianFloat(listOf(prevPart.spaceWidth, part.spaceWidth))
                  .coerceIn(prevPart.fontSize * 0.18f, prevPart.fontSize * 0.65f)
                if (intraGap >= maxOf(spaceRef * 0.68f, prevPart.fontSize * 0.22f)) {
                  append(' ')
                }
              }
              append(part.unicode)
            }
          }
          segments.add(
            VisualSegment(
              text = joinedRun,
              minX = run.minOf { it.minX },
              maxX = run.maxOf { it.maxX },
              fontSize = medianFloat(run.map { it.fontSize }),
              spaceWidth = medianFloat(run.map { it.spaceWidth }),
              isLtrSegment = true,
            )
          )
          idx = j
        } else {
          segments.add(
            VisualSegment(
              text = current.unicode,
              minX = current.minX,
              maxX = current.maxX,
              fontSize = current.fontSize,
              spaceWidth = current.spaceWidth,
              isLtrSegment = false,
            )
          )
          idx++
        }
      }
      return segments
    }

    private fun joinSegmentsWithGapInference(
      segments: List<VisualSegment>,
      isRtlDominant: Boolean,
    ): String {
      if (segments.isEmpty()) return ""
      val sb = StringBuilder()

      for (i in segments.indices) {
        val curr = segments[i]
        if (i == 0) {
          sb.append(curr.text)
          continue
        }

        val prev = segments[i - 1]
        if (curr.text == ZWNJ.toString()) {
          if (sb.isNotEmpty() && sb.last() != ZWNJ && sb.last() != ' ') {
            sb.append(ZWNJ)
          }
          continue
        }
        if (prev.text == ZWNJ.toString()) {
          sb.append(curr.text)
          continue
        }

        val visualGap = if (isRtlDominant) {
          prev.minX - curr.maxX
        } else {
          curr.minX - prev.maxX
        }

        val avgFont = ((prev.fontSize + curr.fontSize) / 2f).coerceAtLeast(1f)
        val rawSpaceMedian = ((prev.spaceWidth + curr.spaceWidth) / 2f)
        val effectiveSpaceRef = if (rawSpaceMedian in (avgFont * 0.18f)..(avgFont * 0.65f)) {
          rawSpaceMedian
        } else {
          avgFont * 0.28f
        }

        val tightKerningLimit = minOf(effectiveSpaceRef * 0.44f, avgFont * 0.135f)
        val wordSpaceLimit = maxOf(effectiveSpaceRef * 0.68f, avgFont * 0.20f)

        val prevEndsWithLetter = prev.text.lastOrNull()?.let { isPersianOrArabicLetter(it) } == true
        val currStartsWithLetter = curr.text.firstOrNull()?.let { isPersianOrArabicLetter(it) } == true
        val currIsStandalonePunct = curr.text.length == 1 && !curr.text[0].isLetterOrDigit() && curr.text[0] != ZWNJ
        val prevIsStandaloneOpenQuote = prev.text.lastOrNull() in setOf('«', '(', '[')

        // Check if curr is a single trailing Persian letter at a word boundary (protect "پسر", "دین")
        val nextGap = if (i + 1 < segments.size) {
          val nextSeg = segments[i + 1]
          if (isRtlDominant) curr.minX - nextSeg.maxX else nextSeg.minX - curr.maxX
        } else {
          Float.MAX_VALUE
        }
        val currIsSingleTrailingLetter = curr.text.length == 1 &&
          currStartsWithLetter &&
          curr.text != "و" &&
          prevEndsWithLetter &&
          nextGap >= wordSpaceLimit

        val separator = when {
          prevIsStandaloneOpenQuote -> ""
          currIsStandalonePunct && visualGap < wordSpaceLimit -> ""
          prev.isLtrSegment != curr.isLtrSegment -> {
            // Digit-to-letter adjacency: insert space if even a tiny gap or glued
            if (visualGap >= avgFont * 0.08f) " " else ""
          }
          visualGap <= tightKerningLimit -> ""
          currIsSingleTrailingLetter && visualGap < effectiveSpaceRef * 0.85f -> ""
          visualGap < wordSpaceLimit && prevEndsWithLetter && currStartsWithLetter -> ZWNJ.toString()
          else -> " "
        }

        sb.append(separator).append(curr.text)
      }

      return sb.toString()
    }

    private fun isBoldFont(
      fontName: String,
      isForceBold: Boolean,
      fontWeight: Float,
    ): Boolean {
      if (isForceBold || fontWeight >= 600f) return true
      val lower = fontName.lowercase()
      return lower.contains("bold") ||
        lower.contains("heavy") ||
        lower.contains("black") ||
        lower.contains("demi") ||
        lower.contains("semibold") ||
        lower.contains("titr") ||
        lower.endsWith("-bd") ||
        lower.endsWith("-b") ||
        lower.contains("b zar") ||
        lower.contains("bnazaninb") ||
        lower.contains("bmitrab") ||
        lower.contains("byekanb") ||
        lower.contains("lotusb")
    }

    private fun classifyZone(baselineY: Float, pageHeight: Float): PageZone {
      val safeH = pageHeight.takeIf { it > 0f } ?: 842f
      return when {
        baselineY <= safeH * 0.16f -> PageZone.TOP
        baselineY >= safeH * 0.84f -> PageZone.BOTTOM
        else -> PageZone.MIDDLE
      }
    }

    private fun isStrongLtrOrDigitUnit(unicode: String): Boolean {
      var hasStrongLtrOrDigit = false
      for (ch in unicode) {
        if (isRtlLetter(ch)) return false
        if (isAsciiLatinLetter(ch) || isAnyDigit(ch)) {
          hasStrongLtrOrDigit = true
        } else if (ch !in setOf('.', ',', '/', '-', '_', ':')) {
          return false
        }
      }
      return hasStrongLtrOrDigit
    }

    private fun isLtrRunContinuationUnit(unicode: String, prevUnicode: String): Boolean {
      if (isStrongLtrOrDigitUnit(unicode)) return true
      return unicode.length == 1 &&
        unicode[0] in setOf('.', '/', '-', ':') &&
        prevUnicode.any { isAsciiLatinLetter(it) || isAnyDigit(it) }
    }

    private fun isRtlLetter(ch: Char): Boolean {
      val code = ch.code
      return code in 0x0621..0x064A ||
        code in 0x066E..0x06D5 ||
        code in 0x0750..0x077F ||
        code in 0x08A0..0x08FF ||
        code in 0xFB50..0xFDFF ||
        code in 0xFE70..0xFEFF
    }

    private fun isPersianOrArabicLetter(ch: Char): Boolean = isRtlLetter(ch)

    private fun isAsciiLatinLetter(ch: Char): Boolean = ch in 'A'..'Z' || ch in 'a'..'z'

    private fun isAnyDigit(ch: Char): Boolean = ch in '0'..'9' || ch in '۰'..'۹' || ch in '\u0660'..'\u0669'

    internal fun medianFloat(values: List<Float>): Float {
      if (values.isEmpty()) return 0f
      val sorted = values.sorted()
      val mid = sorted.size / 2
      return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }
  }
}
