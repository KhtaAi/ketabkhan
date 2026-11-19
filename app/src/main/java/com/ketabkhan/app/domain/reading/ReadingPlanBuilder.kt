package com.ketabkhan.app.domain.reading

import com.ketabkhan.app.data.pdf.DocumentBlock
import com.ketabkhan.app.data.pdf.DocumentBlockType
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.domain.text.SentenceSplitter

enum class TitlePauseDuration(
  val millis: Long,
  val persianLabel: String,
) {
  SHORT(millis = 450L, persianLabel = "کوتاه"),
  MEDIUM(millis = 850L, persianLabel = "متوسط"),
  LONG(millis = 1300L, persianLabel = "بلند"),
}

data class ReadingSettings(
  val readTocEntries: Boolean = true,
  val readHeadersAndFooters: Boolean = false,
  val readFootnotes: Boolean = false,
  val readPageNumbers: Boolean = false,
  val titlePauseDuration: TitlePauseDuration = TitlePauseDuration.LONG,
)

data class ReadingItem(
  val order: Int,
  val role: DocumentBlockType,
  val spokenText: String,
  val pageNumber: Int,
  val pauseAfterMillis: Long,
  val sourceBlockId: String,
)

data class SkippedReadingBlock(
  val block: DocumentBlock,
  val pageNumber: Int,
  val reason: String,
)

data class ReadingPlan(
  val items: List<ReadingItem>,
  val skippedBlocks: List<SkippedReadingBlock>,
  val startPage: Int,
  val endPage: Int,
)

/**
 * Converts structural [DocumentBlock]s inside a chosen page range into an ordered list of
 * role-tagged [ReadingItem]s ready for downstream TTS consumption, plus a transparent list of
 * [SkippedReadingBlock]s matching the user's [ReadingSettings].
 */
object ReadingPlanBuilder {

  private const val BODY_SENTENCE_PAUSE_MS = 350L
  private const val CENTERED_LINE_PAUSE_MS = 650L
  private const val TOC_ENTRY_PAUSE_MS = 550L
  private const val METADATA_PAUSE_MS = 450L

  fun build(
    pages: List<ExtractedPage>,
    startPage: Int,
    endPage: Int,
    settings: ReadingSettings = ReadingSettings(),
  ): ReadingPlan {
    val selectedPages = pages
      .filter { it.pageNumber in startPage..endPage }
      .sortedBy { it.pageNumber }

    val items = mutableListOf<ReadingItem>()
    val skipped = mutableListOf<SkippedReadingBlock>()
    var orderCounter = 1
    var inTocSequence = false

    for (page in selectedPages) {
      val pageBlocks = if (page.blocks.isNotEmpty()) {
        page.blocks
      } else if (page.cleanedText.isNotBlank()) {
        // Legacy fallback before cache upgrade to schema v3
        page.cleanedText
          .split(Regex("\\n\\s*\\n+"))
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .mapIndexed { idx, para ->
            DocumentBlock(
              id = "legacy_p${page.pageNumber}_b$idx",
              type = DocumentBlockType.BODY_PARAGRAPH,
              text = para,
            )
          }
      } else {
        emptyList()
      }

      for (block in pageBlocks) {
        if (block.type != DocumentBlockType.TOC_ENTRY && block.type != DocumentBlockType.EMPTY) {
          if (block.type !in setOf(DocumentBlockType.HEADER, DocumentBlockType.FOOTER, DocumentBlockType.PAGE_NUMBER)) {
            inTocSequence = false
          }
        }

        when (block.type) {
          DocumentBlockType.EMPTY -> Unit

          DocumentBlockType.PAGE_TITLE -> {
            val clean = block.text.trim()
            if (clean.isNotEmpty()) {
              items.add(
                ReadingItem(
                  order = orderCounter++,
                  role = DocumentBlockType.PAGE_TITLE,
                  spokenText = clean,
                  pageNumber = page.pageNumber,
                  pauseAfterMillis = settings.titlePauseDuration.millis,
                  sourceBlockId = block.id,
                )
              )
            }
          }

          DocumentBlockType.SUBTITLE -> {
            val clean = block.text.trim()
            if (clean.isNotEmpty()) {
              items.add(
                ReadingItem(
                  order = orderCounter++,
                  role = DocumentBlockType.SUBTITLE,
                  spokenText = clean,
                  pageNumber = page.pageNumber,
                  pauseAfterMillis = maxOf(400L, (settings.titlePauseDuration.millis * 0.75f).toLong()),
                  sourceBlockId = block.id,
                )
              )
            }
          }

          DocumentBlockType.TOC_ENTRY -> {
            if (!settings.readTocEntries) {
              skipped.add(
                SkippedReadingBlock(
                  block = block,
                  pageNumber = page.pageNumber,
                  reason = "خواندن فهرست در تنظیمات خاموش است",
                )
              )
            } else {
              if (!inTocSequence) {
                items.add(
                  ReadingItem(
                    order = orderCounter++,
                    role = DocumentBlockType.TOC_ENTRY,
                    spokenText = "فهرست کتاب",
                    pageNumber = page.pageNumber,
                    pauseAfterMillis = 650L,
                    sourceBlockId = "${block.id}_toc_announce",
                  )
                )
                inTocSequence = true
              }

              val entryTitle = (block.title ?: block.text).trim()
              val refPage = block.referencePageNumber
              val spoken = if (refPage != null) {
                "$entryTitle، صفحهٔ ${PersianDigits.formatInt(refPage)}"
              } else {
                entryTitle
              }
              if (spoken.isNotBlank()) {
                items.add(
                  ReadingItem(
                    order = orderCounter++,
                    role = DocumentBlockType.TOC_ENTRY,
                    spokenText = spoken,
                    pageNumber = page.pageNumber,
                    pauseAfterMillis = TOC_ENTRY_PAUSE_MS,
                    sourceBlockId = block.id,
                  )
                )
              }
            }
          }

          DocumentBlockType.CENTERED_SHORT_LINE -> {
            val clean = block.text.trim()
            if (clean.isNotEmpty()) {
              items.add(
                ReadingItem(
                  order = orderCounter++,
                  role = DocumentBlockType.CENTERED_SHORT_LINE,
                  spokenText = clean,
                  pageNumber = page.pageNumber,
                  pauseAfterMillis = CENTERED_LINE_PAUSE_MS,
                  sourceBlockId = block.id,
                )
              )
            }
          }

          DocumentBlockType.BODY_PARAGRAPH -> {
            val sentences = SentenceSplitter.split(block.text)
            for ((sIdx, sentence) in sentences.withIndex()) {
              items.add(
                ReadingItem(
                  order = orderCounter++,
                  role = DocumentBlockType.BODY_PARAGRAPH,
                  spokenText = sentence,
                  pageNumber = page.pageNumber,
                  pauseAfterMillis = BODY_SENTENCE_PAUSE_MS,
                  sourceBlockId = "${block.id}_s$sIdx",
                )
              )
            }
          }

          DocumentBlockType.HEADER,
          DocumentBlockType.FOOTER -> {
            if (!settings.readHeadersAndFooters) {
              skipped.add(
                SkippedReadingBlock(
                  block = block,
                  pageNumber = page.pageNumber,
                  reason = if (block.type == DocumentBlockType.HEADER) {
                    "سرصفحه پیش‌فرض خوانده نمی‌شود"
                  } else {
                    "پاصفحه پیش‌فرض خوانده نمی‌شود"
                  },
                )
              )
            } else {
              val clean = block.text.trim()
              if (clean.isNotEmpty()) {
                items.add(
                  ReadingItem(
                    order = orderCounter++,
                    role = block.type,
                    spokenText = clean,
                    pageNumber = page.pageNumber,
                    pauseAfterMillis = METADATA_PAUSE_MS,
                    sourceBlockId = block.id,
                  )
                )
              }
            }
          }

          DocumentBlockType.PAGE_NUMBER -> {
            if (!settings.readPageNumbers) {
              skipped.add(
                SkippedReadingBlock(
                  block = block,
                  pageNumber = page.pageNumber,
                  reason = "شمارهٔ صفحه پیش‌فرض خوانده نمی‌شود",
                )
              )
            } else {
              val num = PersianDigits.parseFlexibleInt(block.text)
              val spoken = if (num != null) {
                "صفحهٔ ${PersianDigits.formatInt(num)}"
              } else {
                block.text.trim()
              }
              if (spoken.isNotEmpty()) {
                items.add(
                  ReadingItem(
                    order = orderCounter++,
                    role = DocumentBlockType.PAGE_NUMBER,
                    spokenText = spoken,
                    pageNumber = page.pageNumber,
                    pauseAfterMillis = METADATA_PAUSE_MS,
                    sourceBlockId = block.id,
                  )
                )
              }
            }
          }

          DocumentBlockType.FOOTNOTE -> {
            if (!settings.readFootnotes) {
              skipped.add(
                SkippedReadingBlock(
                  block = block,
                  pageNumber = page.pageNumber,
                  reason = "پاورقی پیش‌فرض خوانده نمی‌شود",
                )
              )
            } else {
              val sentences = SentenceSplitter.split(block.text)
              for ((sIdx, sentence) in sentences.withIndex()) {
                items.add(
                  ReadingItem(
                    order = orderCounter++,
                    role = DocumentBlockType.FOOTNOTE,
                    spokenText = sentence,
                    pageNumber = page.pageNumber,
                    pauseAfterMillis = BODY_SENTENCE_PAUSE_MS + 50L,
                    sourceBlockId = "${block.id}_fn$sIdx",
                  )
                )
              }
            }
          }

          DocumentBlockType.REFERENCE_MARK -> {
            if (!settings.readFootnotes) {
              skipped.add(
                SkippedReadingBlock(
                  block = block,
                  pageNumber = page.pageNumber,
                  reason = "علامت ارجاع پیش‌فرض خوانده نمی‌شود",
                )
              )
            } else {
              val clean = block.text.trim()
              if (clean.isNotEmpty()) {
                items.add(
                  ReadingItem(
                    order = orderCounter++,
                    role = DocumentBlockType.REFERENCE_MARK,
                    spokenText = clean,
                    pageNumber = page.pageNumber,
                    pauseAfterMillis = 250L,
                    sourceBlockId = block.id,
                  )
                )
              }
            }
          }
        }
      }
    }

    return ReadingPlan(
      items = items,
      skippedBlocks = skipped,
      startPage = startPage,
      endPage = endPage,
    )
  }
}
