package com.ketabkhan.app.domain.reading

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ketabkhan.app.data.pdf.BlockLanguage
import com.ketabkhan.app.data.pdf.DocumentBlock
import com.ketabkhan.app.data.pdf.DocumentBlockType
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.data.reading.ReadingSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadingPlanBuilderTest {

  private fun sampleStructuredPages(): List<ExtractedPage> {
    val page1 = ExtractedPage(
      pageNumber = 1,
      cleanedText = "فهرست مطالب\n\nپیش‌گفتار — صفحهٔ ۵",
      rawText = "...",
      isEmpty = false,
      charCount = 35,
      blocks = listOf(
        DocumentBlock(id = "p1_h", type = DocumentBlockType.HEADER, text = "تاریخ ایران"),
        DocumentBlock(id = "p1_t", type = DocumentBlockType.PAGE_TITLE, text = "فهرست مطالب"),
        DocumentBlock(
          id = "p1_toc1",
          type = DocumentBlockType.TOC_ENTRY,
          text = "پیش‌گفتار — صفحهٔ ۵",
          title = "پیش‌گفتار",
          referencePageNumber = 5,
        ),
        DocumentBlock(
          id = "p1_toc2",
          type = DocumentBlockType.TOC_ENTRY,
          text = "فصل اول — صفحهٔ ۱۲",
          title = "فصل اول",
          referencePageNumber = 12,
        ),
        DocumentBlock(id = "p1_pn", type = DocumentBlockType.PAGE_NUMBER, text = "۱"),
      ),
    )

    val page2 = ExtractedPage(
      pageNumber = 2,
      cleanedText = "فصل اول\n\nاین کتاب دربارهٔ میلانی است. این جملهٔ دوم است.",
      rawText = "...",
      isEmpty = false,
      charCount = 55,
      blocks = listOf(
        DocumentBlock(id = "p2_h", type = DocumentBlockType.HEADER, text = "تاریخ ایران"),
        DocumentBlock(id = "p2_t", type = DocumentBlockType.PAGE_TITLE, text = "فصل اول"),
        DocumentBlock(id = "p2_c1", type = DocumentBlockType.CENTERED_SHORT_LINE, text = "تقدیم به پدرم"),
        DocumentBlock(id = "p2_c2", type = DocumentBlockType.CENTERED_SHORT_LINE, text = "و یاد یاران"),
        DocumentBlock(
          id = "p2_b1",
          type = DocumentBlockType.BODY_PARAGRAPH,
          text = "این کتاب دربارهٔ میلانی است. این جملهٔ دوم است.",
        ),
        DocumentBlock(id = "p2_fn", type = DocumentBlockType.FOOTNOTE, text = "۱. توضیح زیرنویس."),
        DocumentBlock(id = "p2_f", type = DocumentBlockType.FOOTER, text = "نشر معاصر"),
        DocumentBlock(id = "p2_pn", type = DocumentBlockType.PAGE_NUMBER, text = "۲"),
      ),
    )

    return listOf(page1, page2)
  }

  @Test
  fun `builds default structure-aware plan skipping headers footers footnotes pageNumbers and formatting TOC entries`() {
    val pages = sampleStructuredPages()
    val plan = ReadingPlanBuilder.build(
      pages = pages,
      startPage = 1,
      endPage = 2,
      settings = ReadingSettings(), // defaults: TOC=true, Headers/Footers/Footnotes/PageNumbers=false, pause=LONG
    )

    // Verify order & roles:
    // 1. TOC title
    // 2. "فهرست کتاب" announcement before first TOC entry
    // 3. "پیش‌گفتار، صفحهٔ ۵"
    // 4. "فصل اول، صفحهٔ ۱۲"
    // 5. Page 2 PAGE_TITLE ("فصل اول") with LONG pause (1300ms)
    // 6. CENTERED_SHORT_LINE ("تقدیم به پدرم")
    // 7. CENTERED_SHORT_LINE ("و یاد یاران")
    // 8-9. 2 sentences from BODY_PARAGRAPH
    assertEquals(9, plan.items.size)
    assertEquals(1, plan.items.first().order)
    assertEquals(9, plan.items.last().order)

    assertEquals(DocumentBlockType.PAGE_TITLE, plan.items[0].role)
    assertEquals("فهرست مطالب", plan.items[0].spokenText)

    assertEquals(DocumentBlockType.TOC_ENTRY, plan.items[1].role)
    assertEquals("فهرست کتاب", plan.items[1].spokenText)

    assertEquals(DocumentBlockType.TOC_ENTRY, plan.items[2].role)
    assertEquals("پیش‌گفتار، صفحهٔ ۵", plan.items[2].spokenText)

    assertEquals("فصل اول، صفحهٔ ۱۲", plan.items[3].spokenText)

    assertEquals(DocumentBlockType.PAGE_TITLE, plan.items[4].role)
    assertEquals(TitlePauseDuration.LONG.millis, plan.items[4].pauseAfterMillis)

    assertEquals(DocumentBlockType.CENTERED_SHORT_LINE, plan.items[5].role)
    assertEquals("تقدیم به پدرم", plan.items[5].spokenText)
    assertEquals(DocumentBlockType.CENTERED_SHORT_LINE, plan.items[6].role)
    assertEquals("و یاد یاران", plan.items[6].spokenText)

    assertEquals(DocumentBlockType.BODY_PARAGRAPH, plan.items[7].role)
    assertEquals("این کتاب دربارهٔ میلانی است.", plan.items[7].spokenText)
    assertEquals(DocumentBlockType.BODY_PARAGRAPH, plan.items[8].role)
    assertEquals("این جملهٔ دوم است.", plan.items[8].spokenText)

    // Verify skipped items preserved cleanly with reasons (2 headers + 2 page numbers + 1 footnote + 1 footer = 6)
    assertEquals(6, plan.skippedBlocks.size)
    val skippedRoles = plan.skippedBlocks.map { it.block.type }.toSet()
    assertTrue(DocumentBlockType.HEADER in skippedRoles)
    assertTrue(DocumentBlockType.FOOTER in skippedRoles)
    assertTrue(DocumentBlockType.FOOTNOTE in skippedRoles)
    assertTrue(DocumentBlockType.PAGE_NUMBER in skippedRoles)
  }

  @Test
  fun `respects toggled reading settings and custom title pause durations`() {
    val pages = sampleStructuredPages()
    val customSettings = ReadingSettings(
      readTocEntries = false,
      readHeadersAndFooters = true,
      readFootnotes = true,
      readPageNumbers = true,
      titlePauseDuration = TitlePauseDuration.SHORT,
    )

    val plan = ReadingPlanBuilder.build(
      pages = pages,
      startPage = 1,
      endPage = 2,
      settings = customSettings,
    )

    assertFalse(plan.items.any { it.role == DocumentBlockType.TOC_ENTRY })
    assertTrue(plan.items.any { it.role == DocumentBlockType.HEADER })
    assertTrue(plan.items.any { it.role == DocumentBlockType.FOOTER })
    assertTrue(plan.items.any { it.role == DocumentBlockType.FOOTNOTE })
    assertTrue(plan.items.any { it.role == DocumentBlockType.PAGE_NUMBER && it.spokenText == "صفحهٔ ۲" })

    val titleItem = plan.items.first { it.role == DocumentBlockType.PAGE_TITLE }
    assertEquals(TitlePauseDuration.SHORT.millis, titleItem.pauseAfterMillis)

    // Only the 2 TOC entries should now be skipped
    assertEquals(2, plan.skippedBlocks.size)
    assertTrue(plan.skippedBlocks.all { it.block.type == DocumentBlockType.TOC_ENTRY })
  }

  @Test
  fun `persists and reloads reading settings via SharedPreferences repository`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo1 = ReadingSettingsRepository(context)
    val updated = ReadingSettings(
      readTocEntries = false,
      readHeadersAndFooters = true,
      readFootnotes = true,
      readPageNumbers = true,
      readLatinBlocks = true,
      titlePauseDuration = TitlePauseDuration.MEDIUM,
    )
    repo1.updateSettings(updated)

    val repo2 = ReadingSettingsRepository(context)
    assertEquals(updated, repo2.settings.value)
  }

  @Test
  fun `skips LATIN blocks by default while reading MIXED and PERSIAN blocks, and includes LATIN when toggled on`() {
    val pages = listOf(
      ExtractedPage(
        pageNumber = 1,
        cleanedText = "...",
        rawText = "...",
        isEmpty = false,
        charCount = 120,
        blocks = listOf(
          DocumentBlock(
            id = "p1_latin",
            type = DocumentBlockType.BODY_PARAGRAPH,
            text = "All rights reserved. Permission in writing from the author.",
            language = BlockLanguage.LATIN,
          ),
          DocumentBlock(
            id = "p1_mixed",
            type = DocumentBlockType.BODY_PARAGRAPH,
            text = "این فصل درباره مفاهیم PDF و Unicode است.",
            language = BlockLanguage.MIXED,
          ),
          DocumentBlock(
            id = "p1_persian",
            type = DocumentBlockType.BODY_PARAGRAPH,
            text = "متن فارسی خالص ادامه می‌یابد.",
            language = BlockLanguage.PERSIAN,
          ),
        ),
      )
    )

    val defaultPlan = ReadingPlanBuilder.build(pages, startPage = 1, endPage = 1, settings = ReadingSettings())
    assertEquals(2, defaultPlan.items.size)
    assertTrue(defaultPlan.items.any { it.sourceBlockId.startsWith("p1_mixed") })
    assertTrue(defaultPlan.items.any { it.sourceBlockId.startsWith("p1_persian") })
    assertFalse(defaultPlan.items.any { it.sourceBlockId.startsWith("p1_latin") })
    assertEquals(1, defaultPlan.skippedBlocks.size)
    assertEquals(BlockLanguage.LATIN, defaultPlan.skippedBlocks[0].block.language)
    assertTrue(defaultPlan.skippedBlocks[0].reason.contains("لاتین"))

    val latinEnabledPlan = ReadingPlanBuilder.build(
      pages = pages,
      startPage = 1,
      endPage = 1,
      settings = ReadingSettings(readLatinBlocks = true),
    )
    assertEquals(0, latinEnabledPlan.skippedBlocks.size)
    assertTrue(latinEnabledPlan.items.any { it.sourceBlockId.startsWith("p1_latin") })
    assertTrue(latinEnabledPlan.items.any { it.spokenText.contains("from the author.") })
  }
}
