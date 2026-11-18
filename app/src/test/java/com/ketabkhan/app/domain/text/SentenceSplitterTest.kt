package com.ketabkhan.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

  @Test
  fun `splits sentences on Persian terminal punctuation while protecting decimals and abbreviations`() {
    val text = "نرخ تورم حدود ۳.۱۴ درصد بود. به کتاب ر.ک. ص. ۴۵ مراجعه کنید! آیا ادامه می‌دهید؟"
    val sentences = SentenceSplitter.split(text)
    assertEquals(3, sentences.size)
    assertEquals("نرخ تورم حدود ۳.۱۴ درصد بود.", sentences[0])
    assertEquals("به کتاب ر.ک. ص. ۴۵ مراجعه کنید!", sentences[1])
    assertEquals("آیا ادامه می‌دهید؟", sentences[2])
  }

  @Test
  fun `splits overly long sentences at natural comma or conjunction boundaries`() {
    val longSentence = buildString {
      append("این یک جملهٔ بسیار طولانی دربارهٔ تاریخ ادبیات فارسی است، ")
      append("که بخش‌های متعددی از نثر کلاسیک و معاصر را بررسی می‌کند، ")
      append("و در ادامه به تحلیل سبک نویسندگان بزرگ سدهٔ اخیر می‌پردازد، ")
      append("تا خواننده بتواند تصویر روشنی از تحولات زبانی داشته باشد.")
    }
    val parts = SentenceSplitter.split(longSentence, maxSentenceLength = 100)
    assertTrue(parts.size >= 2)
    assertTrue(parts.all { it.length <= 120 })
  }
}
