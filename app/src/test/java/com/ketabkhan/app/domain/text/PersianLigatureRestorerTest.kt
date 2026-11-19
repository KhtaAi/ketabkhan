package com.ketabkhan.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianLigatureRestorerTest {

  @Test
  fun `restores unambiguous inverted لا words such as میالنی آالشت اصالح without corrupting ال words`() {
    val input = "میالنی در آالشت برای اصالح اوضاع از الله و الف و الان و الم و تالیف سخن گفت."
    val result = PersianLigatureRestorer.restore(input, hasGlyphInversionSignal = false)

    assertEquals(
      "میلانی در آلاشت برای اصلاح اوضاع از الله و الف و الان و الم و تالیف سخن گفت.",
      result.text,
    )
    assertEquals(3, result.ligatureFixesCount)
  }

  @Test
  fun `keeps سالم untouched without inversion signal but repairs سالم to سلام only with explicit glyph inversion signal`() {
    val cleanContext = "غذای سالم و تالش در شمال ایران"
    val cleanRes = PersianLigatureRestorer.restore(cleanContext, hasGlyphInversionSignal = false)
    assertEquals(cleanContext, cleanRes.text)
    assertEquals(0, cleanRes.ligatureFixesCount)

    val signalRes = PersianLigatureRestorer.restore("سالم دوست من", hasGlyphInversionSignal = true)
    assertEquals("سلام دوست من", signalRes.text)
    assertEquals(1, signalRes.ligatureFixesCount)
  }

  @Test
  fun `uses horizontal gap check to detect detached final letters while preserving standalone vav`() {
    assertTrue(
      PersianLigatureRestorer.repairDetachedTrailingLetterWithGap(
        leftWord = "پس",
        rightSingleLetter = "ر",
        horizontalGapPt = 0.8f,
        referenceSpaceWidthPt = 3.2f,
      )
    )
    assertTrue(
      PersianLigatureRestorer.repairDetachedTrailingLetterWithGap(
        leftWord = "دی",
        rightSingleLetter = "ن",
        horizontalGapPt = 1.1f,
        referenceSpaceWidthPt = 3.2f,
      )
    )
    assertFalse(
      PersianLigatureRestorer.repairDetachedTrailingLetterWithGap(
        leftWord = "مرد",
        rightSingleLetter = "و",
        horizontalGapPt = 0.8f,
        referenceSpaceWidthPt = 3.2f,
      )
    )
    assertFalse(
      PersianLigatureRestorer.repairDetachedTrailingLetterWithGap(
        leftWord = "کتاب",
        rightSingleLetter = "ر",
        horizontalGapPt = 3.4f,
        referenceSpaceWidthPt = 3.2f,
      )
    )
  }

  @Test
  fun `fallback text repair joins split پسر and دین while keeping standalone و`() {
    val res = PersianLigatureRestorer.repairUnmistakableDetachedTrailingChar("پس ر و دی ن و مادر")
    assertEquals("پسر و دین و مادر", res.text)
    assertEquals(2, res.detachedFinalLetterFixesCount)
  }
}
