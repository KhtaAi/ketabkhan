package com.ketabkhan.app.domain.text

/**
 * Pure Kotlin dictionary mapping glued Persian compound words to their canonical
 * ZWNJ-separated forms. Only exact full-token matches are mapped.
 */
object PersianCompoundDictionary {

  private const val ZWNJ = '\u200C'

  val compounds: Map<String, String> = mapOf(
    // Core required compounds
    "نقلقول" to "نقل${ZWNJ}قول",
    "روبهرو" to "روبه${ZWNJ}رو",
    "گفتوگو" to "گفت${ZWNJ}وگو",
    "رفتوآمد" to "رفت${ZWNJ}وآمد",
    "بینالمللی" to "بین${ZWNJ}المللی",
    "بهاستثنای" to "به${ZWNJ}استثنای",
    "بهویژه" to "به${ZWNJ}ویژه",
    "همچنین" to "هم${ZWNJ}چنین",
    "هیچگاه" to "هیچ${ZWNJ}گاه",
    "هیچوقت" to "هیچ${ZWNJ}وقت",
    "کتابخانه" to "کتاب${ZWNJ}خانه",
    "دانشگاه" to "دانش${ZWNJ}گاه",
    "پژوهشگاه" to "پژوهش${ZWNJ}گاه",
    "روششناسی" to "روش${ZWNJ}شناسی",

    // Safe high-frequency Persian compounds
    "بینالملل" to "بین${ZWNJ}الملل",
    "بینالنهرین" to "بین${ZWNJ}النهرین",
    "بینالاقوامی" to "بین${ZWNJ}الاقوامی",
    "بهاستثنا" to "به${ZWNJ}استثنا",
    "بهعلاوه" to "به${ZWNJ}علاوه",
    "بهخصوص" to "به${ZWNJ}خصوص",
    "بهطور" to "به${ZWNJ}طور",
    "بهمنظور" to "به${ZWNJ}منظور",
    "بهتدریج" to "به${ZWNJ}تدریج",
    "بهسادگی" to "به${ZWNJ}سادگی",
    "بهسرعت" to "به${ZWNJ}سرعت",
    "بهراحتی" to "به${ZWNJ}راحتی",
    "بهخوبی" to "به${ZWNJ}خوبی",
    "بهشدت" to "به${ZWNJ}شدت",
    "بهتازگی" to "به${ZWNJ}تازگی",
    "بهناچار" to "به${ZWNJ}ناچار",
    "جستوجو" to "جست${ZWNJ}وجو",
    "شستوشو" to "شست${ZWNJ}وشو",
    "پختوپز" to "پخت${ZWNJ}وپز",
    "خریدوفروش" to "خرید${ZWNJ}وفروش",
    "هیچیک" to "هیچ${ZWNJ}یک",
    "هیچکدام" to "هیچ${ZWNJ}کدام",
    "هیچکس" to "هیچ${ZWNJ}کس",
    "هیچچیز" to "هیچ${ZWNJ}چیز",
    "فوقالعاده" to "فوق${ZWNJ}العاده",
    "علیرغم" to "علی${ZWNJ}رغم",
    "برنامهریزی" to "برنامه${ZWNJ}ریزی",
    "پایهگذاری" to "پایه${ZWNJ}گذاری",
    "سرمایهگذاری" to "سرمایه${ZWNJ}گذاری",
    "تصمیمگیری" to "تصمیم${ZWNJ}گیری",
    "نتیجهگیری" to "نتیجه${ZWNJ}گیری",
    "پیشگفتار" to "پیش${ZWNJ}گفتار",
    "پیشفرض" to "پیش${ZWNJ}فرض",
    "پیشنمایش" to "پیش${ZWNJ}نمایش",
    "پاسخگویی" to "پاسخ${ZWNJ}گویی",
  )

  private val persianTokenRegex = Regex(
    "(?<![\\u0621-\\u06D5\\u200C])[\\u0621-\\u06D5]+(?![\\u0621-\\u06D5\\u200C])"
  )

  fun containsKey(token: String): Boolean = token in compounds

  fun restoreExactToken(token: String): String {
    return compounds[token] ?: token
  }

  fun restoreInText(text: String): String {
    if (text.isBlank()) return text
    return persianTokenRegex.replace(text) { match ->
      compounds[match.value] ?: match.value
    }
  }
}
