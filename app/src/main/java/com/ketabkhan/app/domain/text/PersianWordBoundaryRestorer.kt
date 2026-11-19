package com.ketabkhan.app.domain.text

/**
 * Pure Kotlin lexical safety net for Persian word boundaries.
 * Separates accidentally glued independent words and normalizes misplaced ZWNJs
 * between distinct words into full spaces, while strictly protecting root nouns and affixes.
 */
object PersianWordBoundaryRestorer {

  private const val ZWNJ = '\u200C'

  val protectedWords = setOf(
    // میدان و هم‌خانواده‌ها
    "میدان", "میدانی", "میدانها", "میدانهای", "میدانچه",
    // میان و هم‌خانواده‌ها
    "میان", "میانی", "میانه", "میانه‌رو", "میانجی", "میانگین", "میانبر",
    // میوه و هم‌خانواده‌ها
    "میوه", "میوهها", "میوههای", "میوههایی", "میوه‌فروش", "میوه‌جات",
    // میز و هم‌خانواده‌ها
    "میز", "میزها", "میزهای", "میزهایی", "میزگرد", "میزتحریر", "میزان", "میزبان", "میزبانی",
    // میل و هم‌خانواده‌ها
    "میل", "میلی", "میلاد", "میلادی", "میلان", "میلانی", "میلیون", "میلیارد", "میلیمتری", "میلیمتر", "میلیگرم",
    // میهن، میخ، مینا و هم‌خانواده‌ها
    "میهن", "میهنی", "میهنم", "میهندوست", "میهن‌پرست",
    "میخ", "میخی", "میخک", "میخچه", "مینا", "مینایی", "میناکاری",
    "میعاد", "میکده", "میخانه", "میراث", "میش", "میترا", "مینو", "مینوی",
    "میهمان", "میهمانی", "میگسار", "میخواره", "میکروب", "میکروسکوپ", "میگرن", "میگو", "میسر", "میمون",
    // نمک، نماز، نمونه، نمودار، نمایش
    "نمک", "نمکی", "نمکدان", "نمکزار", "نمکین",
    "نماز", "نمازی", "نمازگزار", "نمازخانه",
    "نمونه", "نمونهها", "نمونههای", "نمونههایی",
    "نمودار", "نمودارها", "نمودارهای",
    "نمایش", "نمایشی", "نمایشگاه", "نمایشنامه", "نمایان",
    "نمد", "نمدی", "نماد", "نمادین", "نمره", "نمناک", "نمود", "نمودن",
    // استثناهای ریشه با «ها» / «ه»
    "تنها", "تنهایی", "انتها", "انتهایی", "اشتها", "اشتهایی", "منتها",
    "بها", "بهای", "بهایی", "رها", "رهایی", "گواه", "گواهی", "گواهان",
    "شاه", "شاهان", "شاهنشاه", "شاهین", "آشیانه", "آشیانه‌ها",
    "شناسنامه", "شناسنامه‌ها", "شناسنامه‌ای",
    // واژگان آغازشده با به...
    "بهار", "بهاری", "بهمن", "بهبود", "بهبودی", "بهتر", "بهترین",
    "بهشت", "بهشتی", "بهداشت", "بهداشتی", "بهروز", "بهزاد", "بهمنشیر",
    // واژگان آغازشده با واو
    "ورزش", "وزارت", "وجود", "وضعیت", "وصول", "وقوع", "ورود", "وکیل", "وکالت",
    "ویژه", "وام", "واجب", "واحد", "وارث", "واقع", "واقعی", "والدین", "وحشت",
    "وداع", "وزن", "وقف", "ویران", "ولایت", "وضوح", "وفات", "وفادار", "وقت", "وقفه",
    "ولی", "ولیکن", "وحید", "وحشی", "وسعت", "وسیله", "وسایل", "وسوسه", "وعده",
    // دیگر واژگان مستقل
    "بیستون", "اصفهان", "تهران", "شیراز", "ایران", "مشهد", "تبریز",
    "نوش", "نوشابه", "نوشیدنی", "نوروز", "نوجوان", "نوزاد", "نوآوری",
  )

  // Specific high-frequency glued phrase mappings observed in real books
  private val exactGluedPhrases = mapOf(
    "درآنروزگار" to "در آن روزگار",
    "درآن" to "در آن",
    "برایاجرایقانونی" to "برای اجرای قانونی",
    "برایاجرای" to "برای اجرای",
    "رااجباریمیکرد" to "را اجباری می‌کرد",
    "رااجباری" to "را اجباری",
    "اجباریمیکرد" to "اجباری می‌کرد",
    "وبسیاری" to "و بسیاری",
    "بهرغم" to "به رغم",
    "هنوزشناسنامه" to "هنوز شناسنامه",
    "بیستونهرروزه" to "بیستون هر روزه",
    "هرروزه" to "هر روزه",
    "نوشتهاند" to "نوشته‌اند",
    "میدانند" to "می‌دانند",
    "میتوان" to "می‌توان",
    "ناروشنیها" to "ناروشنی‌ها",
    "نادانستههای" to "نادانسته‌های",
  )

  // Independent words that must be separated from leading function words
  private val gluedLeadingFunctionWordPatterns = listOf(
    // در + آن / این / باره / عین / مورد / میان / نتیجه
    Regex("(?<![\\u0621-\\u06D5])در(آن|این|باره|عین|مورد|میان|نتیجه|میان|حال|زمان|واقع|مجموع|برابر|اختیار|دسترس)(?![\\u0621-\\u06D5])") to "در $1",
    // برای + اجرای / این / آن / نمونه
    Regex("(?<![\\u0621-\\u06D5])برای(اجرای|اجرا|این|آن|نمونه|همیشه|همین|تحقق|جلوگیری)(?![\\u0621-\\u06D5])") to "برای $1",
    // را + اجباری / این / آن
    Regex("(?<![\\u0621-\\u06D5])را(اجباری|این|آن|برای|به)(?![\\u0621-\\u06D5])") to "را $1",
    // به + رغم / عنوان / ویژه / طور / نام / سوی / دست / کار / شمار
    Regex("(?<![\\u0621-\\u06D5])به(رغم|عنوان|ویژه|طور|نام|سوی|دست|کار|شمار|همراه|خاطر|منظور|علت|سبب|جا|شکل)(?![\\u0621-\\u06D5])") to "به $1",
    // هنوز + شناسنامه / هم / در / به / یک
    Regex("(?<![\\u0621-\\u06D5])هنوز(شناسنامه|هم|در|به|یک|آن|این)(?![\\u0621-\\u06D5])") to "هنوز $1",
    // بیستون + هرروزه
    Regex("(?<![\\u0621-\\u06D5])بیستون(هرروزه|تاریخ)(?![\\u0621-\\u06D5])") to "بیستون $1",
    // هر + روزه / یک / روز / بار / سال / ماه / زمان / کس
    Regex("(?<![\\u0621-\\u06D5])هر(روزه|یک|روز|بار|سال|ماه|زمان|کس)(?![\\u0621-\\u06D5])") to "هر $1",
  )

  // Independent function words that accidentally got ZWNJ instead of a space
  private val misplacedZwnjBetweenWordsRegex = Regex(
    "(?<![\\u0621-\\u06D5])(در|برای|به|از|با|را|که|این|آن|هنوز|بیستون|هر|یا|تا|اما|ولی|چون)\\u200C([\\u0621-\\u06D5]{2,})"
  )

  // ZWNJ before leading و (e.g. "روز‌وبسیاری")
  private val zwnjBeforeVavRegex = Regex("([\\u0621-\\u06D5]{2,})\\u200C(و\\s+[\\u0621-\\u06D5]{2,})")

  fun restoreWordBoundaries(text: String): String {
    if (text.isBlank()) return text

    var result = text

    // 1. First normalize misplaced ZWNJ between independent function words and their targets
    result = misplacedZwnjBetweenWordsRegex.replace(result) { match ->
      val func = match.groupValues[1]
      val next = match.groupValues[2]
      // Do not touch valid cases like به+علاوه or به+تدریج if desired, but in general "به" before a full noun is "به <noun>"
      "$func $next"
    }
    result = zwnjBeforeVavRegex.replace(result, "$1 $2")

    // 2. Exact high-confidence multi-word glued phrases
    for ((glued, separated) in exactGluedPhrases) {
      if (result.contains(glued)) {
        result = result.replace(glued, separated)
      }
    }

    // 3. Leading function word regex substitutions with protection check
    for ((regex, replacement) in gluedLeadingFunctionWordPatterns) {
      result = regex.replace(result) { match ->
        val matchedStr = match.value
        if (matchedStr in protectedWords) {
          matchedStr
        } else {
          match.destructured.let { (captured) ->
            val prefix = matchedStr.removeSuffix(captured)
            "$prefix $captured"
          }
        }
      }
    }

    // 4. Safely split glued "و" before full words: "وبسیاری" -> "و بسیاری"
    result = splitGluedVav(result)

    // 5. Restore verb & plural ZWNJs that might have been glued without space
    result = restoreSpecificAffixZwnj(result)

    return result
  }

  private val gluedVavPattern = Regex("(?<![\\u0621-\\u06D5])و([\\u0621-\\u06D5]{3,})(?![\\u0621-\\u06D5])")

  private fun splitGluedVav(text: String): String {
    return gluedVavPattern.replace(text) { match ->
      val token = match.value
      val rest = match.groupValues[1]
      if (token in protectedWords || rest in protectedWords) {
        token
      } else if (isKnownGluedVavTarget(rest)) {
        "و $rest"
      } else {
        token
      }
    }
  }

  private fun isKnownGluedVavTarget(word: String): Boolean {
    val knownVavTargets = setOf(
      "بسیاری", "بسیار", "سپس", "نیز", "هم", "دیگر", "دیگران",
      "همه", "همگی", "برخی", "بعضی", "این", "آن", "اینها", "آنها",
      "بیشتر", "کمتر", "مردم", "جامعه", "کشور", "تاریخ", "فرهنگ",
      "پژوهش", "کتاب", "قلم", "نویسنده", "خواننده", "داستان",
    )
    return word in knownVavTargets
  }

  private fun restoreSpecificAffixZwnj(text: String): String {
    var s = text
    // "نوشتهاند" -> "نوشته‌اند"
    s = s.replace(Regex("(?<![\\u0621-\\u06D5])(نوشته|خوانده|دیده|گفته|شنیده|رفته|آمده|مانده|شده|کرده)اند(?![\\u0621-\\u06D5])"), "$1${ZWNJ}اند")
    // "میدانند" -> "می‌دانند" (not میدان!)
    s = s.replace(Regex("(?<![\\u0621-\\u06D5])میدانند(?![\\u0621-\\u06D5])"), "می${ZWNJ}دانند")
    // "میتوان" -> "می‌توان"
    s = s.replace(Regex("(?<![\\u0621-\\u06D5])میتوان(?![\\u0621-\\u06D5])"), "می${ZWNJ}توان")
    // "ناروشنیها" -> "ناروشنی‌ها"
    s = s.replace(Regex("(?<![\\u0621-\\u06D5])ناروشنیها(?![\\u0621-\\u06D5])"), "ناروشنی${ZWNJ}ها")
    // "نادانستههای" -> "نادانسته‌های"
    s = s.replace(Regex("(?<![\\u0621-\\u06D5])نادانستههای(?![\\u0621-\\u06D5])"), "نادانسته${ZWNJ}های")
    return s
  }
}
