package com.ketabkhan.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianNumberWordSplitterTest {

  @Test
  fun `splits glued multi-part Persian number words and trailing non-number adjective`() {
    assertEquals("بیست\u200Cونه روزه", PersianNumberWordSplitter.splitToken("بیستونهروزه"))
    assertEquals("سی\u200Cوچهار ساله", PersianNumberWordSplitter.splitToken("سیوچهارساله"))
    assertEquals("صد\u200Cوبیست", PersianNumberWordSplitter.splitToken("صدوبیست"))
    assertEquals("دویست\u200Cوپنجاه صفحه", PersianNumberWordSplitter.splitToken("دویستوپنجاهصفحه"))
    assertEquals("دو هزار", PersianNumberWordSplitter.splitToken("دوهزار"))
    assertEquals("سه میلیارد", PersianNumberWordSplitter.splitToken("سهمیلیارد"))
    assertEquals("پنج میلیون", PersianNumberWordSplitter.splitToken("پنجمایون".replace("مایون", "میلیون")))
    assertEquals("بیست\u200Cویکمین", PersianNumberWordSplitter.splitToken("بیستویکمین"))
  }

  @Test
  fun `splits glued number-words inside sentences without breaking non-number lookalikes`() {
    val input = "اقامت بیستونهروزه در شهر بیستونهرروزه برای سیستمهای دولتی دوست داشتنی بود."
    val expected = "اقامت بیست\u200Cونه روزه در شهر بیستونهرروزه برای سیستمهای دولتی دوست داشتنی بود."
    assertEquals(expected, PersianNumberWordSplitter.splitInText(input))
  }

  @Test
  fun `leaves invalid or standalone words untouched`() {
    val unchanged = listOf("بیست", "هزار", "دولت", "دوست", "چهارشنبه", "سیستم", "پنجره", "یکدیگر", "یکسان")
    for (token in unchanged) {
      assertEquals(token, PersianNumberWordSplitter.splitToken(token))
    }
  }
}
