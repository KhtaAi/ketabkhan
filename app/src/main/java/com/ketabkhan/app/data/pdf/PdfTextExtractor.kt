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



sealed interface ExtractionResult {
  data class Success(
    val resolvedTitle: String,
    val pages: List<ExtractedPage>,
    val emptyPagesCount: Int,
    val nonEmptyPagesCount: Int,
    val totalChars: Int,
    val isPasswordProtected: Boolean,
    val qualityReport: BookQualityReport,
    val ligatureRepairsCount: Int = 0,
    val detachedFinalRepairsCount: Int = 0,
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

        // Candidate 1 (Primary): Line-based RTL-aware stripper keeping ligatures atomic and capturing typography/layout
        val rtlLineStripper = RtlLinePdfStripper()

        // Fallbacks: Position-sorted & stream-order PDFTextStrippers evaluated only on weak/corrupted RTL candidate
        var sortedStripper: PDFTextStripper? = null
        var streamStripper: PDFTextStripper? = null

        val winningLayouts = ArrayList<ExtractedPageLayout>(totalPages)

        for (page in 1..totalPages) {
          coroutineContext.ensureActive()
          val rtlLayout = rtlLineStripper.extractPageLayout(document, page)
          val rtlScore = PersianExtractionScorer.scoreCandidate(rtlLayout.rawText)

          val chosenLayout = if (PersianExtractionScorer.shouldEvaluateFallback(rtlScore, rtlLayout.rawText)) {
            coroutineContext.ensureActive()
            val posStripper = sortedStripper ?: PDFTextStripper().apply {
              sortByPosition = true
              spacingTolerance = 0.58f
              averageCharTolerance = 0.36f
            }.also { sortedStripper = it }
            posStripper.startPage = page
            posStripper.endPage = page
            val candidatePos = posStripper.getText(document) ?: ""
            val scorePos = PersianExtractionScorer.scoreCandidate(candidatePos)

            val stStripper = streamStripper ?: PDFTextStripper().apply {
              sortByPosition = false
              spacingTolerance = 0.55f
              averageCharTolerance = 0.35f
            }.also { streamStripper = it }
            stStripper.startPage = page
            stStripper.endPage = page
            val candidateStream = stStripper.getText(document) ?: ""
            val scoreStream = PersianExtractionScorer.scoreCandidate(candidateStream)

            val (bestFallbackText, bestFallbackScore) = if (scoreStream.totalScore > scorePos.totalScore) {
              candidateStream to scoreStream
            } else {
              candidatePos to scorePos
            }

            if (bestFallbackScore.totalScore > rtlScore.totalScore + 2f) {
              DocumentStructureAnalyzer.buildSyntheticLayoutFromRawText(
                pageNumber = page,
                rawText = bestFallbackText,
                pageWidth = rtlLayout.pageWidth,
                pageHeight = rtlLayout.pageHeight,
              )
            } else {
              rtlLayout
            }
          } else {
            rtlLayout
          }

          winningLayouts.add(chosenLayout)

          onProgress(
            ExtractionProgress(
              currentPage = page,
              totalPages = totalPages,
              bookTitle = resolvedTitle,
            )
          )
        }

        val analyzedPages = DocumentStructureAnalyzer.analyzeBook(winningLayouts)
        val extractedPages = analyzedPages.mapIndexed { index, analyzed ->
          val layout = winningLayouts.getOrElse(index) {
            ExtractedPageLayout(analyzed.pageNumber, emptyList(), "", 595f, 842f)
          }
          val rawText = layout.rawText.trim()
          ExtractedPage(
            pageNumber = analyzed.pageNumber,
            cleanedText = analyzed.flatCleanedText,
            isEmpty = analyzed.isEmpty,
            charCount = analyzed.flatCleanedText.length,
            rawText = rawText,
            blocks = analyzed.blocks,
            spacingMetrics = layout.spacingMetrics,
          )
        }

        val emptyCount = extractedPages.count { it.isEmpty }
        val nonEmptyCount = extractedPages.size - emptyCount
        val totalChars = extractedPages.sumOf { it.charCount }
        val qualityReport = analyzeBookQuality(extractedPages)
        val totalLigatureRepairs = analyzedPages.sumOf { it.ligatureFixesCount }
        val totalDetachedRepairs = analyzedPages.sumOf { it.detachedLetterFixesCount }

        ExtractionResult.Success(
          resolvedTitle = resolvedTitle,
          pages = extractedPages,
          emptyPagesCount = emptyCount,
          nonEmptyPagesCount = nonEmptyCount,
          totalChars = totalChars,
          isPasswordProtected = password != null || document.isEncrypted,
          qualityReport = qualityReport,
          ligatureRepairsCount = totalLigatureRepairs,
          detachedFinalRepairsCount = totalDetachedRepairs,
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
