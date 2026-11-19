package com.ketabkhan.app.data.pdf

import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.domain.text.PersianTextCleaner
import kotlin.math.abs

data class AnalyzedPageResult(
  val pageNumber: Int,
  val blocks: List<DocumentBlock>,
  val flatCleanedText: String,
  val isEmpty: Boolean,
  val ligatureFixesCount: Int,
  val detachedLetterFixesCount: Int,
)

/**
 * Analyzes per-page lines with typography and spatial metadata into labeled structural [DocumentBlock]s.
 * Headers, footers, page numbers, and footnotes are explicitly labeled and retained in [blocks]
 * rather than silently discarded, while legacy flat `cleanedText` remains available for compatibility.
 */
object DocumentStructureAnalyzer {

  private data class WorkingLine(
    val indexInPage: Int,
    val source: ExtractedLine,
    val cleanedText: String,
    val normalizedForHf: String,
    val ligatureFixes: Int,
    val detachedFixes: Int,
  )

  private data class TocParsedEntry(
    val title: String,
    val referencePage: Int,
  )

  private val tocDotLeaderRegex = Regex(
    "^(.+?)\\s*(?:[.…·•\\-–—_]{2,}|\\s{2,})\\s*([0-9۰-۹٠-٩]{1,4})\\s*$"
  )
  private val tocSimpleTrailingPageRegex = Regex(
    "^(.+?[\\u0621-\\u06D5\\p{L})»\\]])\\s+([0-9۰-۹٠-٩]{1,4})\\s*$"
  )
  private val chapterHeadingPattern = Regex(
    "^(?:فصل|بخش|باب|گفتار|مقدمه|پیشگفتار|پیش‌گفتار|دیباچه|نتیجه‌گیری|خاتمه|فهرست(?:\\s+مطالب)?)\\b.*"
  )
  private val footnoteStartRegex = Regex(
    "^(?:\\[[0-9۰-۹]+\\]|\\([0-9۰-۹]+\\)|[0-9۰-۹]+[.)\\-:]|[*†‡]+|(?:پاورقی|توضیح|حاشیه)[:.\\s]).+"
  )
  private val referenceMarkOnlyRegex = Regex(
    "^(?:\\[[0-9۰-۹]+\\]|\\([0-9۰-۹]+\\)|[*†‡]+|[0-9۰-۹]+[.)])$"
  )
  private val tocPageKeywordRegex = Regex("(?:فهرست|مندرجات)")
  private val sentenceEndChars = setOf('.', '؟', '?', '!', '…', '»', '"')

  fun analyzeBook(
    pageLayouts: List<ExtractedPageLayout>,
  ): List<AnalyzedPageResult> {
    if (pageLayouts.isEmpty()) return emptyList()

    // 1. Clean & deduplicate lines per page while preserving spatial & typography metadata
    val preparedPages: List<List<WorkingLine>> = pageLayouts.map { layout ->
      preparePageLines(layout)
    }

    // 2. Tag standalone page numbers & cross-page repeating headers/footers (never drop from blocks!)
    val nonMetadataPages = preparedPages.map { lines ->
      lines.map { line ->
        when {
          isPageNumberCandidate(line, lines.size) -> DocumentBlockType.PAGE_NUMBER
          else -> null
        }
      }.toMutableList()
    }

    detectRepeatingHeadersAndFooters(preparedPages, nonMetadataPages)

    // 3. Analyze each page's content blocks using typography, coordinates, and vertical gaps
    return preparedPages.mapIndexed { pageIdx, lines ->
      val pageNumber = pageLayouts[pageIdx].pageNumber
      val preTags = nonMetadataPages[pageIdx]
      analyzeSinglePage(
        pageNumber = pageNumber,
        lines = lines,
        preAssignedTypes = preTags,
        pageWidth = pageLayouts[pageIdx].pageWidth,
      )
    }
  }

  fun buildSyntheticLayoutFromRawText(
    pageNumber: Int,
    rawText: String,
    pageWidth: Float = 595f,
    pageHeight: Float = 842f,
  ): ExtractedPageLayout {
    if (rawText.isBlank()) {
      return ExtractedPageLayout(pageNumber, emptyList(), "", pageWidth, pageHeight)
    }
    val rawLines = rawText.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val extracted = mutableListOf<ExtractedLine>()
    var y = 72f
    val defaultFontSize = 12f
    val lineStep = 18f

    for (raw in rawLines) {
      val trimmed = raw.trim()
      if (trimmed.isEmpty()) {
        y += lineStep * 0.9f // preserve paragraph gap in baseline coordinates
        continue
      }
      val zone = when {
        y <= pageHeight * 0.16f -> PageZone.TOP
        y >= pageHeight * 0.84f -> PageZone.BOTTOM
        else -> PageZone.MIDDLE
      }
      val estimatedWidth = (trimmed.length * 5.2f).coerceAtMost(pageWidth * 0.8f)
      extracted.add(
        ExtractedLine(
          text = trimmed,
          rawText = trimmed,
          baselineY = y,
          minX = pageWidth - 60f - estimatedWidth,
          maxX = pageWidth - 60f,
          maxFontSize = defaultFontSize,
          medianFontSize = defaultFontSize,
          isBold = false,
          charCount = trimmed.length,
          zone = zone,
          pageWidth = pageWidth,
          pageHeight = pageHeight,
          hasGlyphInversionSignal = true,
        )
      )
      y += lineStep
    }
    return ExtractedPageLayout(
      pageNumber = pageNumber,
      lines = extracted,
      rawText = rawText,
      pageWidth = pageWidth,
      pageHeight = pageHeight,
    )
  }

  private fun preparePageLines(layout: ExtractedPageLayout): List<WorkingLine> {
    if (layout.lines.isEmpty()) return emptyList()
    val result = mutableListOf<WorkingLine>()
    var prevCleaned: String? = null

    for ((idx, line) in layout.lines.withIndex()) {
      val repairedOrder = PersianReadingOrderRepairer.repairLine(line.text)
      val cleanedResult = PersianTextCleaner.cleanInlineWithMetrics(
        raw = repairedOrder,
        hasGlyphInversionSignal = line.hasGlyphInversionSignal,
      )
      val dedupedPhrase = PersianTextCleaner.deduplicateRepeatedPhrases(cleanedResult.text).trim()
      if (dedupedPhrase.isEmpty()) continue
      if (prevCleaned != null && dedupedPhrase.length >= 8 && dedupedPhrase == prevCleaned) {
        continue
      }
      result.add(
        WorkingLine(
          indexInPage = idx,
          source = line,
          cleanedText = dedupedPhrase,
          normalizedForHf = PersianTextCleaner.normalizeForHeaderFooter(dedupedPhrase),
          ligatureFixes = cleanedResult.ligatureFixesCount,
          detachedFixes = cleanedResult.detachedFinalLetterFixesCount,
        )
      )
      prevCleaned = dedupedPhrase
    }
    return result
  }

  private fun isPageNumberCandidate(line: WorkingLine, totalLinesOnPage: Int): Boolean {
    if (!PersianTextCleaner.isStandalonePageNumber(line.cleanedText)) return false
    return line.source.zone == PageZone.TOP ||
      line.source.zone == PageZone.BOTTOM ||
      line.indexInPage <= 1 ||
      line.indexInPage >= totalLinesOnPage - 2
  }

  private fun detectRepeatingHeadersAndFooters(
    pages: List<List<WorkingLine>>,
    preTags: List<MutableList<DocumentBlockType?>>,
  ) {
    val nonEmptyCount = pages.count { page -> page.isNotEmpty() }
    if (nonEmptyCount < 3) return
    val minThreshold = if (nonEmptyCount == 3) 3 else maxOf(3, (nonEmptyCount * 0.35).toInt())

    val headerFreq = mutableMapOf<String, Int>()
    val footerFreq = mutableMapOf<String, Int>()

    for (pIdx in pages.indices) {
      val lines = pages[pIdx]
      val tags = preTags[pIdx]
      val candidateTop = mutableListOf<String>()
      val candidateBottom = mutableListOf<String>()

      for (i in lines.indices) {
        if (tags[i] != null) continue
        if (candidateTop.size < 2 && (i <= 2 || lines[i].source.zone == PageZone.TOP)) {
          val hf = lines[i].normalizedForHf
          if (hf.length in 2..80) candidateTop.add(hf)
        }
      }
      for (i in lines.indices.reversed()) {
        if (tags[i] != null) continue
        if (candidateBottom.size < 2 && (i >= lines.size - 3 || lines[i].source.zone == PageZone.BOTTOM)) {
          val hf = lines[i].normalizedForHf
          if (hf.length in 2..80) candidateBottom.add(hf)
        }
      }

      candidateTop.distinct().forEach { key -> headerFreq[key] = (headerFreq[key] ?: 0) + 1 }
      candidateBottom.distinct().forEach { key -> footerFreq[key] = (footerFreq[key] ?: 0) + 1 }
    }

    val repeatingHeaders = headerFreq.filterValues { it >= minThreshold }.keys
    val repeatingFooters = footerFreq.filterValues { it >= minThreshold }.keys

    for (pIdx in pages.indices) {
      val lines = pages[pIdx]
      val tags = preTags[pIdx]

      var headerChecked = 0
      for (i in lines.indices) {
        if (tags[i] == DocumentBlockType.PAGE_NUMBER) continue
        if (headerChecked >= 2) break
        headerChecked++
        if (lines[i].normalizedForHf in repeatingHeaders) {
          tags[i] = DocumentBlockType.HEADER
        }
      }

      var footerChecked = 0
      for (i in lines.indices.reversed()) {
        if (tags[i] == DocumentBlockType.PAGE_NUMBER || tags[i] == DocumentBlockType.HEADER) continue
        if (footerChecked >= 2) break
        footerChecked++
        if (lines[i].normalizedForHf in repeatingFooters) {
          tags[i] = DocumentBlockType.FOOTER
        }
      }
    }
  }

  private fun analyzeSinglePage(
    pageNumber: Int,
    lines: List<WorkingLine>,
    preAssignedTypes: List<DocumentBlockType?>,
    pageWidth: Float,
  ): AnalyzedPageResult {
    val totalLigatureFixes = lines.sumOf { it.ligatureFixes }
    val totalDetachedFixes = lines.sumOf { it.detachedFixes }

    if (lines.isEmpty()) {
      return AnalyzedPageResult(
        pageNumber = pageNumber,
        blocks = listOf(
          DocumentBlock(
            id = "p${pageNumber}_b0",
            type = DocumentBlockType.EMPTY,
            text = "",
          )
        ),
        flatCleanedText = "",
        isEmpty = true,
        ligatureFixesCount = totalLigatureFixes,
        detachedLetterFixesCount = totalDetachedFixes,
      )
    }

    // Compute body typography baselines using non-header/footer/page-number lines
    val contentLines = lines.filterIndexed { index, _ -> preAssignedTypes[index] == null }
    val bodyFontMedian = computeWeightedBodyFontMedian(contentLines)
    val bodyLineGapMedian = computeBodyLineGapMedian(contentLines, bodyFontMedian)
    val bodyMaxWidth = computeBodyWidthReference(contentLines, pageWidth)

    // Determine whether this page is a Table of Contents page
    val tocCandidates = Array<TocParsedEntry?>(lines.size) { i ->
      if (preAssignedTypes[i] != null) null else parseTocCandidate(lines[i].cleanedText)
    }
    val tocCandidateCount = tocCandidates.count { it != null }
    val hasTocHeaderKeyword = lines.any { tocPageKeywordRegex.containsMatchIn(it.cleanedText) && it.cleanedText.length <= 50 }
    val isTocPage = tocCandidateCount >= 3 || (hasTocHeaderKeyword && tocCandidateCount >= 2)

    // Assign a preliminary structural type to every line
    val lineTypes = Array(lines.size) { i ->
      preAssignedTypes[i] ?: classifyContentLine(
        index = i,
        lines = lines,
        preAssignedTypes = preAssignedTypes,
        tocCandidate = if (isTocPage) tocCandidates[i] else null,
        hasTocHeaderKeyword = hasTocHeaderKeyword,
        bodyFontMedian = bodyFontMedian,
        bodyLineGapMedian = bodyLineGapMedian,
        bodyWidthRef = bodyMaxWidth,
        pageWidth = pageWidth,
      )
    }

    // Group adjacent lines into structural DocumentBlocks
    val blocks = mutableListOf<DocumentBlock>()
    var blockSeq = 0
    var i = 0
    while (i < lines.size) {
      val currentType = lineTypes[i]
      val currentLine = lines[i]

      when (currentType) {
        DocumentBlockType.HEADER,
        DocumentBlockType.FOOTER,
        DocumentBlockType.PAGE_NUMBER,
        DocumentBlockType.REFERENCE_MARK,
        DocumentBlockType.CENTERED_SHORT_LINE -> {
          blocks.add(
            buildSingleLineBlock(
              id = "p${pageNumber}_b${blockSeq++}",
              type = currentType,
              line = currentLine,
            )
          )
          i++
        }

        DocumentBlockType.TOC_ENTRY -> {
          val parsed = tocCandidates[i]
          val entryTitle = parsed?.title ?: currentLine.cleanedText
          val refPage = parsed?.referencePage
          val displayText = if (refPage != null) {
            "$entryTitle — صفحهٔ ${PersianDigits.formatInt(refPage)}"
          } else {
            currentLine.cleanedText
          }
          blocks.add(
            DocumentBlock(
              id = "p${pageNumber}_b${blockSeq++}",
              type = DocumentBlockType.TOC_ENTRY,
              text = displayText,
              rawText = currentLine.source.rawText,
              title = entryTitle,
              referencePageNumber = refPage,
              fontSizeSp = currentLine.source.maxFontSize,
              isBold = currentLine.source.isBold,
              zone = currentLine.source.zone,
              baselineY = currentLine.source.baselineY,
              leftX = currentLine.source.minX,
              rightX = currentLine.source.maxX,
              lineCount = 1,
            )
          )
          i++
        }

        DocumentBlockType.PAGE_TITLE,
        DocumentBlockType.SUBTITLE -> {
          val group = mutableListOf(currentLine)
          var j = i + 1
          while (j < lines.size && lineTypes[j] == currentType) {
            val prev = group.last()
            val next = lines[j]
            val gap = next.source.baselineY - prev.source.baselineY
            val sameFontTier = abs(next.source.maxFontSize - prev.source.maxFontSize) <= 1.5f
            if (sameFontTier && gap <= bodyLineGapMedian * 1.45f && !tocPageKeywordRegex.containsMatchIn(next.cleanedText)) {
              group.add(next)
              j++
            } else {
              break
            }
          }
          val joinedText = PersianTextCleaner.finalizeParagraphSpacing(
            group.joinToString(" ") { it.cleanedText }
          )
          blocks.add(
            buildMultiLineBlock(
              id = "p${pageNumber}_b${blockSeq++}",
              type = currentType,
              text = joinedText,
              group = group,
            )
          )
          i = j
        }

        DocumentBlockType.FOOTNOTE -> {
          val group = mutableListOf(currentLine)
          var j = i + 1
          while (j < lines.size && lineTypes[j] == DocumentBlockType.FOOTNOTE) {
            val prev = group.last()
            val next = lines[j]
            val gap = next.source.baselineY - prev.source.baselineY
            val startsNewFootnote = footnoteStartRegex.matches(next.cleanedText)
            if (!startsNewFootnote && gap <= bodyLineGapMedian * 1.45f) {
              group.add(next)
              j++
            } else {
              break
            }
          }
          val joinedFootnote = PersianTextCleaner.reconstructParagraphs(group.map { it.cleanedText })
          blocks.add(
            buildMultiLineBlock(
              id = "p${pageNumber}_b${blockSeq++}",
              type = DocumentBlockType.FOOTNOTE,
              text = joinedFootnote,
              group = group,
            )
          )
          i = j
        }

        DocumentBlockType.BODY_PARAGRAPH -> {
          val group = mutableListOf(currentLine)
          var j = i + 1
          while (j < lines.size && lineTypes[j] == DocumentBlockType.BODY_PARAGRAPH) {
            val prev = group.last()
            val next = lines[j]
            if (shouldBreakParagraph(prev, next, bodyLineGapMedian, bodyMaxWidth)) {
              break
            }
            group.add(next)
            j++
          }
          val joinedParagraph = joinBodyParagraphLines(group)
          if (joinedParagraph.isNotBlank()) {
            blocks.add(
              buildMultiLineBlock(
                id = "p${pageNumber}_b${blockSeq++}",
                type = DocumentBlockType.BODY_PARAGRAPH,
                text = joinedParagraph,
                group = group,
              )
            )
          }
          i = j
        }

        DocumentBlockType.EMPTY -> i++
      }
    }

    val readableTypes = setOf(
      DocumentBlockType.PAGE_TITLE,
      DocumentBlockType.SUBTITLE,
      DocumentBlockType.TOC_ENTRY,
      DocumentBlockType.CENTERED_SHORT_LINE,
      DocumentBlockType.BODY_PARAGRAPH,
      DocumentBlockType.FOOTNOTE,
    )
    val flatCleanedText = blocks
      .filter { it.type in setOf(DocumentBlockType.PAGE_TITLE, DocumentBlockType.SUBTITLE, DocumentBlockType.TOC_ENTRY, DocumentBlockType.CENTERED_SHORT_LINE, DocumentBlockType.BODY_PARAGRAPH) }
      .joinToString("\n\n") { it.text }
      .trim()

    val hasReadableContent = blocks.any { it.type in readableTypes && it.text.isNotBlank() }

    return AnalyzedPageResult(
      pageNumber = pageNumber,
      blocks = blocks,
      flatCleanedText = flatCleanedText,
      isEmpty = !hasReadableContent,
      ligatureFixesCount = totalLigatureFixes,
      detachedLetterFixesCount = totalDetachedFixes,
    )
  }

  private fun classifyContentLine(
    index: Int,
    lines: List<WorkingLine>,
    preAssignedTypes: List<DocumentBlockType?>,
    tocCandidate: TocParsedEntry?,
    hasTocHeaderKeyword: Boolean,
    bodyFontMedian: Float,
    bodyLineGapMedian: Float,
    bodyWidthRef: Float,
    pageWidth: Float,
  ): DocumentBlockType {
    val line = lines[index]
    val cleaned = line.cleanedText
    val src = line.source

    if (referenceMarkOnlyRegex.matches(cleaned) && cleaned.length <= 8) {
      return DocumentBlockType.REFERENCE_MARK
    }

    if (tocCandidate != null) {
      return DocumentBlockType.TOC_ENTRY
    }

    val isSmallerFootnoteFont = bodyFontMedian > 0f && src.maxFontSize > 0f && src.maxFontSize <= bodyFontMedian * 0.87f
    val startsWithFootnoteMarker = footnoteStartRegex.matches(cleaned)
    if (src.zone == PageZone.BOTTOM && (isSmallerFootnoteFont || startsWithFootnoteMarker)) {
      return DocumentBlockType.FOOTNOTE
    }

    val prevGap = findPreviousContentGap(index, lines, preAssignedTypes, bodyLineGapMedian)
    val nextGap = findNextContentGap(index, lines, preAssignedTypes, bodyLineGapMedian)
    val hasVerticalSeparationAfter = nextGap >= bodyLineGapMedian * 1.24f || index == lines.lastIndex
    val hasVerticalSeparationAround = hasVerticalSeparationAfter || prevGap >= bodyLineGapMedian * 1.35f

    val safeBodyFont = bodyFontMedian.coerceAtLeast(1f)
    val fontRatio = src.maxFontSize / safeBodyFont
    val endsWithCommaOrMidClause = cleaned.endsWith('،') || cleaned.endsWith('؛')

    // Page/Chapter Titles
    val isLargeTitleFont = fontRatio >= 1.22f || (fontRatio >= 1.14f && src.isBold)
    val isTocHeaderLine = hasTocHeaderKeyword && tocPageKeywordRegex.containsMatchIn(cleaned) && cleaned.length <= 45
    val isExplicitChapterLine = chapterHeadingPattern.matches(cleaned) && cleaned.length <= 65 &&
      !cleaned.endsWith('.') && !endsWithCommaOrMidClause && (src.isBold || hasVerticalSeparationAfter)

    if ((isLargeTitleFont && cleaned.length <= 95 && !endsWithCommaOrMidClause) || isTocHeaderLine || isExplicitChapterLine) {
      return DocumentBlockType.PAGE_TITLE
    }

    // Subtitles
    val isSubtitleFont = (fontRatio in 1.08f..1.219f) || (src.isBold && cleaned.length <= 75 && hasVerticalSeparationAround)
    if (isSubtitleFont && cleaned.length <= 85 && !endsWithCommaOrMidClause) {
      return DocumentBlockType.SUBTITLE
    }

    // Centered short lines (Dedications / Poetry / Epigraphs)
    val safePageWidth = pageWidth.coerceAtLeast(300f)
    val effectiveBodyRef = maxOf(bodyWidthRef, safePageWidth * 0.62f)
    val pageCenterX = safePageWidth / 2f
    val isHorizontallyCentered = src.width > 0f && abs(src.centerX - pageCenterX) <= safePageWidth * 0.14f
    val isSignificantlyNarrower = src.width > 0f && src.width <= effectiveBodyRef * 0.66f
    if (isHorizontallyCentered && isSignificantlyNarrower && cleaned.length in 2..62) {
      return DocumentBlockType.CENTERED_SHORT_LINE
    }

    return DocumentBlockType.BODY_PARAGRAPH
  }

  private fun parseTocCandidate(cleanedLine: String): TocParsedEntry? {
    if (cleanedLine.length < 4 || cleanedLine.length > 120) return null
    val match = tocDotLeaderRegex.matchEntire(cleanedLine)
      ?: tocSimpleTrailingPageRegex.matchEntire(cleanedLine)
      ?: return null
    val rawTitle = match.groupValues[1].trim().trimEnd('.', '…', '·', '-', '–', '—', '_')
    val refNum = PersianDigits.parseFlexibleInt(match.groupValues[2]) ?: return null
    if (refNum !in 1..9999) return null
    if (rawTitle.length < 2 || rawTitle.count { it.isLetter() } < 2) return null
    if (rawTitle == "صفحه" || rawTitle == "صفحهٔ") return null
    return TocParsedEntry(title = rawTitle, referencePage = refNum)
  }

  private fun shouldBreakParagraph(
    prev: WorkingLine,
    next: WorkingLine,
    bodyLineGapMedian: Float,
    bodyWidthRef: Float,
  ): Boolean {
    if (PersianTextCleaner.isBulletLine(next.cleanedText)) return true

    val verticalGap = next.source.baselineY - prev.source.baselineY
    if (verticalGap > bodyLineGapMedian * 1.42f) return true

    val prevText = prev.cleanedText.trimEnd()
    val prevEndsSentence = prevText.lastOrNull() in sentenceEndChars
    val prevLooksShort = bodyWidthRef > 0f && prev.source.width > 0f && prev.source.width < bodyWidthRef * 0.78f
    if (prevEndsSentence && prevLooksShort) return true

    if (prevEndsSentence && verticalGap > bodyLineGapMedian * 1.15f) return true

    return false
  }

  private fun joinBodyParagraphLines(group: List<WorkingLine>): String {
    if (group.isEmpty()) return ""
    val sb = StringBuilder()
    for (line in group) {
      val trimmed = line.cleanedText.trim()
      if (trimmed.isEmpty()) continue
      if (sb.isEmpty()) {
        sb.append(trimmed)
        continue
      }
      val currentEnd = sb[sb.lastIndex]
      val prevSecondLast = if (sb.length >= 2) sb[sb.length - 2] else null
      when {
        currentEnd == '-' && prevSecondLast?.isLetter() == true -> {
          sb.setLength(sb.length - 1)
          sb.append(trimmed)
        }
        currentEnd == '\u200C' -> sb.append(trimmed)
        else -> sb.append(' ').append(trimmed)
      }
    }
    return PersianTextCleaner.finalizeParagraphSpacing(sb.toString())
  }

  private fun buildSingleLineBlock(
    id: String,
    type: DocumentBlockType,
    line: WorkingLine,
  ): DocumentBlock {
    return DocumentBlock(
      id = id,
      type = type,
      text = line.cleanedText,
      rawText = line.source.rawText,
      fontSizeSp = line.source.maxFontSize,
      isBold = line.source.isBold,
      zone = line.source.zone,
      baselineY = line.source.baselineY,
      leftX = line.source.minX,
      rightX = line.source.maxX,
      lineCount = 1,
    )
  }

  private fun buildMultiLineBlock(
    id: String,
    type: DocumentBlockType,
    text: String,
    group: List<WorkingLine>,
  ): DocumentBlock {
    return DocumentBlock(
      id = id,
      type = type,
      text = text,
      rawText = group.joinToString("\n") { it.source.rawText },
      fontSizeSp = medianFloat(group.map { it.source.maxFontSize }),
      isBold = group.any { it.source.isBold },
      zone = group.first().source.zone,
      baselineY = group.first().source.baselineY,
      leftX = group.minOf { it.source.minX },
      rightX = group.maxOf { it.source.maxX },
      lineCount = group.size,
    )
  }

  private fun computeWeightedBodyFontMedian(contentLines: List<WorkingLine>): Float {
    if (contentLines.isEmpty()) return 12f
    val longBodyFonts = contentLines
      .filter { it.cleanedText.length >= 16 }
      .map { it.source.medianFontSize }
    val pool = if (longBodyFonts.isNotEmpty()) longBodyFonts else contentLines.map { it.source.medianFontSize }
    return medianFloat(pool).coerceAtLeast(6f)
  }

  private fun computeBodyLineGapMedian(contentLines: List<WorkingLine>, bodyFontMedian: Float): Float {
    if (contentLines.size < 2) return bodyFontMedian * 1.4f
    val gaps = mutableListOf<Float>()
    for (i in 1 until contentLines.size) {
      val prev = contentLines[i - 1].source
      val curr = contentLines[i].source
      if (abs(prev.maxFontSize - bodyFontMedian) <= 2f && abs(curr.maxFontSize - bodyFontMedian) <= 2f) {
        val diff = curr.baselineY - prev.baselineY
        if (diff in (bodyFontMedian * 0.8f)..(bodyFontMedian * 2.8f)) {
          gaps.add(diff)
        }
      }
    }
    return if (gaps.isNotEmpty()) medianFloat(gaps) else bodyFontMedian * 1.4f
  }

  private fun medianFloat(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
  }

  private fun computeBodyWidthReference(contentLines: List<WorkingLine>, pageWidth: Float): Float {
    val widths = contentLines
      .map { it.source.width }
      .filter { it > 0f }
      .sorted()
    if (widths.isEmpty()) return pageWidth * 0.68f
    val percentile85Idx = ((widths.size - 1) * 0.85f).toInt().coerceIn(0, widths.lastIndex)
    return widths[percentile85Idx]
  }

  private fun findPreviousContentGap(
    index: Int,
    lines: List<WorkingLine>,
    preAssignedTypes: List<DocumentBlockType?>,
    fallbackGap: Float,
  ): Float {
    for (j in index - 1 downTo 0) {
      if (preAssignedTypes[j] == null) {
        return (lines[index].source.baselineY - lines[j].source.baselineY).coerceAtLeast(0f)
      }
    }
    return fallbackGap * 1.5f
  }

  private fun findNextContentGap(
    index: Int,
    lines: List<WorkingLine>,
    preAssignedTypes: List<DocumentBlockType?>,
    fallbackGap: Float,
  ): Float {
    for (j in index + 1 until lines.size) {
      if (preAssignedTypes[j] == null) {
        return (lines[j].source.baselineY - lines[index].source.baselineY).coerceAtLeast(0f)
      }
    }
    return fallbackGap * 1.5f
  }
}
