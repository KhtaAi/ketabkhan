package com.ketabkhan.app.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStructureAnalyzerTest {

  private fun makeLine(
    text: String,
    y: Float,
    minX: Float = 90f,
    maxX: Float = 505f,
    fontSize: Float = 12f,
    isBold: Boolean = false,
    pageWidth: Float = 595f,
    pageHeight: Float = 842f,
  ): ExtractedPageLayoutLineBuilder = ExtractedPageLayoutLineBuilder(
    text = text,
    baselineY = y,
    minX = minX,
    maxX = maxX,
    fontSize = fontSize,
    isBold = isBold,
    pageWidth = pageWidth,
    pageHeight = pageHeight,
  )

  private data class ExtractedPageLayoutLineBuilder(
    val text: String,
    val baselineY: Float,
    val minX: Float,
    val maxX: Float,
    val fontSize: Float,
    val isBold: Boolean,
    val pageWidth: Float,
    val pageHeight: Float,
  ) {
    fun build(): ExtractedLine {
      val zone = when {
        baselineY <= pageHeight * 0.16f -> PageZone.TOP
        baselineY >= pageHeight * 0.84f -> PageZone.BOTTOM
        else -> PageZone.MIDDLE
      }
      return ExtractedLine(
        text = text,
        rawText = text,
        baselineY = baselineY,
        minX = minX,
        maxX = maxX,
        maxFontSize = fontSize,
        medianFontSize = fontSize,
        isBold = isBold,
        charCount = text.length,
        zone = zone,
        pageWidth = pageWidth,
        pageHeight = pageHeight,
      )
    }
  }

  private fun page(num: Int, vararg builders: ExtractedPageLayoutLineBuilder): ExtractedPageLayout {
    return ExtractedPageLayout(
      pageNumber = num,
      lines = builders.map { it.build() },
      rawText = builders.joinToString("\n") { it.text },
      pageWidth = 595f,
      pageHeight = 842f,
    )
  }

  @Test
  fun `analyzes dedication lines, TOC entries, chapter titles, body paragraphs, repeating headers footers and footnotes`() {
    val pages = listOf(
      // Page 1: Dedication centered two lines + repeating footer + page number
      page(
        1,
        makeLine("سرصفحهٔ کتاب تاریخ ایران", y = 55f, minX = 180f, maxX = 415f, fontSize = 10f),
        makeLine("تقدیم به پدرم", y = 320f, minX = 245f, maxX = 350f, fontSize = 12f),
        makeLine("و یاد یاران مهربان", y = 342f, minX = 235f, maxX = 360f, fontSize = 12f),
        makeLine("نشر پژوهش معاصر", y = 765f, minX = 210f, maxX = 385f, fontSize = 9.5f),
        makeLine("۱", y = 795f, minX = 292f, maxX = 303f, fontSize = 10f),
      ),
      // Page 2: TOC page with heading + dot-leader entries + header/footer/page number
      page(
        2,
        makeLine("سرصفحهٔ کتاب تاریخ ایران", y = 55f, minX = 180f, maxX = 415f, fontSize = 10f),
        makeLine("فهرست مطالب", y = 145f, minX = 220f, maxX = 375f, fontSize = 18f, isBold = true),
        makeLine("پیش‌گفتار ........ ۵", y = 200f, minX = 120f, maxX = 475f, fontSize = 12f),
        makeLine("فصل اول: دوران جوانی ........ ۱۲", y = 228f, minX = 120f, maxX = 475f, fontSize = 12f),
        makeLine("فصل دوم: تحول اجتماعی ........ ۴۸", y = 256f, minX = 120f, maxX = 475f, fontSize = 12f),
        makeLine("نشر پژوهش معاصر", y = 765f, minX = 210f, maxX = 385f, fontSize = 9.5f),
        makeLine("۲", y = 795f, minX = 292f, maxX = 303f, fontSize = 10f),
      ),
      // Page 3: Body page with title, 2 distinct paragraphs, footnote, header, footer, page number
      page(
        3,
        makeLine("سرصفحهٔ کتاب تاریخ ایران", y = 55f, minX = 180f, maxX = 415f, fontSize = 10f),
        makeLine("فصل اول: دوران جوانی", y = 140f, minX = 190f, maxX = 405f, fontSize = 17f, isBold = true),
        makeLine("در این فصل سرگذشت روزهای نخست را مرور می‌کنیم و می‌بینیم که", y = 195f, minX = 85f, maxX = 510f, fontSize = 12f),
        makeLine("تلاش مردم چگونه مسیر تاریخ را تغییر داد.", y = 214f, minX = 210f, maxX = 510f, fontSize = 12f),
        // Clear paragraph vertical gap (214 -> 252 = 38pt vs 19pt line step)
        makeLine("بند دوم پس از فاصلهٔ عمودی آغاز می‌شود و رخدادهای تازه را بازگو", y = 252f, minX = 85f, maxX = 510f, fontSize = 12f),
        makeLine("می‌کند تا خواننده تصویر روشنی داشته باشد.", y = 271f, minX = 220f, maxX = 510f, fontSize = 12f),
        makeLine("۱. این نکته برگرفته از یادداشت‌های خطی آرشیو ملی است.", y = 730f, minX = 140f, maxX = 505f, fontSize = 9.5f),
        makeLine("نشر پژوهش معاصر", y = 765f, minX = 210f, maxX = 385f, fontSize = 9.5f),
        makeLine("۳", y = 795f, minX = 292f, maxX = 303f, fontSize = 10f),
      ),
    )

    val analyzed = DocumentStructureAnalyzer.analyzeBook(pages)
    assertEquals(3, analyzed.size)

    // Page 1: Header, 2 separate CENTERED_SHORT_LINE blocks, Footer, PageNumber — none deleted!
    val p1Types = analyzed[0].blocks.map { it.type }
    assertTrue(DocumentBlockType.HEADER in p1Types)
    assertTrue(DocumentBlockType.FOOTER in p1Types)
    assertTrue(DocumentBlockType.PAGE_NUMBER in p1Types)
    val centered = analyzed[0].blocks.filter { it.type == DocumentBlockType.CENTERED_SHORT_LINE }
    assertEquals(2, centered.size)
    assertEquals("تقدیم به پدرم", centered[0].text)
    assertEquals("و یاد یاران مهربان", centered[1].text)

    // Page 2: TOC title + 3 TOC entries with parsed title & referencePageNumber
    val p2TocEntries = analyzed[1].blocks.filter { it.type == DocumentBlockType.TOC_ENTRY }
    assertEquals(3, p2TocEntries.size)
    assertEquals("پیش‌گفتار", p2TocEntries[0].title)
    assertEquals(5, p2TocEntries[0].referencePageNumber)
    assertEquals("فصل اول: دوران جوانی", p2TocEntries[1].title)
    assertEquals(12, p2TocEntries[1].referencePageNumber)
    assertEquals(48, p2TocEntries[2].referencePageNumber)
    assertTrue(analyzed[1].blocks.any { it.type == DocumentBlockType.PAGE_TITLE && it.text.contains("فهرست") })

    // Page 3: Title separated from body, 2 separate paragraphs, footnote labeled, header & footer & page num retained
    val p3Blocks = analyzed[2].blocks
    assertFalse(analyzed[2].isEmpty)
    assertEquals(1, p3Blocks.count { it.type == DocumentBlockType.PAGE_TITLE })
    val paragraphs = p3Blocks.filter { it.type == DocumentBlockType.BODY_PARAGRAPH }
    assertEquals(2, paragraphs.size)
    assertTrue(paragraphs[0].text.contains("نخست را مرور می‌کنیم"))
    assertTrue(paragraphs[1].text.contains("بند دوم"))
    assertEquals(1, p3Blocks.count { it.type == DocumentBlockType.FOOTNOTE })
    assertEquals(1, p3Blocks.count { it.type == DocumentBlockType.HEADER })
    assertEquals(1, p3Blocks.count { it.type == DocumentBlockType.FOOTER })
    assertEquals(1, p3Blocks.count { it.type == DocumentBlockType.PAGE_NUMBER })
  }

  @Test
  fun `tags PERSIAN LATIN and MIXED blocks accurately without mutating logical English copyright text`() {
    val copyrightLogical = "All rights reserved. No part of this book may be reproduced without permission in writing from the author."
    val mixedLogical = "در این کتاب درباره سیستم های هوشمند Android و PDF سخن می گوییم."
    val pages = listOf(
      page(
        1,
        makeLine("All rights reserved. No part of this book may be reproduced without", y = 200f, minX = 60f, maxX = 500f),
        makeLine("permission in writing from the author.", y = 219f, minX = 60f, maxX = 320f),
      ),
      page(
        2,
        makeLine(mixedLogical, y = 200f, minX = 60f, maxX = 500f),
      ),
      page(
        3,
        makeLine("در آن روزگار، ایرانیان هنوز شناسنامه نداشتند.", y = 200f, minX = 60f, maxX = 500f),
      ),
    )

    val analyzed = DocumentStructureAnalyzer.analyzeBook(pages)
    assertEquals(3, analyzed.size)

    val p1Block = analyzed[0].blocks.first { it.type == DocumentBlockType.BODY_PARAGRAPH }
    assertEquals(BlockLanguage.LATIN, p1Block.language)
    assertEquals(copyrightLogical, p1Block.text)
    assertTrue(p1Block.text.endsWith("from the author."))
    assertFalse(p1Block.text.startsWith("."))

    val p2Block = analyzed[1].blocks.first { it.type == DocumentBlockType.BODY_PARAGRAPH }
    assertEquals(BlockLanguage.MIXED, p2Block.language)

    val p3Block = analyzed[2].blocks.first { it.type == DocumentBlockType.BODY_PARAGRAPH }
    assertEquals(BlockLanguage.PERSIAN, p3Block.language)
  }
}
