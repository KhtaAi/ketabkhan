package com.ketabkhan.app.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianZwnjRestorerTest {

  @Test
  fun `restores broken spaced mi and nemi prefixes such as م یزد`() {
    assertEquals("می‌زد", PersianZwnjRestorer.restore("م یزد"))
    assertEquals("نمی‌زد", PersianZwnjRestorer.restore("نم یزد"))
    assertEquals("او می‌زد و می‌رفت", PersianZwnjRestorer.restore("او م یزد و می رفت"))
    assertEquals("نمی‌کنند", PersianZwnjRestorer.restore("نمی کنند"))
    assertEquals("برمی‌گردد", PersianZwnjRestorer.restore("بر می گردد"))
  }

  @Test
  fun `restores shifted consonant before alif madda such as نو شآفرین`() {
    assertEquals("نوش‌آفرین", PersianZwnjRestorer.restore("نو شآفرین"))
    assertEquals("نوش‌آفرین", PersianZwnjRestorer.restore("نوش آفرین"))
    assertEquals("خوش‌آوا", PersianZwnjRestorer.restore("خو شآوا"))
  }

  @Test
  fun `restores glued mi verbs and safe plural ha while protecting nouns and exceptions`() {
    assertEquals("می‌گویم که میز در میدان است.", PersianZwnjRestorer.restore("میگویم که میز در میدان است."))
    assertEquals(
      "میل و میهن و میوه و نمک و نماز نباید بشکنند.",
      PersianZwnjRestorer.restore("میل و میهن و میوه و نمک و نماز نباید بشکنند."),
    )
    assertEquals(
      "می ناب و نوش جان در نوروز مآل خوبی دارد.",
      PersianZwnjRestorer.restore("می ناب و نوش جان در نوروز مآل خوبی دارد."),
    )
    assertEquals(
      "نویسنده یادداشت‌ها را در دفتر می‌نویسد و می‌خواند.",
      PersianZwnjRestorer.restore("نویسنده یادداشتها را در دفتر مینویسد و میخواند."),
    )
    assertEquals(
      "میان میدان، میهمانان از میزبانی در میهن و میخ و مینا و میلاد سخن گفتند.",
      PersianZwnjRestorer.restore("میان میدان، میهمانان از میزبانی در میهن و میخ و مینا و میلاد سخن گفتند."),
    )
    assertEquals(
      "نمونه نمودار نمایش به تنهایی در انتها و تنها ماند.",
      PersianZwnjRestorer.restore("نمونه نمودار نمایش به تنهایی در انتها و تنها ماند."),
    )
  }

  @Test
  fun `restores suffixes ezafe and silent he enclitics`() {
    assertEquals("کتاب‌ها و کتاب‌های قدیمی و خانهٔ مادر", PersianZwnjRestorer.restore("کتاب ها و کتاب های قدیمی و خانه ی مادر"))
    assertEquals("بزرگ‌ترین و ساده‌تر و رفته‌ام", PersianZwnjRestorer.restore("بزرگ ترین و ساده تر و رفته ام"))
    assertEquals("صفحهٔ کتاب", PersianZwnjRestorer.restore("صفحه ی کتاب"))
  }

  @Test
  fun `handles glued plural suffixes uniformly across silent-he and non-silent-he nouns`() {
    // Symmetry verification: both نادانستههای and سیستمهای receive uniform plural ZWNJ
    assertEquals("نادانسته\u200Cهای", PersianZwnjRestorer.restore("نادانستههای"))
    assertEquals("سیستم\u200Cهای", PersianZwnjRestorer.restore("سیستمهای"))
    assertEquals("سیستم\u200Cها", PersianZwnjRestorer.restore("سیستمها"))
    assertEquals("سیستم\u200Cهایی", PersianZwnjRestorer.restore("سیستمهایی"))
    assertEquals("نوشته\u200Cاند", PersianZwnjRestorer.restore("نوشتهاند"))
    assertEquals("نمی\u200Cدانیم", PersianZwnjRestorer.restore("نمیدانیم"))
    assertEquals("الگوریتم\u200Cهای هوشمند و مدل\u200Cها", PersianZwnjRestorer.restore("الگوریتمهای هوشمند و مدلها"))
  }

  @Test
  fun `restores intra-word prefixes compounds indefinite-i and number words`() {
    assertEquals("بیست\u200Cونه روزه", PersianZwnjRestorer.restore("بیستونهروزه"))
    assertEquals("روبه\u200Cرو", PersianZwnjRestorer.restore("روبهرو"))
    assertEquals("بین\u200Cالمللی", PersianZwnjRestorer.restore("بینالمللی"))
    assertEquals("به\u200Cاستثنای", PersianZwnjRestorer.restore("بهاستثنای"))
    assertEquals("به\u200Cویژه", PersianZwnjRestorer.restore("بهویژه"))
    assertEquals("نقل\u200Cقول", PersianZwnjRestorer.restore("نقلقول"))
    assertEquals("ایرانی\u200Cای", PersianZwnjRestorer.restore("ایرانیای"))
    assertEquals("خانه\u200Cای", PersianZwnjRestorer.restore("خانهای"))
    assertEquals("گفت\u200Cوگو", PersianZwnjRestorer.restore("گفتوگو"))
    assertEquals("رفت\u200Cوآمد", PersianZwnjRestorer.restore("رفتوآمد"))
    assertEquals("هم\u200Cچنین پژوهش\u200Cگاه و روش\u200Cشناسی", PersianZwnjRestorer.restore("همچنین پژوهشگاه و روششناسی"))
  }

  @Test
  fun `preserves standalone preposition به and extended protected exception words`() {
    assertEquals("به تهران و به رغم دشواری‌ها", PersianZwnjRestorer.restore("به تهران و به رغم دشواری‌ها"))
    val protectedWords = listOf(
      "میدان", "میان", "میوه", "میز", "میزبان", "میل", "میلاد", "میهن", "میخ", "مینا",
      "نمک", "نماز", "نمونه", "نمودار", "نمایش", "تنها", "انتها", "اشتها",
      "بها", "رها", "گواه", "شاه", "بهار", "صدا", "جدا", "خدا", "شما", "آنها", "اینها",
      "دریای", "دنیای", "مزایای", "هدایای", "قضایای",
    )
    for (word in protectedWords) {
      assertEquals("Protected word '$word' must stay intact", word, PersianZwnjRestorer.restore(word))
    }
  }
}
