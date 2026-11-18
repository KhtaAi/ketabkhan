package com.ketabkhan.app.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianReadingOrderRepairerTest {

  @Test
  fun `swaps inverted unpaired guillemets without disturbing normal pairs`() {
    assertEquals("او «نوش‌آفرین» را دید.", PersianReadingOrderRepairer.repairLine("او »نوش‌آفرین« را دید."))
    assertEquals("«اول» و «دوم» درست است.", PersianReadingOrderRepairer.repairLine("«اول» و «دوم» درست است."))
  }

  @Test
  fun `repairs high-confidence character-reversed visual line preserving digits`() {
    val visual = ".تسا ناریا یخیرات رهش نارهت 1299 رد"
    assertEquals("در 1299 تهران شهر تاریخی ایران است.", PersianReadingOrderRepairer.repairLine(visual))
  }

  @Test
  fun `repairs high-confidence word-reversed Persian sentence`() {
    val wordReversed = ".آمد تهران به ۱۲۹۹ سال در رضاخان"
    val repaired = PersianReadingOrderRepairer.repairLine(wordReversed)
    assertEquals("رضاخان در سال ۱۲۹۹ به تهران آمد.", repaired)
  }

  @Test
  fun `never worsens valid or ambiguous Persian lines`() {
    val normalLines = listOf(
      "رضاخان در سال ۱۲۹۹ به تهران آمد.",
      "می‌روم به خانه تا کتاب بخوانم",
      "درخت سبز و آسمان آبی",
      "سلام دنیا!",
      "فصل اول",
    )
    for (line in normalLines) {
      assertEquals("Line should remain unchanged: $line", line, PersianReadingOrderRepairer.repairLine(line))
    }
  }
}
