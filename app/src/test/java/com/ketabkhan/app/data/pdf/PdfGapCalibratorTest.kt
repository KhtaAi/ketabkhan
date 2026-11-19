package com.ketabkhan.app.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfGapCalibratorTest {

  @Test
  fun `computes reference space width from dominant font space character advance`() {
    val spaceAdvances = listOf(3.4f, 3.5f, 3.4f, 3.5f, 0f, 3.4f)
    val clusterGaps = listOf(3.5f, 3.6f, 3.4f)
    val ref = PdfGapCalibrator.computeReferenceSpaceWidth(
      fontSpaceWidths = spaceAdvances,
      interUnitGaps = clusterGaps,
      fontSizes = listOf(12f, 12f, 12f),
    )
    assertEquals(SpaceReferenceSource.FONT_SPACE_CHAR, ref.source)
    assertTrue("Expected ~3.45pt but got ${ref.spaceWidthPt}", ref.spaceWidthPt in 3.3f..3.6f)
  }

  @Test
  fun `falls back to dominant gap cluster when font space advance is invalid or missing`() {
    val spaceAdvances = listOf(0f, 0f, 0f)
    val clusterGaps = listOf(4.2f, 4.3f, 4.1f, 4.2f, 4.3f)
    val ref = PdfGapCalibrator.computeReferenceSpaceWidth(
      fontSpaceWidths = spaceAdvances,
      interUnitGaps = clusterGaps,
      fontSizes = listOf(12f, 12f, 12f),
    )
    assertEquals(SpaceReferenceSource.DOMINANT_GAP_CLUSTER, ref.source)
    assertTrue("Expected cluster ~4.2pt but got ${ref.spaceWidthPt}", ref.spaceWidthPt in 4.0f..4.4f)
  }

  @Test
  fun `falls back to font size ratio when neither font advance nor gap cluster is clear`() {
    val ref = PdfGapCalibrator.computeReferenceSpaceWidth(
      fontSpaceWidths = emptyList(),
      interUnitGaps = emptyList(),
      fontSizes = listOf(14f),
    )
    assertEquals(SpaceReferenceSource.FONT_SIZE_FRACTION, ref.source)
    assertEquals(14f * 0.28f, ref.spaceWidthPt, 0.01f)
  }

  @Test
  fun `calculates RTL horizontal gap from predecessor minX to successor maxX`() {
    // In RTL, predecessor is to the right (larger X coords), successor is to the left (smaller X coords)
    val rightPredecessorMinX = 250f
    val leftSuccessorMaxX = 244.5f
    val gap = PdfGapCalibrator.calculateRtlHorizontalGap(
      rightPredecessorMinX = rightPredecessorMinX,
      leftSuccessorMaxX = leftSuccessorMaxX,
    )
    assertEquals(5.5f, gap, 0.001f)
  }

  @Test
  fun `classifies tight kerning as JOIN and full space as SPACE`() {
    val tightDecision = PdfGapCalibrator.decideSeparator(
      prevText = "سلام",
      currText = "ت",
      visualGapPt = 0.5f,
      referenceSpaceWidthPt = 3.5f,
      avgFontSizePt = 12f,
      prevIsLtr = false,
      currIsLtr = false,
    )
    assertEquals(SeparatorDecision.JOIN, tightDecision.decision)

    val spaceDecision = PdfGapCalibrator.decideSeparator(
      prevText = "کتاب",
      currText = "خوان",
      visualGapPt = 3.6f,
      referenceSpaceWidthPt = 3.5f,
      avgFontSizePt = 12f,
      prevIsLtr = false,
      currIsLtr = false,
    )
    assertEquals(SeparatorDecision.SPACE, spaceDecision.decision)
  }

  @Test
  fun `applies conservative asymmetry by defaulting ambiguous gaps to full space never ZWNJ`() {
    // Gap in intermediate ambiguous zone (e.g. 1.5pt between unrelated words)
    val ambiguousDecision = PdfGapCalibrator.decideSeparator(
      prevText = "روزگار",
      currText = "قدیم",
      visualGapPt = 1.6f,
      referenceSpaceWidthPt = 3.5f,
      avgFontSizePt = 12f,
      prevIsLtr = false,
      currIsLtr = false,
    )
    assertEquals(SeparatorDecision.SPACE, ambiguousDecision.decision)
    assertEquals("asymmetric_default_space", ambiguousDecision.reason)
  }

  @Test
  fun `allows ZWNJ in intermediate gap only for unmistakable morphological affixes`() {
    val prefixDecision = PdfGapCalibrator.decideSeparator(
      prevText = "می",
      currText = "نویسد",
      visualGapPt = 1.6f,
      referenceSpaceWidthPt = 3.5f,
      avgFontSizePt = 12f,
      prevIsLtr = false,
      currIsLtr = false,
    )
    assertEquals(SeparatorDecision.ZWNJ, prefixDecision.decision)

    val suffixDecision = PdfGapCalibrator.decideSeparator(
      prevText = "کتاب",
      currText = "ها",
      visualGapPt = 1.6f,
      referenceSpaceWidthPt = 3.5f,
      avgFontSizePt = 12f,
      prevIsLtr = false,
      currIsLtr = false,
    )
    assertEquals(SeparatorDecision.ZWNJ, suffixDecision.decision)
  }

  @Test
  fun `handles quotes and digit adjacency correctly`() {
    val afterOpenQuote = PdfGapCalibrator.decideSeparator(
      prevText = "«",
      currText = "سلام",
      visualGapPt = 2.0f,
      referenceSpaceWidthPt = 3.5f,
      avgFontSizePt = 12f,
      prevIsLtr = false,
      currIsLtr = false,
    )
    assertEquals(SeparatorDecision.JOIN, afterOpenQuote.decision)

    val bidiBoundary = PdfGapCalibrator.decideSeparator(
      prevText = "۱۲۹۹",
      currText = "رضاخان",
      visualGapPt = 0.8f,
      referenceSpaceWidthPt = 3.5f,
      avgFontSizePt = 12f,
      prevIsLtr = true,
      currIsLtr = false,
    )
    assertEquals(SeparatorDecision.SPACE, bidiBoundary.decision)
  }

  @Test
  fun `joins unmistakable single trailing letters with narrow gap`() {
    val trailingLetter = PdfGapCalibrator.decideSeparator(
      prevText = "پس",
      currText = "ر",
      visualGapPt = 1.2f,
      referenceSpaceWidthPt = 3.5f,
      avgFontSizePt = 12f,
      prevIsLtr = false,
      currIsLtr = false,
      currIsSingleTrailingLetter = true,
    )
    assertEquals(SeparatorDecision.JOIN, trailingLetter.decision)
  }
}
