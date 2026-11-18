package com.ketabkhan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val KetabkhanLightColors = lightColorScheme(
  primary = TealInkPrimary,
  onPrimary = OnTealPrimary,
  primaryContainer = TealMistContainer,
  onPrimaryContainer = OnTealMistContainer,
  secondary = SaffronSecondary,
  onSecondary = OnSaffronSecondary,
  secondaryContainer = ApricotContainer,
  onSecondaryContainer = OnApricotContainer,
  tertiary = IndigoTertiary,
  onTertiary = OnIndigoTertiary,
  tertiaryContainer = IndigoMistContainer,
  onTertiaryContainer = OnIndigoMistContainer,
  background = IvoryBackground,
  onBackground = OnIvoryInk,
  surface = CreamSurface,
  onSurface = OnIvoryInk,
  surfaceContainerLow = CreamSurfaceLow,
  surfaceContainer = CreamSurfaceContainer,
  surfaceContainerHigh = CreamSurfaceHigh,
  onSurfaceVariant = SlateInkVariant,
  outline = ParchmentOutline,
  outlineVariant = ParchmentOutlineVariant,
  error = CrimsonError,
  onError = OnCrimsonError,
  errorContainer = CrimsonErrorContainer,
  onErrorContainer = OnCrimsonErrorContainer,
)

private val KetabkhanDarkColors = darkColorScheme(
  primary = LuminousTealDark,
  onPrimary = OnLuminousTealDark,
  primaryContainer = TealContainerDark,
  onPrimaryContainer = OnTealContainerDark,
  secondary = WarmAmberDark,
  onSecondary = OnWarmAmberDark,
  secondaryContainer = AmberContainerDark,
  onSecondaryContainer = OnAmberContainerDark,
  tertiary = SoftIndigoDark,
  onTertiary = OnSoftIndigoDark,
  tertiaryContainer = IndigoContainerDark,
  onTertiaryContainer = OnIndigoContainerDark,
  background = NightInkBackground,
  onBackground = OnNightCream,
  surface = NightSurface,
  onSurface = OnNightCream,
  surfaceContainerLow = NightSurfaceLow,
  surfaceContainer = NightSurfaceContainer,
  surfaceContainerHigh = NightSurfaceHigh,
  onSurfaceVariant = NightInkVariant,
  outline = NightOutline,
  outlineVariant = NightOutlineVariant,
  error = ErrorDark,
  onError = OnErrorDark,
  errorContainer = ErrorContainerDark,
  onErrorContainer = OnErrorContainerDark,
)

val KetabkhanShapes = Shapes(
  small = RoundedCornerShape(12.dp),
  medium = RoundedCornerShape(16.dp),
  large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun KetabkhanTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) KetabkhanDarkColors else KetabkhanLightColors

  CompositionLocalProvider(
    LocalLayoutDirection provides LayoutDirection.Rtl,
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = KetabkhanTypography,
      shapes = KetabkhanShapes,
    ) {
      CompositionLocalProvider(
        LocalTextStyle provides KetabkhanTypography.bodyLarge,
      ) {
        content()
      }
    }
  }
}
