package com.ketabkhan.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.ketabkhan.app.data.pdf.BookRepository
import com.ketabkhan.app.data.pdf.ImportOutcome
import com.ketabkhan.app.data.reading.ReadingSettingsRepository
import com.ketabkhan.app.domain.reading.TitlePauseDuration
import com.ketabkhan.app.ui.theme.KetabkhanTheme
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
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BookScreenStructuredRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @get:Rule
  val tempFolder = TemporaryFolder()

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `renders structured view by default, toggles to plain view, updates reading settings, and previews structured reading plan`() {
    val pdfFile = buildMultiLineStructuredPdf(
      target = tempFolder.newFile("structured_ui_book.pdf"),
      title = "کتاب آزمایشی ساختاردار",
      pages = listOf(
        listOf(
          LineSpec("فهرست مطالب", y = 710f, fontSize = 18f, isBold = true, rightX = 365f),
          LineSpec("مقدمه ........ ۵", y = 650f, fontSize = 12f, rightX = 480f),
          LineSpec("فصل اول ........ ۱۲", y = 622f, fontSize = 12f, rightX = 480f),
          LineSpec("پاورقی نمونه درباره فهرست.", y = 100f, fontSize = 9f, rightX = 500f),
        ),
        listOf(
          LineSpec("فصل اول: آلاشت و میلانی", y = 710f, fontSize = 18f, isBold = true, rightX = 420f),
          LineSpec("نویسنده در این صفحه یادداشت‌ها را می‌نویسد.", y = 650f, fontSize = 12f, rightX = 500f),
        ),
      ),
    )

    val bookRepo = BookRepository(context)
    val settingsRepo = ReadingSettingsRepository(context)
    val bookId = runBlocking {
      (bookRepo.importAndExtractBook(Uri.fromFile(pdfFile)) {} as ImportOutcome.Success).book.id
    }

    val viewModel = BookViewModel(
      bookId = bookId,
      repository = bookRepo,
      readingSettingsRepository = settingsRepo,
    )

    composeTestRule.setContent {
      KetabkhanTheme {
        BookScreen(viewModel = viewModel, onNavigateBack = {})
      }
    }

    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      viewModel.uiState.value is BookUiState.Loaded
    }
    ShadowLooper.idleMainLooper()
    composeTestRule.waitForIdle()

    val loaded = viewModel.uiState.value as BookUiState.Loaded
    assertEquals(BookDisplayFormat.STRUCTURED, loaded.displayFormat)

    // Structured tabs & reading settings button visible
    composeTestRule.onNodeWithTag("structured_view_tab").assertIsDisplayed()
    composeTestRule.onNodeWithTag("plain_view_tab").assertIsDisplayed()
    composeTestRule.onNodeWithTag("reading_settings_button").assertIsDisplayed()

    // Structured badge labels render on page 1
    composeTestRule.onNodeWithText("فهرست مطالب").assertIsDisplayed()
    composeTestRule.onNodeWithText("مقدمه").assertIsDisplayed()

    // Switch to plain view
    composeTestRule.onNodeWithTag("plain_view_tab").performClick()
    composeTestRule.waitForIdle()
    assertEquals(BookDisplayFormat.PLAIN, (viewModel.uiState.value as BookUiState.Loaded).displayFormat)
    composeTestRule.onNodeWithTag("page_text_1").assertTextContains("فهرست مطالب", substring = true)

    // Switch back to structured view
    composeTestRule.onNodeWithTag("structured_view_tab").performClick()
    composeTestRule.waitForIdle()
    assertEquals(BookDisplayFormat.STRUCTURED, (viewModel.uiState.value as BookUiState.Loaded).displayFormat)

    // Open Reading Settings modal, toggle footnotes ON, pick SHORT pause, save
    composeTestRule.onNodeWithTag("reading_settings_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("switch_read_footnotes").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("pause_short").assertIsDisplayed().performClick()
    composeTestRule.onNodeWithTag("save_reading_settings_button").performClick()

    ShadowLooper.idleMainLooper()
    composeTestRule.waitForIdle()

    val afterSettings = viewModel.uiState.value as BookUiState.Loaded
    assertTrue(afterSettings.readingSettings.readFootnotes)
    assertEquals(TitlePauseDuration.SHORT, afterSettings.readingSettings.titlePauseDuration)
    assertFalse(afterSettings.readingSettings.readHeadersAndFooters)
    assertTrue(settingsRepo.settings.value.readFootnotes)

    // Switch to Selected Range preview tab and verify ordered role-tagged reading items
    composeTestRule.onNodeWithTag("selected_range_tab").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("reading_plan_header").assertIsDisplayed()
    composeTestRule.onNodeWithTag("range_preview_list").performScrollToNode(hasTestTag("reading_item_1"))
    composeTestRule.onNodeWithTag("reading_item_1").assertIsDisplayed()
    composeTestRule.onNodeWithTag("range_preview_list").performScrollToNode(hasTestTag("reading_item_2"))
    composeTestRule.onNodeWithTag("reading_item_2").assertIsDisplayed().assertTextContains("فهرست کتاب", substring = true)
    composeTestRule.onNodeWithTag("range_preview_list").performScrollToNode(hasTestTag("reading_item_3"))
    composeTestRule.onNodeWithTag("reading_item_3").assertIsDisplayed().assertTextContains("مقدمه، صفحهٔ ۵", substring = true)
  }

  private data class LineSpec(
    val logicalText: String,
    val y: Float,
    val fontSize: Float,
    val isBold: Boolean = false,
    val rightX: Float,
  )

  private fun buildMultiLineStructuredPdf(
    target: File,
    title: String,
    pages: List<List<LineSpec>>,
  ): File {
    val allChars = mutableSetOf(' ')
    val visualPages = pages.map { lines ->
      lines.map { spec ->
        val visual = toVisual(spec.logicalText)
        visual.forEach { allChars.add(it) }
        spec to visual
      }
    }
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

    val objBodies = mutableListOf<String>()
    val pageObjStart = 7
    val kidsRefs = visualPages.indices.joinToString(" ") { idx -> "${pageObjStart + idx * 2} 0 R" }
    val titleHex = title.toByteArray(Charsets.UTF_16BE).joinToString("") { "%02X".format(it) }

    objBodies += "<< /Type /Catalog /Pages 2 0 R >>"
    objBodies += "<< /Type /Pages /Kids [$kidsRefs] /Count ${visualPages.size} >>"
    objBodies += "<< /Title <FEFF$titleHex> >>"
    objBodies += "<< /Type /Font /Subtype /Type0 /BaseFont /UiTestFont /Encoding /Identity-H /DescendantFonts [<< /Type /Font /Subtype /CIDFontType2 /BaseFont /UiTestFont /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 500 >>] /ToUnicode 6 0 R >>"
    objBodies += "<< /Type /Font /Subtype /Type0 /BaseFont /UiTestFont-Bold /Encoding /Identity-H /DescendantFonts [<< /Type /Font /Subtype /CIDFontType2 /BaseFont /UiTestFont-Bold /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /DW 500 /FontDescriptor << /Type /FontDescriptor /FontName /UiTestFont-Bold /FontWeight 700 /Flags 262144 >> >>] /ToUnicode 6 0 R >>"
    objBodies += "<< /Length ${toUnicodeCMap.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${toUnicodeCMap}endstream"

    for (page in visualPages) {
      val stream = buildString {
        for ((spec, visual) in page) {
          val adv = spec.fontSize * 0.45f
          val leftX = (spec.rightX - visual.length * adv).coerceAtLeast(40f)
          val hex = visual.map { "%04X".format(charToCode[it] ?: 1) }.joinToString("")
          val fontRef = if (spec.isBold) "/F2" else "/F1"
          append("BT $fontRef ${spec.fontSize} Tf 1 0 0 1 $leftX ${spec.y} Tm <$hex> Tj ET\n")
        }
      }
      val contentObjId = pageObjStart + visualPages.indexOf(page) * 2 + 1
      objBodies += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents $contentObjId 0 R >>"
      objBodies += "<< /Length ${stream.toByteArray(Charsets.US_ASCII).size} >>\nstream\n${stream}endstream"
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

  private fun toVisual(logical: String): String {
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
