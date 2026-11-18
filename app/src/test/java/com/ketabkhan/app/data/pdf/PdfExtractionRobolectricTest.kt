package com.ketabkhan.app.data.pdf

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ketabkhan.app.domain.tts.TtsAvailability
import com.ketabkhan.app.domain.tts.TtsSynthesisEvent
import com.ketabkhan.app.domain.tts.UnavailableTtsEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfExtractionRobolectricTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `detects scanned empty PDF and reports OCR unsupported warning`() = runBlocking {
    val pdfFile = tempFolder.newFile("scanned.pdf")
    PDDocument().use { doc ->
      doc.addPage(PDPage())
      doc.addPage(PDPage())
      doc.save(pdfFile)
    }

    val extractor = PdfTextExtractor(context)
    val result = extractor.extractBook(pdfFile, fallbackTitle = "اسکن نمونه")

    assertTrue(result is ExtractionResult.Success)
    val success = result as ExtractionResult.Success
    assertEquals(2, success.pages.size)
    assertEquals(2, success.emptyPagesCount)
    assertTrue(success.pages.all { it.isEmpty })
    assertEquals(PdfQualityStatus.LIKELY_SCANNED_IMAGE, success.qualityReport.status)
    assertTrue(success.qualityReport.warningMessage?.contains("OCR") == true)
  }

  @Test
  fun `detects corrupted font mapping when extracted characters are replacement or outside Persian-Arabic`() {
    val pages = listOf(
      ExtractedPage(
        pageNumber = 1,
        cleanedText = " ÇáÝ ÈÇ ÊË ÌÍÎÏÐÑÒÓÔÕÖ",
        isEmpty = false,
        charCount = 30,
      )
    )
    val report = PdfTextExtractor.analyzeBookQuality(pages)
    assertEquals(PdfQualityStatus.CORRUPTED_FONT_MAPPING, report.status)
    assertNotNull(report.warningMessage)
  }

  @Test
  fun `handles password protected PDF requiring password then unlocking with correct password`() = runBlocking {
    val pdfFile = tempFolder.newFile("protected.pdf")
    PDDocument().use { doc ->
      doc.documentInformation.title = "Protected Book"
      val page = PDPage()
      doc.addPage(page)
      PDPageContentStream(doc, page).use { stream ->
        stream.beginText()
        stream.setFont(PDType1Font.HELVETICA, 12f)
        stream.newLineAtOffset(50f, 700f)
        stream.showText("Hello world test page 1")
        stream.endText()
      }
      val policy = StandardProtectionPolicy("owner123", "user123", AccessPermission())
      policy.encryptionKeyLength = 128
      doc.protect(policy)
      doc.save(pdfFile)
    }

    val extractor = PdfTextExtractor(context)

    val noPass = extractor.extractBook(pdfFile, fallbackTitle = "کتاب رمزدار", password = null)
    assertTrue(noPass is ExtractionResult.PasswordRequired)

    val wrongPass = extractor.extractBook(pdfFile, fallbackTitle = "کتاب رمزدار", password = "wrong")
    assertTrue(wrongPass is ExtractionResult.InvalidPassword)

    val rightPass = extractor.extractBook(pdfFile, fallbackTitle = "کتاب رمزدار", password = "user123")
    assertTrue(rightPass is ExtractionResult.Success)
    val success = rightPass as ExtractionResult.Success
    assertTrue(success.isPasswordProtected)
    assertEquals("Protected Book", success.resolvedTitle)
    assertEquals(1, success.pages.size)
    assertTrue(success.pages[0].cleanedText.contains("Hello world"))
  }

  @Test
  fun `persists Persian PDF extraction to JSON cache and deletes both PDF and cache`() = runBlocking {
    val pdfFile = buildPersianUnicodePdf(
      target = tempFolder.newFile("persian_sample.pdf"),
      title = "کتاب آزمایشی فارسی",
      pageTexts = listOf(
        "سلام دنیا! این صفحهٔ اول کتاب فارسی است. جملهٔ دوم با عدد ۳.۱۴ آزمایش می‌شود.",
        "در صفحهٔ دوم نیز متن پاکسازی‌شده نمایش داده می‌شود؟ بله!",
      ),
    )

    val repo = BookRepository(context)
    val outcome = repo.importAndExtractBook(Uri.fromFile(pdfFile)) {}
    assertTrue(outcome is ImportOutcome.Success)
    val meta = (outcome as ImportOutcome.Success).book

    assertEquals("کتاب آزمایشی فارسی", meta.title)
    assertEquals(2, meta.pageCount)
    assertEquals(0, meta.emptyPagesCount)
    assertEquals(PdfQualityStatus.OK, meta.qualityReport.status)

    val cached = repo.getBookContent(meta.id)
    assertNotNull(cached)
    assertEquals(CURRENT_EXTRACTION_SCHEMA_VERSION, cached!!.meta.cacheSchemaVersion)
    assertFalse(cached.meta.needsReextraction)
    assertFalse(cached.isCacheOutdated)
    assertEquals(2, cached.pages.size)
    assertTrue(
      "Expected 'سلام دنیا!' in extracted text, got: '${cached.pages[0].cleanedText}'",
      cached.pages[0].cleanedText.contains("سلام دنیا!")
    )
    assertTrue(
      "Expected non-empty rawText saved alongside cleanedText",
      cached.pages[0].rawText.isNotBlank()
    )
    assertTrue(File(meta.pdfFilePath).exists())
    assertTrue(File(meta.cacheFilePath).exists())

    repo.deleteBook(meta.id)
    assertFalse(File(meta.pdfFilePath).exists())
    assertFalse(File(meta.cacheFilePath).exists())
    assertNull(repo.getBookContent(meta.id))
  }

  @Test
  fun `candidate scorer favors intact Persian text over broken ZWNJ guillemet and digit defects`() {
    val intactCandidate = "در سال ۱۲۹۹ رضاخان به «تهران» می‌زد و نامش نوش‌آفرین بود."
    val brokenCandidate = "در سال۱۲۹۹ رضاخان به »تهران« م یزد و نامش نو شآفرین بود."
    val corruptedCharsCandidate = "\uFFFD\uE001\u00C3 در\uFFFD ۱۲۹۹"

    val intactScore = PersianExtractionScorer.scoreCandidate(intactCandidate)
    val brokenScore = PersianExtractionScorer.scoreCandidate(brokenCandidate)
    val corruptedScore = PersianExtractionScorer.scoreCandidate(corruptedCharsCandidate)

    assertTrue(intactScore.totalScore > brokenScore.totalScore)
    assertTrue(intactScore.totalScore > corruptedScore.totalScore)
    assertFalse(intactScore.hasCorruptionSignals)
    assertTrue(brokenScore.hasCorruptionSignals)
    assertTrue(PersianExtractionScorer.shouldEvaluateFallback(brokenScore, brokenCandidate))
  }

  @Test
  fun `marks legacy schema v1 cache as outdated and upgrades via reextractBook without re-picking URI`() = runBlocking {
    val pdfFile = buildPersianUnicodePdf(
      target = tempFolder.newFile("legacy_upgrade.pdf"),
      title = "کتاب نسخه قدیم",
      pageTexts = listOf(
        "در سال ۱۲۹۹رضاخان م یزد و نو شآفرین در صفحه ی اول بود.",
      ),
    )

    val repo = BookRepository(context)
    val outcome = repo.importAndExtractBook(Uri.fromFile(pdfFile)) {}
    assertTrue(outcome is ImportOutcome.Success)
    val meta = (outcome as ImportOutcome.Success).book

    // Simulate a Phase 1 (v1) cache & index entry on disk without rawText or cacheSchemaVersion
    val cacheFile = File(meta.cacheFilePath)
    cacheFile.writeText(
      """
      {
        "version": 1,
        "bookId": "${meta.id}",
        "pages": [
          {
            "pageNumber": 1,
            "cleanedText": "متن قدیمی م یزد و نو شآفرین",
            "isEmpty": false,
            "charCount": 27
          }
        ]
      }
      """.trimIndent(),
      Charsets.UTF_8,
    )
    val indexFile = File(context.filesDir, "books/library_index.json")
    val patchedIndex = indexFile.readText(Charsets.UTF_8)
      .replace("\"cacheSchemaVersion\":2", "\"cacheSchemaVersion\":1")
    indexFile.writeText(patchedIndex, Charsets.UTF_8)

    repo.loadLibrary()
    val libraryBook = repo.books.value.first { it.id == meta.id }
    assertTrue(libraryBook.needsReextraction)
    assertFalse(repo.isCacheVersionCurrent(libraryBook))

    val outdatedContent = repo.getBookContent(meta.id)
    assertNotNull(outdatedContent)
    assertTrue(outdatedContent!!.isCacheOutdated)
    assertEquals("", outdatedContent.pages[0].rawText)

    // Re-extract directly from the stored local PDF file
    val reextractOutcome = repo.reextractBook(meta.id)
    assertTrue(reextractOutcome is ImportOutcome.Success)
    val upgradedMeta = (reextractOutcome as ImportOutcome.Success).book
    assertEquals(CURRENT_EXTRACTION_SCHEMA_VERSION, upgradedMeta.cacheSchemaVersion)
    assertFalse(upgradedMeta.needsReextraction)

    val upgradedContent = repo.getBookContent(meta.id)!!
    assertFalse(upgradedContent.isCacheOutdated)
    assertTrue(upgradedContent.pages[0].rawText.isNotBlank())
    val cleaned = upgradedContent.pages[0].cleanedText
    assertTrue("Expected ZWNJ fix می‌زد in '$cleaned'", cleaned.contains("می\u200Cزد"))
    assertTrue("Expected ZWNJ fix نوش‌آفرین in '$cleaned'", cleaned.contains("نوش\u200Cآفرین"))
    assertTrue("Expected digit spacing ۱۲۹۹ رضاخان in '$cleaned'", cleaned.contains("۱۲۹۹ رضاخان"))
  }

  @Test
  fun `unavailable tts engine reports clear Persian message`() = runBlocking {
    val engine = UnavailableTtsEngine()
    val state = engine.checkAvailability()
    assertTrue(state is TtsAvailability.Unavailable)
    assertFalse(engine.isModelPresent())
    assertTrue(engine.listAvailableVoices().isEmpty())
    assertFalse(engine.selectVoice("any"))
    val event = engine.synthesizeSentence("سلام").first()
    assertTrue(event is TtsSynthesisEvent.Failed)
  }

  /**
   * Builds a valid PDF containing a ToUnicode CMap with Persian UTF-16BE characters
   * so PDFBox PDFTextStripper extracts real Persian Unicode text without requiring any bundled .ttf binary.
   */
  private fun buildPersianUnicodePdf(
    target: File,
    title: String,
    pageTexts: List<String>,
  ): File {
    val visualPageTexts = pageTexts.map { toVisualOrderForPdfTj(it) }
    val allChars = (visualPageTexts.joinToString("") + " ").toSet().toList()
    val charToCode = mutableMapOf<Char, Int>()
    allChars.forEachIndexed { idx, ch ->
      charToCode[ch] = idx + 1
    }

    val toUnicodeCMap = buildString {
      append("/CIDInit /ProcSet findresource begin\n")
      append("12 dict begin\n")
      append("begincmap\n")
      append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
      append("/CMapName /Adobe-Identity-UCS def\n")
      append("/CMapType 2 def\n")
      append("1 begincodespacerange\n<0001> <00FF>\nendcodespacerange\n")
      append("${charToCode.size} beginbfchar\n")
      for ((ch, code) in charToCode) {
        val codeHex = String.format("%04X", code)
        val uniHex = String.format("%04X", ch.code)
        append("<$codeHex> <$uniHex>\n")
      }
      append("endbfchar\n")
      append("endcmap\n")
      append("CMapName currentdict /CMap defineresource pop\n")
      append("end\nend\n")
    }

    val objBodies = mutableListOf<String>()
    // 1: Catalog, 2: Pages, 3: Info, 4: Font, 5: ToUnicode stream
    val pageObjStart = 6
    val kidsRefs = visualPageTexts.indices.joinToString(" ") { idx -> "${pageObjStart + idx * 2} 0 R" }

    objBodies += "<< /Type /Catalog /Pages 2 0 R >>"
    objBodies += "<< /Type /Pages /Kids [$kidsRefs] /Count ${visualPageTexts.size} >>"
    val titleHex = title.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) }
    objBodies += "<< /Title <FEFF$titleHex> >>"
    objBodies += "<< /Type /Font /Subtype /Type0 /BaseFont /TestPersianFont /Encoding /Identity-H /DescendantFonts [<< /Type /Font /Subtype /CIDFontType2 /BaseFont /TestPersianFont /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 500 >>] /ToUnicode 5 0 R >>"
    objBodies += "<< /Length ${toUnicodeCMap.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${toUnicodeCMap}endstream"

    for (i in visualPageTexts.indices) {
      val pageObjId = pageObjStart + i * 2
      val contentObjId = pageObjId + 1
      val hexText = visualPageTexts[i].map { ch ->
        val code = charToCode[ch] ?: 1
        String.format("%04X", code)
      }.joinToString("")
      val contentStream = "BT /F1 12 Tf 50 700 Td <$hexText> Tj ET\n"
      objBodies += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents $contentObjId 0 R >>"
      objBodies += "<< /Length ${contentStream.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${contentStream}endstream"
    }

    val out = StringBuilder()
    out.append("%PDF-1.4\n")
    val offsets = mutableListOf<Int>()
    for ((idx, body) in objBodies.withIndex()) {
      offsets += out.toString().toByteArray(Charsets.US_ASCII).size
      out.append("${idx + 1} 0 obj\n$body\nendobj\n")
    }
    val xrefOffset = out.toString().toByteArray(Charsets.US_ASCII).size
    out.append("xref\n0 ${objBodies.size + 1}\n")
    out.append("0000000000 65535 f \n")
    for (offset in offsets) {
      out.append(String.format("%010d 00000 n \n", offset))
    }
    out.append("trailer\n<< /Size ${objBodies.size + 1} /Root 1 0 R /Info 3 0 R >>\n")
    out.append("startxref\n$xrefOffset\n%%EOF\n")

    target.writeBytes(out.toString().toByteArray(Charsets.US_ASCII))
    return target
  }

  private fun toVisualOrderForPdfTj(logical: String): String {
    val bidi = java.text.Bidi(logical, java.text.Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT)
    val runCount = bidi.runCount
    val levels = ByteArray(runCount)
    val runs = Array(runCount) { "" }
    for (i in 0 until runCount) {
      levels[i] = bidi.getRunLevel(i).toByte()
      val slice = logical.substring(bidi.getRunStart(i), bidi.getRunLimit(i))
      runs[i] = if (levels[i].toInt() % 2 == 1) slice.reversed() else slice
    }
    java.text.Bidi.reorderVisually(levels, 0, runs, 0, runCount)
    return runs.joinToString("")
  }
}
