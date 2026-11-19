package com.ketabkhan.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.ketabkhan.app.data.pdf.BookRepository
import com.ketabkhan.app.data.pdf.CURRENT_EXTRACTION_SCHEMA_VERSION
import com.ketabkhan.app.data.pdf.ImportOutcome
import com.ketabkhan.app.ui.theme.KetabkhanTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BookScreenDiagnosticRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `switching between cleaned and raw text updates displayed content and reextract clears outdated banner`() {
    val rawPersianLine = "در سال ۱۲۹۹رضاخان م یزد و نو شآفرین در خانه بود."
    val pdfFile = buildSinglePagePersianPdf(
      target = tempFolder.newFile("diagnostic_persian.pdf"),
      title = "کتاب آزمایشی",
      pageText = rawPersianLine,
    )

    val repository = BookRepository(context)
    val bookId = runBlocking {
      val outcome = repository.importAndExtractBook(Uri.fromFile(pdfFile)) {}
      val meta = (outcome as ImportOutcome.Success).book

      // Downgrade cache to legacy v1 without rawText to simulate pre-Phase-4 install
      File(meta.cacheFilePath).writeText(
        """
        {
          "version": 1,
          "bookId": "${meta.id}",
          "pages": [
            {
              "pageNumber": 1,
              "cleanedText": "متن قدیمی م یزد",
              "isEmpty": false,
              "charCount": 16
            }
          ]
        }
        """.trimIndent(),
        Charsets.UTF_8,
      )
      val indexFile = File(context.filesDir, "books/library_index.json")
      indexFile.writeText(
        indexFile.readText(Charsets.UTF_8).replace("\"cacheSchemaVersion\":2", "\"cacheSchemaVersion\":1"),
        Charsets.UTF_8,
      )
      repository.loadLibrary()
      meta.id
    }

    val viewModel = BookViewModel(bookId = bookId, repository = repository)
    composeTestRule.setContent {
      KetabkhanTheme {
        BookScreen(viewModel = viewModel, onNavigateBack = {})
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      viewModel.uiState.value is BookUiState.Loaded
    }
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("outdated_cache_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("reextract_banner_button").assertIsDisplayed().performClick()

    val deadline = System.currentTimeMillis() + 15_000L
    while (System.currentTimeMillis() < deadline) {
      org.robolectric.shadows.ShadowLooper.idleMainLooper()
      composeTestRule.waitForIdle()
      val state = viewModel.uiState.value as? BookUiState.Loaded
      if (state != null && !state.isReextracting && !state.content.isCacheOutdated) {
        break
      }
      Thread.sleep(25)
    }
    org.robolectric.shadows.ShadowLooper.idleMainLooper()
    composeTestRule.waitForIdle()

    val afterReextract = viewModel.uiState.value as BookUiState.Loaded
    assertNull(afterReextract.reextractionError)
    org.junit.Assert.assertFalse("Expected isReextracting=false", afterReextract.isReextracting)
    org.junit.Assert.assertFalse("Expected isCacheOutdated=false", afterReextract.content.isCacheOutdated)

    composeTestRule.onNodeWithTag("outdated_cache_banner").assertDoesNotExist()
    composeTestRule.onNodeWithTag("full_pages_list").performScrollToNode(hasTestTag("page_card_1"))
    composeTestRule.onNodeWithTag("page_text_1")
      .assertTextContains("۱۲۹۹ رضاخان می‌زد و نوش\u200Cآفرین", substring = true)

    composeTestRule.onNodeWithTag("raw_text_tab").performClick()
    composeTestRule.waitForIdle()

    val currentState = viewModel.uiState.value as BookUiState.Loaded
    assertTrue(currentState.textSource == BookTextSource.RAW)
    composeTestRule.onNodeWithTag("full_pages_list").performScrollToIndex(0)
    composeTestRule.onNodeWithTag("page_text_1")
      .assertTextContains("۱۲۹۹رضاخان م یزد", substring = true)
  }

  private fun buildSinglePagePersianPdf(
    target: File,
    title: String,
    pageText: String,
  ): File {
    val bidi = java.text.Bidi(pageText, java.text.Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT)
    val runCount = bidi.runCount
    val levels = ByteArray(runCount)
    val runs = Array(runCount) { "" }
    for (i in 0 until runCount) {
      levels[i] = bidi.getRunLevel(i).toByte()
      val slice = pageText.substring(bidi.getRunStart(i), bidi.getRunLimit(i))
      runs[i] = if (levels[i].toInt() % 2 == 1) slice.reversed() else slice
    }
    java.text.Bidi.reorderVisually(levels, 0, runs, 0, runCount)
    val visualText = runs.joinToString("")

    val allChars = (visualText + " ").toSet().toList()
    val charToCode = mutableMapOf<Char, Int>()
    allChars.forEachIndexed { idx, ch -> charToCode[ch] = idx + 1 }

    val toUnicodeCMap = buildString {
      append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
      append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
      append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
      append("1 begincodespacerange\n<0001> <00FF>\nendcodespacerange\n")
      append("${charToCode.size} beginbfchar\n")
      for ((ch, code) in charToCode) {
        append("<${"%04X".format(code)}> <${"%04X".format(ch.code)}>\n")
      }
      append("endbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
    }

    val titleHex = title.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) }
    val hexText = visualText.map { ch -> "%04X".format(charToCode[ch] ?: 1) }.joinToString("")
    val contentStream = "BT /F1 12 Tf 50 700 Td <$hexText> Tj ET\n"

    val objBodies = listOf(
      "<< /Type /Catalog /Pages 2 0 R >>",
      "<< /Type /Pages /Kids [6 0 R] /Count 1 >>",
      "<< /Title <FEFF$titleHex> >>",
      "<< /Type /Font /Subtype /Type0 /BaseFont /TestPersianFont /Encoding /Identity-H /DescendantFonts [<< /Type /Font /Subtype /CIDFontType2 /BaseFont /TestPersianFont /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 500 >>] /ToUnicode 5 0 R >>",
      "<< /Length ${toUnicodeCMap.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${toUnicodeCMap}endstream",
      "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 7 0 R >>",
      "<< /Length ${contentStream.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${contentStream}endstream",
    )

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
