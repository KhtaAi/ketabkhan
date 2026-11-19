package com.ketabkhan.app.data.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RtlLinePdfStripperRobolectricTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private data class PdfGlyphDraw(
    val unicode: String,
    val xPt: Float,
    val yPt: Float,
    val fontSize: Float = 12f,
    val isBold: Boolean = false,
  )

  @Test
  fun `extracts atomic لا ligature units and detached trailing letters accurately with zero post-fix ligature corruption`() = runBlocking {
    // Visual X coordinates go left->right cleanly, so RTL Persian words start at large X and decrease leftwards:
    // Line 1 (y=720): "میلانی در آلاشت اصلاح و سلام و الله و الف و الان و الم نوشت."
    // Notice we emit "لا" as a SINGLE atomic CID/TextPosition ("\u0644\u0627" / "\uFEFB") so PDFTextStripper-naive would drop or reverse it!
    val line1TokensVisualRightToLeft = listOf(
      "م", "ی", "\uFEFB", "ن", "ی", " ",
      "د", "ر", " ",
      "آ", "\uFEFB", "ش", "ت", " ",
      "ا", "ص", "\uFEFB", "ح", " ",
      "و", " ",
      "س", "\uFEFB", "م", " ",
      "و", " ",
      "ا", "ل", "ل", "ه", " ",
      "و", " ",
      "ا", "ل", "ف", " ",
      "و", " ",
      "ا", "ل", "ا", "ن", " ",
      "و", " ",
      "ا", "ل", "م", " ",
      "ن", "و", "ش", "ت", "."
    )

    val draws = mutableListOf<PdfGlyphDraw>()
    var cursorX = 520f
    for (unit in line1TokensVisualRightToLeft) {
      val step = if (unit == " ") 5.5f else unit.length * 6.0f
      cursorX -= step
      if (unit != " ") {
        draws += PdfGlyphDraw(unicode = unit, xPt = cursorX, yPt = 720f, fontSize = 12f)
      }
    }

    // Line 2 (y=690): Words with slight horizontal glyph detachment inside final letters ("پسر", "دین", "تحرک", "نمایه")
    // Place "پس" at x=490..502, tiny gap 1.1pt, "ر" at x=482.9..488.9; then real word space (6pt), "و", real word space, "دین", "تحرک", "نمایه"
    var x2 = 510f
    // پسر
    draws += PdfGlyphDraw("پ", xPt = x2 - 6f, yPt = 690f)
    draws += PdfGlyphDraw("س", xPt = x2 - 12f, yPt = 690f)
    draws += PdfGlyphDraw("ر", xPt = x2 - 19.1f, yPt = 690f) // tiny 1.1pt gap inside word
    x2 -= 29f // word space (~10pt -> clearly a space)
    draws += PdfGlyphDraw("و", xPt = x2 - 6f, yPt = 690f)
    x2 -= 16f
    // دین
    draws += PdfGlyphDraw("د", xPt = x2 - 6f, yPt = 690f)
    draws += PdfGlyphDraw("ی", xPt = x2 - 12f, yPt = 690f)
    draws += PdfGlyphDraw("ن", xPt = x2 - 19.0f, yPt = 690f)
    x2 -= 29f
    // تحرک
    draws += PdfGlyphDraw("ت", xPt = x2 - 6f, yPt = 690f)
    draws += PdfGlyphDraw("ح", xPt = x2 - 12f, yPt = 690f)
    draws += PdfGlyphDraw("ر", xPt = x2 - 18f, yPt = 690f)
    draws += PdfGlyphDraw("ک", xPt = x2 - 25.0f, yPt = 690f)
    x2 -= 35f
    // نمایه
    draws += PdfGlyphDraw("ن", xPt = x2 - 6f, yPt = 690f)
    draws += PdfGlyphDraw("م", xPt = x2 - 12f, yPt = 690f)
    draws += PdfGlyphDraw("ا", xPt = x2 - 18f, yPt = 690f)
    draws += PdfGlyphDraw("ی", xPt = x2 - 24f, yPt = 690f)
    draws += PdfGlyphDraw("ه", xPt = x2 - 30.8f, yPt = 690f)

    val pdfFile = buildPositionedGlyphPdf(
      target = tempFolder.newFile("atomic_ligature_and_gaps.pdf"),
      title = "آزمایش لیگاتور لا",
      pagesDraws = listOf(draws),
    )

    val extractor = PdfTextExtractor(context)
    val result = extractor.extractBook(pdfFile, fallbackTitle = "آزمایش لا")
    assertTrue(result is ExtractionResult.Success)
    val success = result as ExtractionResult.Success

    val pageText = success.pages[0].cleanedText
    assertTrue("Expected میلانی in '$pageText'", pageText.contains("میلانی"))
    assertTrue("Expected آلاشت in '$pageText'", pageText.contains("آلاشت"))
    assertTrue("Expected اصلاح in '$pageText'", pageText.contains("اصلاح"))
    assertTrue("Expected سلام in '$pageText'", pageText.contains("سلام"))
    assertTrue("Expected الله in '$pageText'", pageText.contains("الله"))
    assertTrue("Expected الف in '$pageText'", pageText.contains("الف"))
    assertTrue("Expected الان in '$pageText'", pageText.contains("الان"))
    assertTrue("Expected الم in '$pageText'", pageText.contains("الم"))

    // Ensure ligatures were extracted faithfully at primary RtlLinePdfStripper level without needing repair
    assertEquals(0, success.ligatureRepairsCount)

    // Ensure detached final letters were joined while preserving standalone "و"
    assertTrue("Expected پسر in '$pageText'", pageText.contains("پسر"))
    assertTrue("Expected دین in '$pageText'", pageText.contains("دین"))
    assertTrue("Expected تحرک in '$pageText'", pageText.contains("تحرک"))
    assertTrue("Expected نمایه in '$pageText'", pageText.contains("نمایه"))
    assertFalse("Should not contain broken 'پس ر'", pageText.contains("پس ر"))
    assertFalse("Should not contain broken 'دی ن'", pageText.contains("دی ن"))
  }

  @Test
  fun `extracts multi-page structured PDF preserving title TOC body repeating header footer and footnote blocks`() = runBlocking {
    val pagesTextLines = listOf(
      // Page 1: Dedication centered + header/footer/page num
      listOf(
        PositionedLogicalLine("تاریخ معاصر", yPt = 785f, fontSize = 10f, rightX = 360f),
        PositionedLogicalLine("تقدیم به پدرم", yPt = 520f, fontSize = 12f, rightX = 345f),
        PositionedLogicalLine("و یاد روزگاران", yPt = 496f, fontSize = 12f, rightX = 345f),
        PositionedLogicalLine("انتشارات خرد", yPt = 70f, fontSize = 9.5f, rightX = 360f),
        PositionedLogicalLine("۱", yPt = 42f, fontSize = 10f, rightX = 302f),
      ),
      // Page 2: TOC
      listOf(
        PositionedLogicalLine("تاریخ معاصر", yPt = 785f, fontSize = 10f, rightX = 360f),
        PositionedLogicalLine("فهرست مطالب", yPt = 700f, fontSize = 18f, rightX = 365f, isBold = true),
        PositionedLogicalLine("مقدمه ........ ۵", yPt = 640f, fontSize = 12f, rightX = 480f),
        PositionedLogicalLine("فصل اول: میلانی ........ ۱۸", yPt = 612f, fontSize = 12f, rightX = 480f),
        PositionedLogicalLine("فصل دوم: آلاشت ........ ۴۴", yPt = 584f, fontSize = 12f, rightX = 480f),
        PositionedLogicalLine("انتشارات خرد", yPt = 70f, fontSize = 9.5f, rightX = 360f),
        PositionedLogicalLine("۲", yPt = 42f, fontSize = 10f, rightX = 302f),
      ),
      // Page 3: Title + body + footnote + header/footer/page num
      listOf(
        PositionedLogicalLine("تاریخ معاصر", yPt = 785f, fontSize = 10f, rightX = 360f),
        PositionedLogicalLine("فصل اول: یادداشت‌های آلاشت", yPt = 700f, fontSize = 18f, rightX = 420f, isBold = true),
        PositionedLogicalLine("نویسنده در این دفتر خاطره‌ها را می‌نویسد و رخدادها را شرح می‌دهد.", yPt = 650f, fontSize = 12f, rightX = 520f),
        PositionedLogicalLine("۱. پاورقی تاریخی مربوط به اسناد دوره پهلوی.", yPt = 110f, fontSize = 9f, rightX = 510f),
        PositionedLogicalLine("انتشارات خرد", yPt = 70f, fontSize = 9.5f, rightX = 360f),
        PositionedLogicalLine("۳", yPt = 42f, fontSize = 10f, rightX = 302f),
      ),
    )

    val pdfFile = buildStructuredMultiPagePdf(
      target = tempFolder.newFile("structured_book.pdf"),
      title = "کتاب ساختاردار",
      pages = pagesTextLines,
    )

    val extractor = PdfTextExtractor(context)
    val result = extractor.extractBook(pdfFile, fallbackTitle = "کتاب ساختاردار")
    assertTrue(result is ExtractionResult.Success)
    val success = result as ExtractionResult.Success
    assertEquals(3, success.pages.size)

    val p2Blocks = success.pages[1].blocks
    assertTrue(p2Blocks.any { it.type == DocumentBlockType.TOC_ENTRY && it.referencePageNumber == 18 })
    assertTrue(p2Blocks.any { it.type == DocumentBlockType.TOC_ENTRY && it.referencePageNumber == 44 })
    assertTrue(p2Blocks.any { it.type == DocumentBlockType.HEADER })
    assertTrue(p2Blocks.any { it.type == DocumentBlockType.FOOTER })

    // Page 3 separates PAGE_TITLE from BODY_PARAGRAPH and keeps FOOTNOTE/HEADER/FOOTER labeled
    val p3Blocks = success.pages[2].blocks
    assertTrue(p3Blocks.any { it.type == DocumentBlockType.PAGE_TITLE })
    assertTrue(p3Blocks.any { it.type == DocumentBlockType.BODY_PARAGRAPH })
    assertTrue(p3Blocks.any { it.type == DocumentBlockType.FOOTNOTE })
    assertTrue(p3Blocks.any { it.type == DocumentBlockType.HEADER })
    assertTrue(p3Blocks.any { it.type == DocumentBlockType.FOOTER })
  }

  private data class PositionedLogicalLine(
    val logicalText: String,
    val yPt: Float,
    val fontSize: Float,
    val rightX: Float,
    val isBold: Boolean = false,
  )

  private fun buildStructuredMultiPagePdf(
    target: File,
    title: String,
    pages: List<List<PositionedLogicalLine>>,
  ): File {
    val pagesDraws = pages.map { lines ->
      val draws = mutableListOf<PdfGlyphDraw>()
      for (line in lines) {
        val bidi = java.text.Bidi(line.logicalText, java.text.Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT)
        val runCount = bidi.runCount
        val levels = ByteArray(runCount)
        val runs = Array(runCount) { "" }
        for (i in 0 until runCount) {
          levels[i] = bidi.getRunLevel(i).toByte()
          val slice = line.logicalText.substring(bidi.getRunStart(i), bidi.getRunLimit(i))
          runs[i] = if (levels[i].toInt() % 2 == 1) slice.reversed() else slice
        }
        java.text.Bidi.reorderVisually(levels, 0, runs, 0, runCount)
        val visual = runs.joinToString("")
        val charAdvance = line.fontSize * 0.45f
        val leftX = (line.rightX - visual.length * charAdvance).coerceAtLeast(40f)
        for ((idx, ch) in visual.withIndex()) {
          if (ch != ' ') {
            draws += PdfGlyphDraw(
              unicode = ch.toString(),
              xPt = leftX + idx * charAdvance,
              yPt = line.yPt,
              fontSize = line.fontSize,
              isBold = line.isBold,
            )
          }
        }
      }
      draws
    }
    return buildPositionedGlyphPdf(target, title, pagesDraws)
  }

  private fun buildPositionedGlyphPdf(
    target: File,
    title: String,
    pagesDraws: List<List<PdfGlyphDraw>>,
  ): File {
    val allUnits = (pagesDraws.flatten().map { it.unicode } + " ").distinct()
    val unitToCode = mutableMapOf<String, Int>()
    allUnits.forEachIndexed { idx, u -> unitToCode[u] = idx + 1 }

    val toUnicodeCMap = buildString {
      append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
      append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
      append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
      append("1 begincodespacerange\n<0001> <00FF>\nendcodespacerange\n")
      append("${unitToCode.size} beginbfchar\n")
      for ((unit, code) in unitToCode) {
        val codeHex = "%04X".format(code)
        val uniHex = unit.map { "%04X".format(it.code) }.joinToString("")
        append("<$codeHex> <$uniHex>\n")
      }
      append("endbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
    }

    val objBodies = mutableListOf<String>()
    // 1: Catalog, 2: Pages, 3: Info, 4: RegularFont, 5: BoldFont, 6: ToUnicode stream
    val pageObjStart = 7
    val kidsRefs = pagesDraws.indices.joinToString(" ") { idx -> "${pageObjStart + idx * 2} 0 R" }
    val titleHex = title.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) }

    objBodies += "<< /Type /Catalog /Pages 2 0 R >>"
    objBodies += "<< /Type /Pages /Kids [$kidsRefs] /Count ${pagesDraws.size} >>"
    objBodies += "<< /Title <FEFF$titleHex> >>"
    objBodies += "<< /Type /Font /Subtype /Type0 /BaseFont /TestPersianRegular /Encoding /Identity-H /DescendantFonts [<< /Type /Font /Subtype /CIDFontType2 /BaseFont /TestPersianRegular /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 500 >>] /ToUnicode 6 0 R >>"
    objBodies += "<< /Type /Font /Subtype /Type0 /BaseFont /TestPersian-Bold /Encoding /Identity-H /DescendantFonts [<< /Type /Font /Subtype /CIDFontType2 /BaseFont /TestPersian-Bold /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 500 /FontDescriptor << /Type /FontDescriptor /FontName /TestPersian-Bold /FontWeight 700 /Flags 262144 >> >>] /ToUnicode 6 0 R >>"
    objBodies += "<< /Length ${toUnicodeCMap.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${toUnicodeCMap}endstream"

    for (i in pagesDraws.indices) {
      val pageObjId = pageObjStart + i * 2
      val contentObjId = pageObjId + 1
      val streamContent = buildString {
        for (draw in pagesDraws[i]) {
          val codeHex = "%04X".format(unitToCode[draw.unicode] ?: 1)
          val fontRef = if (draw.isBold) "/F2" else "/F1"
          append("BT $fontRef ${draw.fontSize} Tf 1 0 0 1 ${draw.xPt} ${draw.yPt} Tm <$codeHex> Tj ET\n")
        }
      }
      objBodies += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents $contentObjId 0 R >>"
      objBodies += "<< /Length ${streamContent.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${streamContent}endstream"
    }

    val out = StringBuilder("%PDF-1.4\n")
    val offsets = mutableListOf<Int>()
    for ((idx, body) in objBodies.withIndex()) {
      offsets += out.toString().toByteArray(Charsets.US_ASCII).size
      out.append("${idx + 1} 0 obj\n$body\nendobj\n")
    }
    val xrefOffset = out.toString().toByteArray(Charsets.US_ASCII).size
    out.append("xref\n0 ${objBodies.size + 1}\n0000000000 65535 f \n")
    offsets.forEach { out.append(String.format("%010d 00000 n \n", it)) }
    out.append("trailer\n<< /Size ${objBodies.size + 1} /Root 1 0 R /Info 3 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")

    target.writeBytes(out.toString().toByteArray(Charsets.US_ASCII))
    return target
  }
}
