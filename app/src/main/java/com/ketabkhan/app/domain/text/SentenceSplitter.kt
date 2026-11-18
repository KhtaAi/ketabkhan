package com.ketabkhan.app.domain.text

object SentenceSplitter {

  const val DEFAULT_MAX_SENTENCE_LENGTH = 220

  private val abbreviations = setOf(
    "ص", "ج", "ش", "م", "ق", "ه", "هـ", "د", "ت", "ر.ک", "ن.ک", "رک", "نک",
    "الخ", "غیره", "پ", "س", "ع", "عج",
    "dr", "mr", "mrs", "ms", "prof", "vs", "etc", "e.g", "i.e", "st", "no", "fig"
  )

  private val closingDelimiters = setOf('»', '"', '\'', ')', ']', '}')
  private val terminalPunctuation = setOf('.', '؟', '?', '!', '…')
  private val secondaryBreaks = setOf('؛', ':', '،', ',', '–', '—')
  private val conjunctionSplitTokens = listOf(" و ", " که ", " اما ", " ولی ", " یا ", " بنابراین ", " زیرا ")

  fun split(text: String, maxSentenceLength: Int = DEFAULT_MAX_SENTENCE_LENGTH): List<String> {
    if (text.isBlank()) return emptyList()

    val paragraphs = text.replace("\r\n", "\n")
      .split(Regex("\\n\\s*\\n+"))
      .map { it.replace('\n', ' ').replace(Regex("\\s+"), " ").trim() }
      .filter { it.isNotEmpty() }

    val sentences = mutableListOf<String>()
    for (paragraph in paragraphs) {
      val rawSentences = splitParagraphIntoRawSentences(paragraph)
      for (sentence in rawSentences) {
        if (sentence.length <= maxSentenceLength) {
          addIfMeaningful(sentences, sentence)
        } else {
          splitLongSentence(sentence, maxSentenceLength).forEach { part ->
            addIfMeaningful(sentences, part)
          }
        }
      }
    }
    return sentences
  }

  private fun splitParagraphIntoRawSentences(paragraph: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    val len = paragraph.length

    while (i < len) {
      val ch = paragraph[i]
      current.append(ch)

      if (ch in terminalPunctuation) {
        // Consume repeated terminal punctuation (e.g., "...", "!!", "؟!")
        while (i + 1 < len && paragraph[i + 1] in terminalPunctuation) {
          i++
          current.append(paragraph[i])
        }

        val isDecimal = ch == '.' &&
          i > 0 && isAnyDigit(paragraph[i - 1]) &&
          i + 1 < len && isAnyDigit(paragraph[i + 1])

        val isEllipsis = current.endsWith("...") || ch == '…'
        val isAbbreviation = ch == '.' && !isEllipsis && isProtectedAbbreviation(current, paragraph, i)

        if (!isDecimal && !isAbbreviation) {
          // Attach trailing closing quotes/brackets
          while (i + 1 < len && paragraph[i + 1] in closingDelimiters) {
            i++
            current.append(paragraph[i])
          }

          val candidate = current.toString().trim()
          if (candidate.isNotEmpty()) {
            result += candidate
          }
          current.setLength(0)

          // Skip whitespace immediately following sentence boundary
          while (i + 1 < len && paragraph[i + 1].isWhitespace()) {
            i++
          }
        }
      }
      i++
    }

    val remainder = current.toString().trim()
    if (remainder.isNotEmpty()) {
      result += remainder
    }
    return result
  }

  private fun isProtectedAbbreviation(
    current: StringBuilder,
    paragraph: String,
    dotIndex: Int,
  ): Boolean {
    // Check if followed directly by another non-space letter (e.g., ر.ک. or e.g.)
    if (dotIndex + 1 < paragraph.length && paragraph[dotIndex + 1].isLetter()) {
      return true
    }

    // Extract token before the dot
    val beforeDot = current.substring(0, current.length - 1).trimEnd()
    val lastSpace = beforeDot.lastIndexOfAny(charArrayOf(' ', '\t', '(', '«', '['))
    val token = if (lastSpace >= 0) beforeDot.substring(lastSpace + 1) else beforeDot
    val normalizedToken = token.lowercase().trimEnd('.')

    if (normalizedToken in abbreviations) return true

    // Single-letter Persian/Latin initial followed by dot and space (e.g. "م. قاسمی" or "ا. ب.")
    if (normalizedToken.length == 1 && normalizedToken[0].isLetter()) {
      return true
    }
    return false
  }

  private fun splitLongSentence(sentence: String, maxLen: Int): List<String> {
    val parts = mutableListOf<String>()
    var remaining = sentence.trim()

    while (remaining.length > maxLen) {
      val splitIndex = findBestSplitIndex(remaining, maxLen)
      if (splitIndex <= 0 || splitIndex >= remaining.length) {
        break
      }
      val head = remaining.substring(0, splitIndex).trim()
      val tail = remaining.substring(splitIndex).trim()
      if (head.isNotEmpty()) {
        parts += head
      }
      if (tail.isEmpty() || tail.length == remaining.length) {
        break
      }
      remaining = tail
    }

    if (remaining.isNotEmpty()) {
      parts += remaining
    }
    return parts
  }

  private fun findBestSplitIndex(text: String, maxLen: Int): Int {
    val minIndex = maxOf(24, maxLen / 3)
    val searchLimit = minOf(maxLen, text.length - 1)

    // 1. Prefer natural punctuation breaks (؛ : ، , – —)
    for (idx in searchLimit downTo minIndex) {
      if (text[idx] in secondaryBreaks) {
        val afterPunct = idx + 1
        if (afterPunct < text.length && text[afterPunct].isWhitespace()) {
          return afterPunct
        }
        return afterPunct
      }
    }

    // 2. Try conjunctions (" و ", " که ", ...)
    var bestConjunctionSplit = -1
    for (token in conjunctionSplitTokens) {
      val pos = text.lastIndexOf(token, startIndex = searchLimit)
      if (pos >= minIndex && pos > bestConjunctionSplit) {
        bestConjunctionSplit = pos
      }
    }
    if (bestConjunctionSplit >= minIndex) {
      return bestConjunctionSplit
    }

    // 3. Nearest whitespace before maxLen
    for (idx in searchLimit downTo minIndex) {
      if (text[idx].isWhitespace()) {
        return idx
      }
    }

    // 4. Forward whitespace if no space was found in [minIndex..searchLimit]
    val nextSpace = text.indexOf(' ', startIndex = searchLimit)
    return if (nextSpace > 0) nextSpace else searchLimit
  }

  private fun addIfMeaningful(target: MutableList<String>, candidate: String) {
    val trimmed = candidate.trim()
    if (trimmed.any { it.isLetterOrDigit() }) {
      target += trimmed
    }
  }

  private fun isAnyDigit(ch: Char): Boolean {
    return ch in '0'..'9' || ch in '۰'..'۹' || ch in '\u0660'..'\u0669'
  }
}
