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
  fun `restores glued mi verbs while protecting exception dictionary words`() {
    assertEquals("می‌گویم که میز در میدان است.", PersianZwnjRestorer.restore("میگویم که میز در میدان است."))
    assertEquals(
      "میل و میهن و میوه و نمک و نماز نباید بشکنند.",
      PersianZwnjRestorer.restore("میل و میهن و میوه و نمک و نماز نباید بشکنند."),
    )
    assertEquals(
      "می ناب و نوش جان در نوروز مآل خوبی دارد.",
      PersianZwnjRestorer.restore("می ناب و نوش جان در نوروز مآل خوبی دارد."),
    )
  }

  @Test
  fun `restores suffixes ezafe and silent he enclitics`() {
    assertEquals("کتاب‌ها و کتاب‌های قدیمی و خانهٔ مادر", PersianZwnjRestorer.restore("کتاب ها و کتاب های قدیمی و خانه ی مادر"))
    assertEquals("بزرگ‌ترین و ساده‌تر و رفته‌ام", PersianZwnjRestorer.restore("بزرگ ترین و ساده تر و رفته ام"))
    assertEquals("صفحهٔ کتاب", PersianZwnjRestorer.restore("صفحه ی کتاب"))
  }
}
