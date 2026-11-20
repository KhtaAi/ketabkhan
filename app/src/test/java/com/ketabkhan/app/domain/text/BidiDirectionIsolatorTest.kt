package com.ketabkhan.app.domain.text

import com.ketabkhan.app.data.pdf.BlockLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BidiDirectionIsolatorTest {

  @Test
  fun `classifies PERSIAN LATIN and MIXED text blocks accurately`() {
    val persian = "در آن روزگار، ایرانیان هنوز شناسنامه نداشتند."
    val englishCopyright = "All rights reserved. No part of this book may be reproduced without permission in writing from the author."
    val mixed = "واژهٔ PDF برای پردازش کتاب‌های فارسی در Android بسیار مهم است."

    assertEquals(BlockLanguage.PERSIAN, BidiDirectionIsolator.classifyBlockLanguage(persian))
    assertEquals(BlockLanguage.LATIN, BidiDirectionIsolator.classifyBlockLanguage(englishCopyright))
    assertEquals(BlockLanguage.MIXED, BidiDirectionIsolator.classifyBlockLanguage(mixed))
  }

  @Test
  fun `leaves LATIN logical text untouched while isolating embedded LTR runs in PERSIAN or MIXED text`() {
    val copyright = "All rights reserved. No part of this publication may be reproduced without permission in writing from the author."
    val latinDisplay = BidiDirectionIsolator.formatForDisplay(copyright, BlockLanguage.LATIN)
    assertEquals(copyright, latinDisplay)
    assertFalse(latinDisplay.contains(BidiDirectionIsolator.LRI))

    val mixed = "کتاب Android Studio در سال ۲۰۲۶ منتشر شد."
    val mixedDisplay = BidiDirectionIsolator.formatForDisplay(mixed, BlockLanguage.MIXED)
    assertTrue(mixedDisplay.contains("${BidiDirectionIsolator.LRI}Android Studio${BidiDirectionIsolator.PDI}"))
    // Idempotent if called again
    assertEquals(mixedDisplay, BidiDirectionIsolator.formatForDisplay(mixedDisplay, BlockLanguage.MIXED))
  }
}
