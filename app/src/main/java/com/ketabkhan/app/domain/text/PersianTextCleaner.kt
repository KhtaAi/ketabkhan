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
  private val spaceBeforePunctRegex = Regex("\\s+([،؛؟!.…,:)])")
  private val spaceAfterOpenParenRegex = Regex("([(«])\\s+")
  private val spaceBeforeCloseBracketRegex = Regex("\\s+([»\\]])")
  private val pageNumberLineRegex = Regex(
    "^[-–—\\[\\]()«»\\s]*(?:صفحه|صفحهٔ|ص\\.?|Page|page|P\\.?)?[\\s:]*[0-9۰-۹٠-٩]+(?:\\s*(?:از|of|/)\\s*[0-9۰-۹٠-٩]+)?[\\s\\-–—\\[\\]()]*$"
  )
  private val bulletStartRegex = Regex("^(?:[•●▪◦\\-*]|\\d+[.)\\-]|[۰-۹]+[.)\\-])\\s+.*")
  private val sentenceEndChars = setOf('.', '؟', '?', '!', '…', ':', '»', '"', '؛')
  private val digitsRegex = Regex("[0-9۰-۹٠-٩]+")

  fun cleanInline(raw: String): String {
    if (raw.isBlank()) return ""
    var text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
    text = text
      .replace('\u064A', '\u06CC') // ي -> ی
      .replace('\u0649', '\u06CC') // ى -> ی
      .replace('\u0643', '\u06A9') // ك -> ک
      .replace("\u0640", "")       // remove tatweel/kashida
    text = diacriticsRegex.replace(text, "")
    text = zeroWidthJunkRegex.replace(text, "")
    text = spaceAroundZwnjRegex.replace(text, "\u200C")
    text = multipleZwnjRegex.replace(text, "\u200C")
    text = multiSpacesRegex.replace(text, " ")
    text = spaceBeforePunctRegex.replace(text, "$1")
    text = spaceAfterOpenParenRegex.replace(text, "$1")
    text = spaceBeforeCloseBracketRegex.replace(text, "$1")
    return text.trim()
  }

  fun cleanBookPages(rawPages: List<String>): List<CleanedPage> {
    if (rawPages.isEmpty()) return emptyList()

    // Step 1: Normalize lines per page and strip standalone page numbers
    val pageLinesList = rawPages.map { raw ->
      normalizeRawLines(raw)
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

    // Step 3: Rejoin broken mid-sentence lines while preserving paragraph boundaries
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

  private fun isStandalonePageNumber(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.length > 28) return false
    return pageNumberLineRegex.matches(trimmed)
  }

  private fun normalizeForHeaderFooter(line: String): String {
    return digitsRegex.replace(line.trim(), "#")
      .replace(multiSpacesRegex, " ")
      .trim()
  }

  private fun reconstructParagraphs(lines: List<String>): String {
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

  private fun finalizeParagraphSpacing(raw: String): String {
    var text = raw.trim()
    if (text.isEmpty()) return ""
    text = multiSpacesRegex.replace(text, " ")
    text = spaceBeforePunctRegex.replace(text, "$1")
    text = spaceAfterOpenParenRegex.replace(text, "$1")
    text = spaceBeforeCloseBracketRegex.replace(text, "$1")
    // Ensure a single space after Persian/standard punctuation when followed directly by a letter
    text = text.replace(Regex("([،؛؟!])([^\\s»)\"\\]،؛؟!.])"), "$1 $2")
    // Strip stray ZWNJ at start/end of paragraph or next to spaces
    text = text.replace(Regex("^\\u200C+|\\u200C+$"), "")
    return text.trim()
  }
}
