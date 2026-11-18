package com.ketabkhan.app.data.pdf

enum class PdfQualityStatus {
  OK,
  LIKELY_SCANNED_IMAGE,
  CORRUPTED_FONT_MAPPING,
}

data class BookQualityReport(
  val status: PdfQualityStatus = PdfQualityStatus.OK,
  val warningMessage: String? = null,
  val persianLetterRatio: Float = 1f,
  val corruptedCharRatio: Float = 0f,
)

data class BookMeta(
  val id: String,
  val title: String,
  val fileName: String,
  val sourceUri: String?,
  val pdfFilePath: String,
  val cacheFilePath: String,
  val pageCount: Int,
  val emptyPagesCount: Int,
  val nonEmptyPagesCount: Int,
  val totalChars: Int,
  val fileSizeBytes: Long,
  val addedAtMillis: Long,
  val isPasswordProtected: Boolean,
  val qualityReport: BookQualityReport = BookQualityReport(),
)

data class ExtractedPage(
  val pageNumber: Int,
  val cleanedText: String,
  val isEmpty: Boolean,
  val charCount: Int,
)

data class BookContent(
  val meta: BookMeta,
  val pages: List<ExtractedPage>,
)

data class ExtractionProgress(
  val currentPage: Int,
  val totalPages: Int,
  val bookTitle: String,
) {
  val fraction: Float
    get() = if (totalPages <= 0) 0f else (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
}

sealed interface ImportOutcome {
  data class Success(val book: BookMeta) : ImportOutcome
  data class NeedsPassword(
    val bookId: String,
    val displayTitle: String,
    val errorMessage: String? = null,
  ) : ImportOutcome
  data class Failure(val message: String) : ImportOutcome
}
