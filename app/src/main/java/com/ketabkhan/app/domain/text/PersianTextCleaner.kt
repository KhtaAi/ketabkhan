package com.ketabkhan.app.domain.text

import java.text.Normalizer

data class CleanedPage(
  val pageNumber: Int,
  val cleanedText: String,
  val isEmpty: Boolean,
)

object PersianTextCleaner {

  private val diacriticsRegex = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
  private val zeroWidthJunkRegex = Regex("[\\u200B\\u200E\\u200F\\uFEFF\\u00AD]")
  private val multipleZwnjRegex = Regex("\\u200C{2,}")
  private val spaceAroundZwnjRegex = Regex("[ \\t]*\\u200C[ \\t]*")
  private val multiSpacesRegex = Regex("[ \\t]+")

  // Spacing rules for digits & Persian/Arabic letters (e.g. "۱۲۹۹رضاخان" -> "۱۲۹۹ رضاخان")
  private val digitBeforeLetterRegex = Regex("([0-9۰-۹٠-٩])([\\u0621-\\u064A\\u066E-\\u06D5])")
  private val letterBeforeDigitRegex = Regex("([\\u0621-\\u064A\\u066E-\\u06D5])([0-9۰-۹٠-٩])")

  // Guillemet & punctuation spacing rules
  private val spaceBeforePunctRegex = Regex("\\s+([،؛؟!.…,:)\\]])")
  private val spaceAfterOpenDelimRegex = Regex("([(«\\[])\\s+")
  private val spaceBeforeCloseGuillemetRegex = Regex("\\s+»")
  private val charBeforeOpenGuillemetRegex = Regex("([\\u0621-\\u06D5\\p{L}\\p{Nd}،؛:.!؟])«")
  private val closeGuillemetBeforeCharRegex = Regex("»([\\u0621-\\u06D5\\p{L}\\p{Nd}])")
  private val spaceAfterPunctRegex = Regex("([،؛؟!])([^\\s»)\"\\]،؛؟!.])")
  private val periodBeforeWordRegex = Regex("(?<![0-9۰-۹٠-٩])\\.([\\u0621-\\u06D5]{2,})")

  // Exact repeated 3+ word overlap caused by duplicate PDF text layers
  private val repeatedMultiWordPhraseRegex = Regex(
    "(?<![\\u0621-\\u06D5\\u200C\\p{Nd}])([\\u0621-\\u06D5\\u200C0-9۰-۹]+(?:\\s+[\\u0621-\\u06D5\\u200C0-9۰-۹]+){2,7})\\s+\\1(?![\\u0621-\\u06D5\\u200C\\p{Nd}])"
  )

  private val pageNumberLineRegex = Regex(
    "^[-–—\\[\\]()«»\\s]*(?:صفحه|صفحهٔ|ص\\.?|Page|page|P\\.?)?[\\s:]*[0-9۰-۹٠-٩]+(?:\\s*(?:از|of|/)\\s*[0-9۰-۹٠-٩]+)?[\\s\\-–—\\[\\]()]*$"
  )
  private val bulletStartRegex = Regex("^(?:[•●▪◦\\-*]|\\d+[.)\\-]|[۰-۹]+[.)\\-])\\s+.*")
  private val sentenceEndChars = setOf('.', '؟', '?', '!', '…', ':', '»', '"', '؛')
  private val digitsRegex = Regex("[0-9۰-۹٠-٩]+")

  fun cleanInline(raw: String): String {
    return cleanInlineWithMetrics(raw).text
  }

  fun cleanInlineWithMetrics(
    raw: String,
    hasGlyphInversionSignal: Boolean = false,
  ): LigatureRepairResult {
    if (raw.isBlank()) {
      return LigatureRepairResult(text = "", ligatureFixesCount = 0, detachedFinalLetterFixesCount = 0)
    }
    var text = normalizeCharacters(raw)
    text = fixUnpairedGuillemets(text)
    val ligatureRepair = PersianLigatureRestorer.restore(
      text = text,
      hasGlyphInversionSignal = hasGlyphInversionSignal,
    )
    text = ligatureRepair.text
    val detachedRepair = PersianLigatureRestorer.repairUnmistakableDetachedTrailingChar(text)
    text = detachedRepair.text
    text = fixSpacingAndPunctuation(text)
    text = PersianWordBoundaryRestorer.restoreWordBoundaries(text)
    text = PersianZwnjRestorer.restore(text)
    text = fixSpacingAndPunctuation(text)
    text = fixZwnjBoundaries(text)
    return LigatureRepairResult(
      text = text.trim(),
      ligatureFixesCount = ligatureRepair.ligatureFixesCount,
      detachedFinalLetterFixesCount = detachedRepair.detachedFinalLetterFixesCount,
    )
  }

  fun cleanBookPages(rawPages: List<String>): List<CleanedPage> {
    if (rawPages.isEmpty()) return emptyList()

    // Step 1: Normalize lines per page, remove deterministic overlap duplicates, strip page numbers
    val pageLinesList = rawPages.map { raw ->
      val normalizedLines = normalizeRawLines(raw)
      removeDeterministicDuplicateLines(normalizedLines)
        .filterNot { isStandalonePageNumber(it) }
        .toMutableList()
    }

    // Step 2: Detect and strip repeating headers & footers when book has enough pages
    val nonEmptyCount = pageLinesList.count { it.isNotEmpty() }
    if (nonEmptyCount >= 4) {
      val minThreshold = maxOf(3, (nonEmptyCount * 0.35).toInt())

      val headerFreq = mutableMapOf<String, Int>()
      val footerFreq = mutableMapOf<String, Int>()

      for (lines in pageLinesList) {
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) continue

        nonBlank.take(2)
          .map { normalizeForHeaderFooter(it) }
          .filter { it.length in 2..80 }
          .distinct()
          .forEach { key -> headerFreq[key] = (headerFreq[key] ?: 0) + 1 }

        nonBlank.takeLast(2)
          .map { normalizeForHeaderFooter(it) }
          .filter { it.length in 2..80 }
          .distinct()
          .forEach { key -> footerFreq[key] = (footerFreq[key] ?: 0) + 1 }
      }

      val repeatingHeaders = headerFreq.filterValues { it >= minThreshold }.keys
      val repeatingFooters = footerFreq.filterValues { it >= minThreshold }.keys

      for (lines in pageLinesList) {
        var strippedTop = 0
        while (lines.isNotEmpty() && strippedTop < 2) {
          val firstIdx = lines.indexOfFirst { it.isNotBlank() }
          if (firstIdx in 0..1 && normalizeForHeaderFooter(lines[firstIdx]) in repeatingHeaders) {
            lines.removeAt(firstIdx)
            strippedTop++
          } else {
            break
          }
        }

        var strippedBottom = 0
        while (lines.isNotEmpty() && strippedBottom < 2) {
          val lastIdx = lines.indexOfLast { it.isNotBlank() }
          if (lastIdx >= 0 && lastIdx >= lines.size - 2 &&
            normalizeForHeaderFooter(lines[lastIdx]) in repeatingFooters
          ) {
            lines.removeAt(lastIdx)
            strippedBottom++
          } else {
            break
          }
        }
      }
    }

    // Step 3: Rejoin broken lines, fix spacing/guillemets, and restore ZWNJ per paragraph
    return pageLinesList.mapIndexed { index, lines ->
      val cleaned = reconstructParagraphs(lines)
      CleanedPage(
        pageNumber = index + 1,
        cleanedText = cleaned,
        isEmpty = cleaned.isBlank(),
      )
    }
  }

  fun cleanSinglePage(raw: String, pageNumber: Int = 1): CleanedPage {
    return cleanBookPages(listOf(raw)).first().copy(pageNumber = pageNumber)
  }

  private fun normalizeCharacters(raw: String): String {
    var text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
    text = text
      .replace('\u064A', '\u06CC') // ي -> ی
      .replace('\u0649', '\u06CC') // ى -> ی
      .replace('\u0643', '\u06A9') // ك -> ک
      .replace("\u0640", "")       // remove tatweel/kashida
    text = diacriticsRegex.replace(text, "")
    text = zeroWidthJunkRegex.replace(text, "")
    return fixZwnjBoundaries(text)
  }

  private fun fixZwnjBoundaries(raw: String): String {
    // Preserve space if space was adjacent to ZWNJ: spaces take precedence over zero-width non-joiner
    var text = raw.replace(Regex("[ \\t]+\\u200C+|\\u200C+[ \\t]+"), " ")
    text = multipleZwnjRegex.replace(text, "\u200C")
    text = text.replace(Regex("^\\u200C+|\\u200C+$"), "")
    return text
  }

  private fun fixUnpairedGuillemets(line: String): String {
    if (!line.contains('«') && !line.contains('»')) return line
    val chars = line.toCharArray()
    var depth = 0
    val unmatchedCloses = mutableListOf<Int>()
    for (i in chars.indices) {
      when (chars[i]) {
        '«' -> {
          if (unmatchedCloses.isNotEmpty()) {
            val openIdx = unmatchedCloses.removeAt(0)
            chars[openIdx] = '«'
            chars[i] = '»'
          } else {
            depth++
          }
        }
        '»' -> {
          if (depth > 0) depth-- else unmatchedCloses.add(i)
        }
      }
    }
    return String(chars)
  }

  private fun fixSpacingAndPunctuation(raw: String): String {
    var text = multiSpacesRegex.replace(raw, " ")
    text = digitBeforeLetterRegex.replace(text, "$1 $2")
    text = letterBeforeDigitRegex.replace(text, "$1 $2")
    text = spaceBeforePunctRegex.replace(text, "$1")
    text = spaceAfterOpenDelimRegex.replace(text, "$1")
    text = spaceBeforeCloseGuillemetRegex.replace(text, "»")
    text = charBeforeOpenGuillemetRegex.replace(text, "$1 «")
    text = closeGuillemetBeforeCharRegex.replace(text, "» $1")
    text = spaceAfterPunctRegex.replace(text, "$1 $2")
    text = periodBeforeWordRegex.replace(text, ". $1")
    text = multiSpacesRegex.replace(text, " ")
    return text
  }

  private fun normalizeRawLines(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val normalizedNewlines = raw.replace("\r\n", "\n").replace('\r', '\n')
    val rawLines = normalizedNewlines.split('\n')
    val result = mutableListOf<String>()
    var previousBlank = true

    for (line in rawLines) {
      val cleaned = cleanInline(line)
      if (cleaned.isEmpty()) {
        if (!previousBlank) {
          result += ""
          previousBlank = true
        }
      } else {
        result += cleaned
        previousBlank = false
      }
    }
    while (result.isNotEmpty() && result.last().isEmpty()) {
      result.removeAt(result.lastIndex)
    }
    return result
  }

  fun removeDeterministicDuplicateLines(lines: List<String>): List<String> {
    if (lines.size < 2) return lines
    val deduped = ArrayList<String>(lines.size)
    var prevNonBlank: String? = null
    for (line in lines) {
      if (line.isBlank()) {
        deduped.add(line)
        continue
      }
      val deduplicatedPhrase = repeatedMultiWordPhraseRegex.replace(line, "$1")
      if (prevNonBlank != null && deduplicatedPhrase.length >= 8 && deduplicatedPhrase == prevNonBlank) {
        continue
      }
      deduped.add(deduplicatedPhrase)
      prevNonBlank = deduplicatedPhrase
    }
    return deduped
  }

  fun deduplicateRepeatedPhrases(line: String): String {
    return repeatedMultiWordPhraseRegex.replace(line, "$1")
  }

  fun isStandalonePageNumber(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.length > 28) return false
    return pageNumberLineRegex.matches(trimmed)
  }

  fun isBulletLine(line: String): Boolean {
    return bulletStartRegex.matches(line.trim())
  }

  fun normalizeForHeaderFooter(line: String): String {
    return digitsRegex.replace(line.trim(), "#")
      .replace(multiSpacesRegex, " ")
      .trim()
  }

  fun reconstructParagraphs(lines: List<String>): String {
    if (lines.isEmpty()) return ""

    val paragraphs = mutableListOf<String>()
    val currentParagraph = StringBuilder()

    fun flushParagraph() {
      val text = finalizeParagraphSpacing(currentParagraph.toString())
      if (text.isNotEmpty()) {
        paragraphs += text
      }
      currentParagraph.setLength(0)
    }

    for (i in lines.indices) {
      val line = lines[i]
      if (line.isBlank()) {
        flushParagraph()
        continue
      }

      if (currentParagraph.isEmpty()) {
        currentParagraph.append(line)
        continue
      }

      val prev = currentParagraph.toString()
      val prevTrimmed = prev.trimEnd()
      val lastChar = prevTrimmed.lastOrNull()

      val isBullet = bulletStartRegex.matches(line)
      val prevEndsSentence = lastChar != null && lastChar in sentenceEndChars
      val prevEndsWithHyphen = lastChar == '-' && prevTrimmed.length >= 2 &&
        prevTrimmed[prevTrimmed.length - 2].isLetter()
      val prevLooksLikeShortHeading = !prevEndsSentence && prevTrimmed.length < 22 &&
        !prevTrimmed.endsWith('،') && !prevTrimmed.endsWith('\u200C')

      when {
        isBullet -> {
          flushParagraph()
          currentParagraph.append(line)
        }
        prevEndsWithHyphen -> {
          currentParagraph.setLength(prevTrimmed.length - 1)
          currentParagraph.append(line)
        }
        prevTrimmed.endsWith('\u200C') -> {
          currentParagraph.setLength(prevTrimmed.length)
          currentParagraph.append(line)
        }
        prevEndsSentence || prevLooksLikeShortHeading -> {
          flushParagraph()
          currentParagraph.append(line)
        }
        else -> {
          currentParagraph.append(' ').append(line)
        }
      }
    }

    flushParagraph()
    return paragraphs.joinToString("\n\n")
  }

  fun finalizeParagraphSpacing(raw: String): String {
    var text = raw.trim()
    if (text.isEmpty()) return ""
    text = fixUnpairedGuillemets(text)
    val ligatureResult = PersianLigatureRestorer.restore(text)
    text = ligatureResult.text
    text = fixSpacingAndPunctuation(text)
    text = PersianWordBoundaryRestorer.restoreWordBoundaries(text)
    text = PersianZwnjRestorer.restore(text)
    text = fixSpacingAndPunctuation(text)
    text = fixZwnjBoundaries(text)
    return text.trim()
  }
}
