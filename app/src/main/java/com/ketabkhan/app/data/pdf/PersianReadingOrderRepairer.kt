package com.ketabkhan.app.data.pdf

import java.text.Normalizer

/**
 * High-precision Persian RTL reading-order detector & repairer for PDF-extracted lines.
 * Governing principle: NEVER worsen uncertain or ambiguous lines — only apply deterministic
 * or high-confidence repairs.
 */
object PersianReadingOrderRepairer {

  private const val HIGH_CONFIDENCE_WORD_ORDER_MARGIN = 20f

  private val persianFunctionAndCommonWords = setOf(
    "از", "به", "که", "را", "در", "و", "است", "بود", "شد", "این", "آن",
    "با", "برای", "تا", "یا", "اما", "ولی", "اگر", "چون", "هم", "نیز",
    "یک", "دو", "سه", "او", "ما", "شما", "آنها", "می", "نمی", "گفت", "کرد",
    "دارد", "داشت", "شود", "کند", "نیست", "هست", "بر", "پس", "پیش", "روی",
  )

  private val clauseStartersAndPrepositions = setOf(
    "در", "به", "از", "با", "که", "و", "تا", "یا", "برای", "اما", "ولی",
    "چون", "زیرا", "اگر", "بر", "این", "آن",
  )

  private val finiteSentenceEndVerbs = setOf(
    "است", "بود", "شد", "نیست", "هست", "دارد", "داشت", "گفت", "کرد",
    "آمد", "رفت", "ماند", "یافت", "داد", "گرفت", "دید", "خواند", "نوشت",
    "می‌شد", "می‌شود", "می‌کند", "می‌کرد", "نمی‌شود", "نمی‌شد", "نمی‌کند",
    "شدند", "بودند", "هستند", "نیستند", "دارند", "داشتند", "گفتند", "کردند",
  )

  private val terminalPunctuation = setOf('.', '؟', '!', '؛')

  fun repairPage(rawPage: String): String {
    if (rawPage.isBlank()) return rawPage
    val lines = rawPage.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    return lines.joinToString("\n") { repairLine(it) }
  }

  fun repairLine(line: String): String {
    if (line.isBlank()) return line

    val normalizedLine = lightNormalizeForAnalysis(line)
    val persianLetterCount = normalizedLine.count { isPersianLetter(it) }
    if (persianLetterCount < 4) {
      return swapUnpairedGuillemetsIfSafe(line)
    }

    // 1. Check for full character-reversed visual dump (e.g. ".دوب رهش رد وا")
    tryRepairReversedCharacters(line)?.let { charRepaired ->
      return swapUnpairedGuillemetsIfSafe(charRepaired)
    }

    // 2. Check for token-level reversed RTL line with high confidence
    // (e.g. ".آمد تهران به ۱۲۹۹ سال در رضاخان")
    tryRepairReversedWordOrder(line)?.let { wordRepaired ->
      return swapUnpairedGuillemetsIfSafe(wordRepaired)
    }

    // 3. Deterministic bracket/guillemet mirroring when » opens at depth 0 before «
    return swapUnpairedGuillemetsIfSafe(line)
  }

  internal fun swapUnpairedGuillemetsIfSafe(line: String): String {
    if (!line.contains('«') && !line.contains('»')) return line

    val chars = line.toCharArray()
    var depth = 0
    val unpairedCloseIndices = mutableListOf<Int>()
    var i = 0
    while (i < chars.size) {
      when (chars[i]) {
        '«' -> {
          if (unpairedCloseIndices.isNotEmpty()) {
            // Match earliest unpaired '»' with this '«' to fix »...« -> «...»
            val openIdx = unpairedCloseIndices.removeAt(0)
            chars[openIdx] = '«'
            chars[i] = '»'
          } else {
            depth++
          }
        }
        '»' -> {
          if (depth > 0) {
            depth--
          } else {
            unpairedCloseIndices.add(i)
          }
        }
      }
      i++
    }
    return String(chars)
  }

  private fun tryRepairReversedCharacters(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.length < 6) return null

    val origTokens = tokenizeWords(lightNormalizeForAnalysis(trimmed))
    val origKnownCount = origTokens.count { it in persianFunctionAndCommonWords }
    if (origKnownCount > 0) return null

    val reversedCandidate = reverseVisualLinePreservingLtrRuns(trimmed)
    val revTokens = tokenizeWords(lightNormalizeForAnalysis(reversedCandidate))
    val revKnownCount = revTokens.count { it in persianFunctionAndCommonWords }

    val startsWithTerminalOrig = trimmed.first() in terminalPunctuation && !trimmed.startsWith("...")
    val endsWithTerminalRev = reversedCandidate.last() in terminalPunctuation

    val highConfidence =
      (revKnownCount >= 2 && origKnownCount == 0) ||
        (revKnownCount >= 1 && startsWithTerminalOrig && endsWithTerminalRev && origTokens.size >= 3)

    return if (highConfidence) reversedCandidate else null
  }

  private fun tryRepairReversedWordOrder(line: String): String? {
    val trimmed = line.trim()
    val rawTokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (rawTokens.size < 4) return null

    val startsWithTerminalPunct = trimmed.first() in terminalPunctuation && !trimmed.startsWith("...")
    val endsWithTerminalPunct = trimmed.last() in terminalPunctuation

    val normFirst = stripPunctuation(lightNormalizeForAnalysis(rawTokens.first()))
    val normLast = stripPunctuation(lightNormalizeForAnalysis(rawTokens.last()))

    val hasVerbAtStartAndPrepositionAtEnd =
      normFirst in finiteSentenceEndVerbs && normLast in clauseStartersAndPrepositions

    val hasInvertedGuillemetAcrossTokens =
      trimmed.indexOf('»').let { closeIdx ->
        val openIdx = trimmed.indexOf('«')
        closeIdx >= 0 && openIdx > closeIdx
      }

    // Must exhibit clear structural reversal signal before even considering word reversal
    if (!startsWithTerminalPunct && !hasVerbAtStartAndPrepositionAtEnd && !hasInvertedGuillemetAcrossTokens) {
      return null
    }

    // Do not reverse if sentence already looks like valid Persian prose ending properly
    if (!startsWithTerminalPunct && endsWithTerminalPunct && normLast in finiteSentenceEndVerbs) {
      return null
    }

    val candidate = buildRepairedWordOrderLine(rawTokens, startsWithTerminalPunct, endsWithTerminalPunct)
    val origScore = scoreReadingOrder(trimmed)
    val candidateScore = scoreReadingOrder(candidate)

    return if (candidateScore - origScore >= HIGH_CONFIDENCE_WORD_ORDER_MARGIN) {
      candidate
    } else {
      null
    }
  }

  private fun buildRepairedWordOrderLine(
    rawTokens: List<String>,
    startsWithTerminalPunct: Boolean,
    endsWithTerminalPunct: Boolean,
  ): String {
    val working = rawTokens.toMutableList()
    var movedTerminalPunct: Char? = null

    if (startsWithTerminalPunct && !endsWithTerminalPunct) {
      val first = working.first()
      val ch = first.firstOrNull { it in terminalPunctuation }
      if (ch != null && first.startsWith(ch)) {
        movedTerminalPunct = ch
        val stripped = first.drop(1)
        if (stripped.isEmpty()) {
          working.removeAt(0)
        } else {
          working[0] = stripped
        }
      }
    }

    val reversed = working.reversed().map { mirrorTokenDelimiters(it) }.toMutableList()
    if (movedTerminalPunct != null && reversed.isNotEmpty()) {
      reversed[reversed.lastIndex] = reversed.last() + movedTerminalPunct
    }
    return reversed.joinToString(" ")
  }

  internal fun scoreReadingOrder(line: String): Float {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return 0f

    var score = 0f
    val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val normTokens = tokens.map { stripPunctuation(lightNormalizeForAnalysis(it)) }.filter { it.isNotEmpty() }
    if (normTokens.isEmpty()) return 0f

    if (trimmed.first() in terminalPunctuation && !trimmed.startsWith("...")) {
      score -= 18f
    }
    if (trimmed.last() in terminalPunctuation) {
      score += 12f
    }

    val firstWord = normTokens.first()
    val lastWord = normTokens.last()

    if (firstWord in finiteSentenceEndVerbs) {
      score -= 10f
    }
    if (lastWord in finiteSentenceEndVerbs) {
      score += 10f
    }
    if (lastWord in clauseStartersAndPrepositions) {
      score -= 12f
    }
    if (firstWord in clauseStartersAndPrepositions) {
      score += 4f
    }

    // Preposition followed by a non-preposition noun/number gets a natural flow bonus
    for (i in 0 until normTokens.lastIndex) {
      if (normTokens[i] in setOf("در", "به", "از", "با", "برای") &&
        normTokens[i + 1] !in finiteSentenceEndVerbs
      ) {
        score += 3f
      }
    }

    // Check guillemet orientation
    val firstOpen = trimmed.indexOf('«')
    val firstClose = trimmed.indexOf('»')
    if (firstOpen >= 0 && firstClose >= 0) {
      if (firstOpen < firstClose) score += 6f else score -= 10f
    }

    return score
  }

  private fun reverseVisualLinePreservingLtrRuns(text: String): String {
    val reversedChars = text.reversed()
    val result = StringBuilder(reversedChars.length)
    var i = 0
    while (i < reversedChars.length) {
      val ch = reversedChars[i]
      if (isLtrRunChar(ch)) {
        val start = i
        while (i < reversedChars.length && isLtrRunChar(reversedChars[i])) {
          i++
        }
        val run = reversedChars.substring(start, i)
        // Only flip back contiguous runs that contain at least one digit or ASCII letter
        if (run.any { it.isDigit() || it in 'A'..'Z' || it in 'a'..'z' }) {
          result.append(run.reversed())
        } else {
          result.append(run)
        }
      } else {
        result.append(mirrorDelimiterChar(ch))
        i++
      }
    }
    return result.toString()
  }

  private fun mirrorTokenDelimiters(token: String): String {
    if (!token.any { it == '«' || it == '»' || it == '(' || it == ')' || it == '[' || it == ']' }) {
      return token
    }
    return buildString(token.length) {
      for (ch in token) {
        append(mirrorDelimiterChar(ch))
      }
    }
  }

  private fun mirrorDelimiterChar(ch: Char): Char = when (ch) {
    '«' -> '»'
    '»' -> '«'
    '(' -> ')'
    ')' -> '('
    '[' -> ']'
    ']' -> '['
    else -> ch
  }

  private fun isLtrRunChar(ch: Char): Boolean {
    return ch.isDigit() ||
      ch in 'A'..'Z' ||
      ch in 'a'..'z' ||
      ch == '.' ||
      ch == '/' ||
      ch == ':' ||
      ch == '-'
  }

  private fun lightNormalizeForAnalysis(text: String): String {
    return Normalizer.normalize(text, Normalizer.Form.NFKC)
      .replace('\u064A', '\u06CC')
      .replace('\u0649', '\u06CC')
      .replace('\u0643', '\u06A9')
      .replace("\u0640", "")
  }

  private fun tokenizeWords(text: String): List<String> {
    return text.split(Regex("\\s+"))
      .map { stripPunctuation(it) }
      .filter { it.isNotEmpty() }
  }

  private fun stripPunctuation(token: String): String {
    return token.trim { ch ->
      !isPersianLetter(ch) && !ch.isLetterOrDigit() && ch != '\u200C'
    }
  }

  private fun isPersianLetter(ch: Char): Boolean {
    val code = ch.code
    return code in 0x0621..0x064A || code in 0x066E..0x06D5
  }
}
