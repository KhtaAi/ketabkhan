package com.ketabkhan.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersianTextCleanerTest {

  @Test
  fun `normalizes arabic yeh and kaf presentation forms and diacritics while preserving alef madda`() {
    val raw = "آسَمانِ كِتابِ يارِ مَن، \uFEB3\uFEFC\uFEE1! می   \u200C خواهم."
    val cleaned = PersianTextCleaner.cleanInline(raw)
    assertTrue(cleaned.startsWith("آسمان کتاب یار من، سلام!"))
    assertTrue(cleaned.contains("می\u200Cخواهم."))
    assertFalse(cleaned.contains('\u064A'))
    assertFalse(cleaned.contains('\u0643'))
  }

  @Test
  fun `strips repeating headers footers and page numbers while joining broken lines`() {
    val pages = listOf(
      "فصل اول - داستان\nاین یک جملهٔ آزمایشی است که در خط\nاول شکسته شده است.\n\nپاراگراف دوم همین جا شروع می‌شود؟\n- ۱ -\nانتشارات نمونه",
      "فصل اول - داستان\nمتن صفحهٔ دوم ادامه دارد و به\nخط بعد می‌رود؛ اما معنای آن کامل است.\n- ۲ -\nانتشارات نمونه",
      "فصل اول - داستان\nخط اول صفحهٔ سوم بدون پایان\nبه خط دوم می‌چسبد.\n- ۳ -\nانتشارات نمونه",
      "فصل اول - داستان\nصفحهٔ چهارم نیز همین الگو را\nادامه می‌دهد!\n- ۴ -\nانتشارات نمونه",
    )

    val cleaned = PersianTextCleaner.cleanBookPages(pages)
    assertEquals(4, cleaned.size)

    val page1 = cleaned[0].cleanedText
    assertFalse(page1.contains("فصل اول"))
    assertFalse(page1.contains("انتشارات نمونه"))
    assertFalse(page1.contains("- ۱ -"))
    assertTrue(page1.contains("در خط اول شکسته شده است."))
    assertTrue(page1.contains("\n\nپاراگراف دوم همین جا شروع می‌شود؟"))
  }

  @Test
  fun `marks blank pages as empty`() {
    val pages = PersianTextCleaner.cleanBookPages(listOf("   \n- ۱۲ -\n  "))
    assertEquals(1, pages.size)
    assertTrue(pages[0].isEmpty)
  }

  @Test
  fun `inserts space between digits and Persian letters while keeping decimals intact`() {
    val cleaned = PersianTextCleaner.cleanInline("در سال ۱۲۹۹رضاخان با عدد ۳.۱۴ وارد صفحه۳کتاب شد.")
    assertEquals("در سال ۱۲۹۹ رضاخان با عدد ۳.۱۴ وارد صفحه ۳ کتاب شد.", cleaned)
  }

  @Test
  fun `fixes guillemet orientation and surrounding punctuation spacing`() {
    val cleaned = PersianTextCleaner.cleanInline("او» نوش آفرین«را دید ، و گفت : « سلام ! »")
    assertEquals("او «نوش‌آفرین» را دید، و گفت: «سلام!»", cleaned)
  }

  @Test
  fun `reproduces real-device defects together and cleans deterministically`() {
    val broken = "در سال ۱۲۹۹رضاخان »تهران« را م یزد و نو شآفرین در صفحه ی اول بود."
    val cleaned = PersianTextCleaner.cleanInline(broken)
    assertEquals("در سال ۱۲۹۹ رضاخان «تهران» را می‌زد و نوش‌آفرین در صفحهٔ اول بود.", cleaned)
  }

  @Test
  fun `removes deterministic consecutive duplicate lines and 3-word overlaps`() {
    val rawPage = listOf(
      "این سطر تکراری است",
      "این سطر تکراری است",
      "در آن روز بارانی در آن روز بارانی هوا خنک بود.",
    ).joinToString("\n")
    val cleaned = PersianTextCleaner.cleanSinglePage(rawPage, 1).cleanedText
    assertEquals(
      "این سطر تکراری است\n\nدر آن روز بارانی هوا خنک بود.",
      cleaned,
    )
  }
}
