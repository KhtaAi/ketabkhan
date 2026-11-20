package com.ketabkhan.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianCompoundDictionaryTest {

  @Test
  fun `restores exact glued required Persian compounds to ZWNJ form`() {
    val expectedMappings = listOf(
      "نقلقول" to "نقل\u200Cقول",
      "روبهرو" to "روبه\u200Cرو",
      "گفتوگو" to "گفت\u200Cوگو",
      "رفتوآمد" to "رفت\u200Cوآمد",
      "بینالمللی" to "بین\u200Cالمللی",
      "بهاستثنای" to "به\u200Cاستثنای",
      "بهویژه" to "به\u200Cویژه",
      "همچنین" to "هم\u200Cچنین",
      "هیچگاه" to "هیچ\u200Cگاه",
      "هیچوقت" to "هیچ\u200Cوقت",
      "کتابخانه" to "کتاب\u200Cخانه",
      "دانشگاه" to "دانش\u200Cگاه",
      "پژوهشگاه" to "پژوهش\u200Cگاه",
      "روششناسی" to "روش\u200Cشناسی",
    )

    for ((glued, expected) in expectedMappings) {
      assertEquals("Failed for compound '$glued'", expected, PersianCompoundDictionary.restoreExactToken(glued))
    }
  }

  @Test
  fun `applies mapping only when full token matches glued compound`() {
    assertEquals(
      "در دانش\u200Cگاه شهر نقل\u200Cقول تازه‌ای روبه\u200Cرو شد و هم\u200Cچنین روش\u200Cشناسی تغییر یافت.",
      PersianCompoundDictionary.restoreInText(
        "در دانشگاه شهر نقلقول تازه‌ای روبهرو شد و همچنین روششناسی تغییر یافت."
      ),
    )

    // Partial tokens or already-separated forms remain untouched
    assertEquals("کتابخانه‌ای", PersianCompoundDictionary.restoreExactToken("کتابخانه‌ای"))
    assertEquals("سخنانی بدون هیچگونه نقل", PersianCompoundDictionary.restoreInText("سخنانی بدون هیچگونه نقل"))
  }
}
