package com.ketabkhan.app.data.pdf

import android.content.Context
import com.ketabkhan.app.domain.text.PersianTextCleaner
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ExtractionCandidateScore(
  val totalScore: Float,
  val persianLetterRatio: Float,
  val functionWordCount: Int,
  val wordCount: Int,
  val brokenPrefixCount: Int,
  val guillemetIssueCount: Int,
  val digitAdjacencyCount: Int,
  val corruptedCharCount: Int,
  val hasCorruptionSignals: Boolean,
)

object PersianExtractionScorer {

  private const val FALLBACK_SCORE_THRESHOLD = 48f

  private val independentFunctionTokens = setOf(
    "از", "به", "که", "را", "در", "و", "است", "بود",
  )

  private val brokenShortTokens = setOf("م", "نم", "نو")
  private val brokenMiSplitRegex = Regex("(?<![\\u0621-\\u06D5])(?:نم|م)\\s+ی[\\u0621-\\u06D5]{2,}")
  private val shiftedAlifMaddaRegex = Regex("[\\u0621-\\u06D5]{2,}\\s+[بپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی]آ[\\u0621-\\u06D5]{2,}")
  private val digitGluedToLetterRegex = Regex(
    "(?:[0-9۰-۹٠-٩][\\u0621-\\u064A\\u066E-\\u06D5]|[\\u0621-\\u064A\\u066E-\\u06D5][0-9۰-۹٠-٩])"
  )

  fun scoreCandidate(rawText: String): ExtractionCandidateScore {
    if (rawText.isBlank()) {
      return ExtractionCandidateScore(
        totalScore = 0f,
        persianLetterRatio = 0f,
        functionWordCount = 0,
        wordCount = 0,
        brokenPrefixCount = 0,
        guillemetIssueCount = 0,
        digitAdjacencyCount = 0,
        corruptedCharCount = 0,
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

    for (ch in normalized) {
      if (ch.isWhitespace() || ch == '\u200C') continue
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

    val wordCount = strippedTokens.size
    val functionDensity = if (wordCount > 0) {
      (functionWordCount.toFloat() / wordCount.toFloat()).coerceAtMost(0.40f) / 0.40f
    } else {
      0f
    }

    var score = 0f
    // 1. Persian-Arabic character ratio (0..38 pts)
    score += persianRatio * 38f
    // 2. Presence of genuine independent Persian function words (0..32 pts)
    score += (functionDensity * 22f) + minOf(functionWordCount, 5) * 2f
    // 3. Penalty for broken short/shifted prefixes ("م یزد", "نو شآفرین")
    score -= brokenShortCount * 10f + brokenPatternCount * 14f
    // 4. Penalty for mismatched or inverted guillemets
    score -= guillemetIssues * 10f
    // 5. Penalty for digit glued to Persian letter ("۱۲۹۹رضاخان")
    score -= digitAdjacencyCount * 5f
    // 6. Penalty for replacement/PUA/non-Persian corruption characters
    val corruptedRatio = if (totalNonWhitespace > 0) corruptedCount.toFloat() / totalNonWhitespace.toFloat() else 0f
    score -= corruptedRatio * 80f + corruptedCount * 2f

    // Extra bonus when Persian prose has multi-character words and no structural splits
    val avgWordLen = if (wordCount > 0) strippedTokens.sumOf { it.length }.toFloat() / wordCount.toFloat() else 0f
    if (avgWordLen in 3.2f..7.5f && brokenPrefixTotal == 0) {
      score += 8f
    }

    val hasCorruptionSignals = brokenPrefixTotal > 0 ||
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
      brokenPrefixCount = brokenPrefixTotal,
      guillemetIssueCount = guillemetIssues,
      digitAdjacencyCount = digitAdjacencyCount,
      corruptedCharCount = corruptedCount,
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

sealed interface ExtractionResult {
  data class Success(
    val resolvedTitle: String,
    val pages: List<ExtractedPage>,
    val emptyPagesCount: Int,
    val nonEmptyPagesCount: Int,
    val totalChars: Int,
    val isPasswordProtected: Boolean,
    val qualityReport: BookQualityReport,
  ) : ExtractionResult

  data object PasswordRequired : ExtractionResult

  data class InvalidPassword(val message: String) : ExtractionResult

  data class Error(val message: String) : ExtractionResult
}

class PdfTextExtractor(private val context: Context) {

  companion object {
    private val pdfBoxInitialized = AtomicBoolean(false)
    private const val SCRATCH_MEMORY_LIMIT_BYTES = 8L * 1024 * 1024

    fun ensurePdfBoxInitialized(context: Context) {
      if (pdfBoxInitialized.compareAndSet(false, true)) {
        try {
          PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Throwable) {
          // Allows unit-test fallback if assets are unavailable in pure JVM
        }
      }
    }

    fun analyzeBookQuality(pages: List<ExtractedPage>): BookQualityReport {
      val totalPages = pages.size
      if (totalPages == 0) {
        return BookQualityReport(
          status = PdfQualityStatus.LIKELY_SCANNED_IMAGE,
          warningMessage =
            "هیچ صفحه‌ای در این فایل PDF یافت نشد.",
          persianLetterRatio = 0f,
          corruptedCharRatio = 0f,
        )
      }

      val nonEmptyPages = pages.count { !it.isEmpty }
      val totalChars = pages.sumOf { it.charCount }
      val avgCharsPerPage = totalChars.toFloat() / totalPages.toFloat()
      val nonEmptyPageRatio = nonEmptyPages.toFloat() / totalPages.toFloat()

      if (nonEmptyPages == 0 || (nonEmptyPageRatio < 0.25f && avgCharsPerPage < 40f)) {
        return BookQualityReport(
          status = PdfQualityStatus.LIKELY_SCANNED_IMAGE,
          warningMessage =
            "متن قابل استخراج این PDF بسیار کم یا صفر است و احتمالاً فایل تصویری (اسکن‌شده) است. استخراج متن از تصویر نیاز به OCR دارد و OCR در این اپلیکیشن پشتیبانی نمی‌شود.",
          persianLetterRatio = 0f,
          corruptedCharRatio = 0f,
        )
      }

      var totalNonWhitespace = 0
      var corruptedOrUnknownChars = 0
      var totalLetters = 0
      var persianArabicLetters = 0

      for (page in pages) {
        if (page.isEmpty) continue
        for (ch in page.cleanedText) {
          if (ch.isWhitespace() || ch == '\u200C') continue
          totalNonWhitespace++

          val code = ch.code
          val isReplacementOrPua = ch == '\uFFFD' ||
            code in 0xE000..0xF8FF ||
            code in 0x00C0..0x00FF ||
            Character.getType(ch) == Character.CONTROL.toInt() ||
            Character.getType(ch) == Character.PRIVATE_USE.toInt() ||
            Character.getType(ch) == Character.UNASSIGNED.toInt()

          if (isReplacementOrPua) {
            corruptedOrUnknownChars++
          }

          if (ch.isLetter()) {
            totalLetters++
            if (isPersianOrArabicLetter(ch)) {
              persianArabicLetters++
            }
          }
        }
      }

      if (totalNonWhitespace == 0) {
        return BookQualityReport(
          status = PdfQualityStatus.LIKELY_SCANNED_IMAGE,
          warningMessage =
            "این PDF تصویری است و متن قابل استخراجی ندارد. استخراج متن از تصویر نیازمند OCR است و OCR در این اپلیکیشن پشتیبانی نمی‌شود.",
          persianLetterRatio = 0f,
          corruptedCharRatio = 0f,
        )
      }

      val corruptedRatio = corruptedOrUnknownChars.toFloat() / totalNonWhitespace.toFloat()
      val persianLetterRatio = if (totalLetters > 0) {
        persianArabicLetters.toFloat() / totalLetters.toFloat()
      } else {
        0f
      }
      val outsidePersianArabicRatio = if (totalLetters > 0) {
        (totalLetters - persianArabicLetters).toFloat() / totalLetters.toFloat()
      } else {
        1f
      }

      val isCorruptedMapping = totalNonWhitespace >= 10 && (
        corruptedRatio > 0.10f ||
          outsidePersianArabicRatio > 0.55f ||
          (totalLetters < totalNonWhitespace * 0.25f)
        )

      return if (isCorruptedMapping) {
        BookQualityReport(
          status = PdfQualityStatus.CORRUPTED_FONT_MAPPING,
          warningMessage =
            "نگاشت فونت این PDF خراب است یا بخش بزرگی از کاراکترها جایگزین، ناشناخته یا خارج از الفبای فارسی-عربی هستند. متن استخراج‌شده ممکن است قابل خواندن نباشد.",
          persianLetterRatio = persianLetterRatio,
          corruptedCharRatio = corruptedRatio,
        )
      } else {
        BookQualityReport(
          status = PdfQualityStatus.OK,
          warningMessage = null,
          persianLetterRatio = persianLetterRatio,
          corruptedCharRatio = corruptedRatio,
        )
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

    fun isReadableTitle(title: String): Boolean {
      val trimmed = title.trim()
      if (trimmed.length < 2) return false
      var bad = 0
      var total = 0
      for (ch in trimmed) {
        if (ch.isWhitespace()) continue
        total++
        val code = ch.code
        if (ch == '\uFFFD' || code in 0xE000..0xF8FF || code in 0x00C0..0x00FF) {
          bad++
        }
      }
      return total > 0 && bad.toFloat() / total.toFloat() < 0.20f
    }
  }

  suspend fun extractBook(
    pdfFile: File,
    fallbackTitle: String,
    password: String? = null,
    onProgress: suspend (ExtractionProgress) -> Unit = {},
  ): ExtractionResult = withContext(Dispatchers.IO) {
    ensurePdfBoxInitialized(context)

    if (!pdfFile.exists() || !pdfFile.isFile) {
      return@withContext ExtractionResult.Error("فایل PDF در حافظه داخلی یافت نشد.")
    }

    try {
      val memorySetting = MemoryUsageSetting.setupMixed(SCRATCH_MEMORY_LIMIT_BYTES)
      PDDocument.load(pdfFile, password ?: "", memorySetting).use { document ->
        val totalPages = document.numberOfPages
        val rawMetadataTitle = runCatching { document.documentInformation?.title }.getOrNull()
        val cleanedMetaTitle = rawMetadataTitle?.let {
          PersianTextCleaner.cleanInline(PersianReadingOrderRepairer.repairLine(it))
        }
        val resolvedTitle = if (!cleanedMetaTitle.isNullOrBlank() && isReadableTitle(cleanedMetaTitle)) {
          cleanedMetaTitle
        } else {
          fallbackTitle
        }

        // Candidate 1: Position-sorted with tuned kerning tolerance to reduce spurious ZWNJ spaces
        val sortedStripper = PDFTextStripper().apply {
          sortByPosition = true
          spacingTolerance = 0.58f
          averageCharTolerance = 0.36f
        }

        // Candidate 2: Stream-order extractor evaluated only when Candidate 1 shows weak/corrupted score
        var streamStripper: PDFTextStripper? = null

        val selectedRawPages = ArrayList<String>(totalPages)
        val orderRepairedPages = ArrayList<String>(totalPages)

        for (page in 1..totalPages) {
          coroutineContext.ensureActive()
          sortedStripper.startPage = page
          sortedStripper.endPage = page
          val candidateA = sortedStripper.getText(document) ?: ""
          val scoreA = PersianExtractionScorer.scoreCandidate(candidateA)

          val chosenRaw = if (PersianExtractionScorer.shouldEvaluateFallback(scoreA, candidateA)) {
            coroutineContext.ensureActive()
            val fallbackStripper = streamStripper ?: PDFTextStripper().apply {
              sortByPosition = false
              spacingTolerance = 0.55f
              averageCharTolerance = 0.35f
            }.also { streamStripper = it }
            fallbackStripper.startPage = page
            fallbackStripper.endPage = page
            val candidateB = fallbackStripper.getText(document) ?: ""
            val scoreB = PersianExtractionScorer.scoreCandidate(candidateB)
            if (scoreB.totalScore > scoreA.totalScore) candidateB else candidateA
          } else {
            candidateA
          }

          selectedRawPages.add(chosenRaw)
          orderRepairedPages.add(PersianReadingOrderRepairer.repairPage(chosenRaw))

          onProgress(
            ExtractionProgress(
              currentPage = page,
              totalPages = totalPages,
              bookTitle = resolvedTitle,
            )
          )
        }

        val cleanedPages = PersianTextCleaner.cleanBookPages(orderRepairedPages)
        val extractedPages = cleanedPages.mapIndexed { index, cleaned ->
          val rawText = selectedRawPages.getOrElse(index) { "" }.trim()
          ExtractedPage(
            pageNumber = cleaned.pageNumber,
            cleanedText = cleaned.cleanedText,
            isEmpty = cleaned.isEmpty,
            charCount = cleaned.cleanedText.length,
            rawText = rawText,
          )
        }

        val emptyCount = extractedPages.count { it.isEmpty }
        val nonEmptyCount = extractedPages.size - emptyCount
        val totalChars = extractedPages.sumOf { it.charCount }
        val qualityReport = analyzeBookQuality(extractedPages)

        ExtractionResult.Success(
          resolvedTitle = resolvedTitle,
          pages = extractedPages,
          emptyPagesCount = emptyCount,
          nonEmptyPagesCount = nonEmptyCount,
          totalChars = totalChars,
          isPasswordProtected = password != null || document.isEncrypted,
          qualityReport = qualityReport,
        )
      }
    } catch (_: InvalidPasswordException) {
      if (password == null) {
        ExtractionResult.PasswordRequired
      } else {
        ExtractionResult.InvalidPassword("رمز عبور واردشده نادرست است. لطفاً دوباره تلاش کنید.")
      }
    } catch (t: Throwable) {
      ExtractionResult.Error(
        "خطا در پردازش PDF: ${t.localizedMessage ?: "امکان استخراج متن این فایل وجود ندارد."}"
      )
    }
  }
}
