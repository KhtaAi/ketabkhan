package com.ketabkhan.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ketabkhan.app.R

val VazirmatnFontFamily = FontFamily(
  Font(R.font.vazirmatn_regular, FontWeight.Normal),
  Font(R.font.vazirmatn_medium, FontWeight.Medium),
  Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

val KetabkhanTypography = Typography(
  displayLarge = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 40.sp,
    lineHeight = 52.sp,
  ),
  displayMedium = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    lineHeight = 46.sp,
  ),
  displaySmall = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 40.sp,
  ),
  headlineLarge = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 38.sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 32.sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 30.sp,
  ),
  titleLarge = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 30.sp,
  ),
  titleMedium = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 26.sp,
  ),
  titleSmall = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 22.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 28.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 24.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 20.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 18.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = VazirmatnFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
  ),
)
