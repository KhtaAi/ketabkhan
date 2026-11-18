package com.ketabkhan.app.domain.text

/**
 * Pure Kotlin morphological ZWNJ (نیم‌فاصله) restorer for Persian text extracted from PDFs.
 * Free of Android framework dependencies for fast JVM unit testing.
 */
object PersianZwnjRestorer {

  private const val ZWNJ = '\u200C'

  private val protectedNonSplitWords = setOf(
    "میز", "میزها", "میزهای", "میزان", "میزبانی", "میزبان",
    "میدان", "میدانی", "میدانها", "میدانهای",
    "میل", "میلی", "میلاد", "میلادی", "میلیون", "میلیارد", "میلیمتری", "میلیمتر",
    "میهن", "میهنی", "میهنم",
    "میوه", "میوهها", "میوههای", "میعاد", "میکده", "میخانه", "میراث",
    "میخ", "میخک", "میش", "مینا", "میترا", "مینو", "میهمان", "میهمانی",
    "میگسار", "میخواره", "میکروب", "میکروسکوپ", "میگرن", "میگو",
    "نمک", "نمکی", "نمکدان", "نمد", "نمدی", "نماد", "نمادین",
    "نماز", "نمازگزار", "نمایش", "نمایشگاه", "نمایشنامه", "نمایان",
    "نمره", "نمودار", "نمونه", "نمناک", "نمود", "نمودن",
    "نوش", "نوشابه", "نوشیدنی", "نوروز", "نوجوان", "نوزاد", "نوآوری",
    "لآلی", "مآل", "مآب", "مآخذ",
  )

  private val nonVerbWordsAfterMi = setOf(
    "و", "از", "به", "در", "با", "را", "که", "تا", "یا", "اما", "ولی",
    "اگر", "هم", "نیز", "این", "آن", "یک", "دو", "سه", "چهار", "پنج",
    "بسیار", "خیلی", "سرخ", "ناب", "کهنه", "سفید", "شیراز", "انگور",
    "میدان", "میز", "میل", "میهن",
  )

  private val functionWordsNoSuffix = setOf(
    "از", "به", "در", "که", "با", "تا", "یا", "و", "را", "اگر", "اما",
    "ولی", "چون", "زیرا", "هم", "نیز", "پس", "برای", "بر", "بی",
  )

  private val commonVerbBasesForGluedSplit = listOf(
    // Past / present high-frequency stems
    "شود", "شد", "شوند", "شدند", "شوم", "شدم", "شوی", "شدی", "شویم", "شدیم", "شوید", "شدید",
    "کند", "کرد", "کنند", "کردند", "کنم", "کردم", "کنی", "کردی", "کنیم", "کردیم", "کنید", "کردید",
    "گوید", "گفت", "گویند", "گفتند", "گویم", "گفتم", "گویی", "گفتی", "گوییم", "گفتیم", "گویید", "گفتید",
    "خواهد", "خواست", "خواهند", "خواستند", "خواهم", "خواستم", "خواهی", "خواستی", "خواهیم", "خواستیم", "خواهید", "خواستید",
    "تواند", "توانست", "توانند", "توانستند", "توانم", "توانستم", "توانی", "توانستی", "توانیم", "توانستیم", "توانید", "توانستید", "توان",
    "داند", "دانست", "دانند", "دانستند", "دانم", "دانستم", "دانی", "دانستی", "دانیم", "دانستیم", "دانید", "دانستید",
    "آید", "آمد", "آیند", "آمدند", "آیم", "آمدم", "آیی", "آمدی", "آییم", "آمدیم", "آیید", "آمدید",
    "رود", "رفت", "روند", "رفتند", "روم", "رفتم", "روی", "رفتی", "رویم", "رفتیم", "روید", "رفتید",
    "زد", "زند", "زنند", "زدند", "زنم", "زدم", "زنی", "زدی", "زنیم", "زدیم", "زنید", "زدید",
    "بیند", "دید", "بینند", "دیدند", "بینم", "دیدم", "بینی", "دیدی", "بینیم", "دیدیم", "بینید", "دیدید",
    "دهد", "داد", "دهند", "دادند", "دهم", "دادم", "دهی", "دادی", "دهیم", "دادیم", "دهید", "دادید",
    "گیرد", "گرفت", "گیرند", "گرفتند", "گیرم", "گرفتم", "گیری", "گرفتی", "گیریم", "گرفتیم", "گیرید", "گرفتید",
    "خورد", "خورند", "خوردند", "خورم", "خوردم", "خوری", "خوردی", "خوریم", "خوردیم", "خورید", "خوردید",
    "ماند", "مانند", "ماندند", "مانم", "ماندم", "مانی", "ماندی", "مانیم", "ماندیم", "مانید", "ماندید",
    "دارد", "داشت", "دارند", "داشتند", "دارم", "داشتم", "داری", "داشتی", "داریم", "داشتیم", "دارید", "داشتید",
    "باشد", "باشند", "باشم", "باشی", "باشیم", "باشید",
    "نویسد", "نوشت", "نویسند", "نوشتند", "خواند", "خوانند", "خواندند",
    "برد", "برند", "بردند", "آورد", "آورند", "آوردند",
    "فهمد", "فهمید", "پرسد", "پرسید", "شناسد", "شناخت",
    "گذارد", "گذاشت", "نشیند", "نشست", "فروشد", "فروخت", "خرد", "خرید",
    "سازد", "ساخت", "افتد", "افتاد", "ایستد", "ایستاد",
    "پندارد", "پنداشت", "شمارد", "شمرد", "پذیرد", "پذیرفت",
    "ریزد", "ریخت", "سوزد", "سوخت", "کشد", "کشید", "بندد", "بست",
    "خندد", "خندید", "گرید", "گریست", "ترسد", "ترسید", "گذرد", "گذشت",
    "یافت", "یابد", "آفریند", "آفرید", "چرخد", "چرخید", "پوشد", "پوشید",
    "گردد", "گردم", "گردی", "گردیم", "گردید", "گردند", "گشت", "گشتند",
    "رسد", "رسید", "رسند", "رسیدند", "خیزد", "خاست", "اندازد", "انداخت",
  ).toSet()

  private val compoundFirstAlifMaddaPrefixes = setOf(
    "نوش", "خوش", "دل", "هم", "کم", "پر", "پاک", "کار", "دست", "رنگ",
    "مهر", "جهان", "گل", "سر", "تن", "خود", "روان", "زود", "دیر", "پسا",
  )

  private val alifMaddaSecondParts = setOf(
    "آفرین", "آفرینی", "آلود", "آلوده", "آمیز", "آور", "آوری", "آمد", "آمدن",
    "آمدید", "آرام", "آرا", "آرایی", "آشنا", "آشنایی", "آموز", "آموزی",
    "آهنگ", "آباد", "آبادی", "آزما", "آزمایی", "آورد", "آورده",
  )

  private val consonantHeNonSilentEndings = listOf("اه", "وه")
  private val consonantHeExceptions = setOf("توجیه", "شبیه", "وجیه", "فقیه", "نزیه", "مکروه", "انبوه")

  // 1. Broken inside می / نمی prefix due to PDFBox spacing: "م یزد" -> "می‌زد", "نم یزد" -> "نمی‌زد"
  private val brokenMiRegex = Regex(
    "(?<![\\u0621-\\u06D5\\p{Nd}])م\\s+ی([\\u0621-\\u06D5]{2,})(?![\\u0621-\\u06D5])"
  )
  private val brokenNemiRegex = Regex(
    "(?<![\\u0621-\\u06D5\\p{Nd}])نم\\s+ی([\\u0621-\\u06D5]{2,})(?![\\u0621-\\u06D5])"
  )

  // 2. Shifted single-consonant before Alif Madda (e.g., "نو شآفرین" -> "نوش‌آفرین")
  private val shiftedConsonantBeforeAlifMaddaRegex = Regex(
    "([\\u0621-\\u06D5]{2,})\\s+([بپتثجچحخدذرزژسشصضطظعغفقکگلمنوهی])(آ[\\u0621-\\u06D5]{2,})"
  )

  // Compound noun/adj with space before آ... (e.g., "نوش آفرین" -> "نوش‌آفرین")
  private val spacedAlifMaddaCompoundRegex = Regex(
    "([\\u0621-\\u06D5]{2,})\\s+(آ[\\u0621-\\u06D5]{2,})"
  )

  // 3. Compound verb prefixes with spaces: "بر می گردد" / "در می آید" -> "برمی‌گردد" / "درمی‌آید"
  private val compoundMiRegex = Regex(
    "(?<![\\u0621-\\u06D5])(بر|در|فرا|باز|ور)\\s+(ن)?می\\s+([\\u0621-\\u06D5]{2,})"
  )

  // Standalone "می <verb>" or "نمی <verb>"
  private val spacedMiVerbRegex = Regex(
    "(?<![\\u0621-\\u06D5\\u200C])(ن?می)\\s+([\\u0621-\\u06D5]{2,})"
  )

  // Glued "می<verb>" / "نمی<verb>" when missing ZWNJ
  private val gluedMiVerbRegex = Regex(
    "(?<![\\u0621-\\u06D5\\u200C])(ن?می)([\\u0621-\\u06D5]{2,})(?![\\u0621-\\u06D5])"
  )

  // 4. Plural & superlative suffixes separated by space
  private val spacedSuffixRegex = Regex(
    "([\\u0621-\\u06D5]{2,})\\s+(هایشان|هایتان|هایمان|هایی|هایش|هایت|هایم|های|ها|ترین)(?![\\u0621-\\u06D5])"
  )

  // Comparative "تر" after silent ه or adjectives of length >= 3
  private val spacedTarSuffixRegex = Regex(
    "([\\u0621-\\u06D5]{2,}ه)\\s+(تر)(?![\\u0621-\\u06D5])"
  )

  // Glued silent ه + ها/های/هایی without ZWNJ (e.g., "خانهها" -> "خانه\u200Cها")
  private val gluedSilentHePluralRegex = Regex(
    "([\\u0621-\\u06D5]{2,}ه)(هایشان|هایتان|هایمان|هایی|هایش|هایت|هایم|های|ها)(?![\\u0621-\\u06D5])"
  )

  // 5. Ezafe "ه ی" or "ه\u200Cی" -> "هٔ"
  private val silentHeEzafeRegex = Regex(
    "([\\u0621-\\u06D5]{2,}ه)(?:\\s+|\\u200C)ی(?![\\u0621-\\u06D5])"
  )

  // 6. Enclitic pronouns / copula after silent ه (e.g., "رفته ام" -> "رفته‌ام")
  private val silentHeEncliticRegex = Regex(
    "([\\u0621-\\u06D5]{2,}ه)\\s+(ام|ای|اش|ات|ایم|اید|اند)(?![\\u0621-\\u06D5])"
  )

  fun restore(text: String): String {
    if (text.isBlank()) return text

    var result = text

    // Step 1: Fix split prefixes "م یزد" -> "می‌زد", "نم یزد" -> "نمی‌زد"
    result = brokenNemiRegex.replace(result) { match ->
      val stem = match.groupValues[1]
      if (isLikelyVerbTail(stem)) "نمی$ZWNJ$stem" else match.value
    }
    result = brokenMiRegex.replace(result) { match ->
      val stem = match.groupValues[1]
      if (isLikelyVerbTail(stem) || stem.length >= 2) "می$ZWNJ$stem" else match.value
    }

    // Step 2: Fix shifted consonant before Alif Madda ("نو شآفرین" -> "نوش‌آفرین")
    result = shiftedConsonantBeforeAlifMaddaRegex.replace(result) { match ->
      val left = match.groupValues[1]
      val detached = match.groupValues[2]
      val alifPart = match.groupValues[3]
      val secondWordCheck = "$detached$alifPart"
      if (secondWordCheck in protectedNonSplitWords || isKnownVerbBase(secondWordCheck)) {
        match.value
      } else {
        "$left$detached$ZWNJ$alifPart"
      }
    }

    result = spacedAlifMaddaCompoundRegex.replace(result) { match ->
      val left = match.groupValues[1]
      val right = match.groupValues[2]
      if (left in compoundFirstAlifMaddaPrefixes && right in alifMaddaSecondParts) {
        "$left$ZWNJ$right"
      } else {
        match.value
      }
    }

    // Step 3: Compound verb prefixes ("بر می گردد" -> "برمی‌گردد")
    result = compoundMiRegex.replace(result) { match ->
      val prefix = match.groupValues[1]
      val neg = match.groupValues[2]
      val stem = match.groupValues[3]
      if (stem !in nonVerbWordsAfterMi && isLikelyVerbTail(stem)) {
        "$prefix${neg}می$ZWNJ$stem"
      } else {
        match.value
      }
    }

    // Standalone spaced "می <verb>" / "نمی <verb>"
    result = spacedMiVerbRegex.replace(result) { match ->
      val prefix = match.groupValues[1]
      val wordAfter = match.groupValues[2]
      if (wordAfter !in nonVerbWordsAfterMi && isLikelyVerbTail(wordAfter)) {
        "$prefix$ZWNJ$wordAfter"
      } else {
        match.value
      }
    }

    // Glued "می<verb>" / "نمی<verb>" respecting exception dictionary
    result = gluedMiVerbRegex.replace(result) { match ->
      val fullWord = match.value
      val prefix = match.groupValues[1]
      val stem = match.groupValues[2]
      if (fullWord in protectedNonSplitWords || stem !in commonVerbBasesForGluedSplit) {
        fullWord
      } else {
        "$prefix$ZWNJ$stem"
      }
    }

    // Step 4: Suffixes ها، های، هایی، ترین، تر
    result = spacedSuffixRegex.replace(result) { match ->
      val base = match.groupValues[1]
      val suffix = match.groupValues[2]
      if (base in functionWordsNoSuffix) {
        match.value
      } else {
        "$base$ZWNJ$suffix"
      }
    }

    result = spacedTarSuffixRegex.replace(result) { match ->
      val base = match.groupValues[1]
      val suffix = match.groupValues[2]
      if (isSilentHeWord(base)) "$base$ZWNJ$suffix" else match.value
    }

    result = gluedSilentHePluralRegex.replace(result) { match ->
      val base = match.groupValues[1]
      val suffix = match.groupValues[2]
      if (isSilentHeWord(base)) "$base$ZWNJ$suffix" else match.value
    }

    // Step 5: Ezafe هٔ ("صفحه ی" -> "صفحهٔ")
    result = silentHeEzafeRegex.replace(result) { match ->
      val base = match.groupValues[1]
      if (isSilentHeWord(base)) "${base}ٔ" else match.value
    }

    // Step 6: Enclitics after silent ه ("رفته ام" -> "رفته‌ام")
    result = silentHeEncliticRegex.replace(result) { match ->
      val base = match.groupValues[1]
      val enclitic = match.groupValues[2]
      if (isSilentHeWord(base)) "$base$ZWNJ$enclitic" else match.value
    }

    // Cleanup any accidental duplicate ZWNJ
    return result.replace(Regex("$ZWNJ{2,}"), "$ZWNJ")
  }

  private fun isSilentHeWord(word: String): Boolean {
    if (word.length < 3 || word in functionWordsNoSuffix || word in consonantHeExceptions) {
      return false
    }
    if (!word.endsWith('ه')) return false
    return consonantHeNonSilentEndings.none { word.endsWith(it) }
  }

  private fun isKnownVerbBase(token: String): Boolean = token in commonVerbBasesForGluedSplit

  private fun isLikelyVerbTail(token: String): Boolean {
    if (token in commonVerbBasesForGluedSplit) return true
    if (token in protectedNonSplitWords || token in nonVerbWordsAfterMi) return false
    if (token.length < 2) return false
    // Recognize typical conjugated Persian verb endings when preceded explicitly by spaced "می" / "نمی"
    val verbEndings = listOf("یم", "ید", "ند", "دم", "دی", "تم", "تی", "ست")
    return verbEndings.any { token.endsWith(it) && token.length >= it.length + 1 }
  }
}
