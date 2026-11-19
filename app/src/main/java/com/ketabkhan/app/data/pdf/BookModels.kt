package com.ketabkhan.app.data.pdf

const val CURRENT_EXTRACTION_SCHEMA_VERSION = 3

enum class PdfQualityStatus {
  OK,
  LIKELY_SCANNED_IMAGE,
  CORRUPTED_FONT_MAPPING,
}

enum class PageZone {
  TOP,
  MIDDLE,
  BOTTOM,
}

enum class DocumentBlockType(val persianLabel: String) {
  PAGE_TITLE("تیتر"),
  SUBTITLE("زیرتیتر"),
  BODY_PARAGRAPH("بدنه"),
  TOC_ENTRY("فهرست"),
  CENTERED_SHORT_LINE("تقدیم / شعر"),
  FOOTNOTE("پاورقی"),
  HEADER("سرصفحه"),
  FOOTER("پاصفحه"),
  PAGE_NUMBER("شماره صفحه"),
  REFERENCE_MARK("ارجاع"),
  EMPTY("خالی"),
}

data class DocumentBlock(
  val id: String,
  val type: DocumentBlockType,
  val text: String,
  val rawText: String = text,
  val title: String? = null,
  val referencePageNumber: Int? = null,
  val fontSizeSp: Float = 0f,
  val isBold: Boolean = false,
  val zone: PageZone = PageZone.MIDDLE,
  val baselineY: Float = 0f,
  val leftX: Float = 0f,
  val rightX: Float = 0f,
  val lineCount: Int = 1,
)

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
  val cacheSchemaVersion: Int = CURRENT_EXTRACTION_SCHEMA_VERSION,
) {
  val needsReextraction: Boolean
    get() = cacheSchemaVersion < CURRENT_EXTRACTION_SCHEMA_VERSION
}

data class ExtractedPage(
  val pageNumber: Int,
  val cleanedText: String,
  val isEmpty: Boolean,
  val charCount: Int,
  val rawText: String = "",
  val blocks: List<DocumentBlock> = emptyList(),
)

data class BookContent(
  val meta: BookMeta,
  val pages: List<ExtractedPage>,
  val isCacheOutdated: Boolean = meta.needsReextraction,
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
