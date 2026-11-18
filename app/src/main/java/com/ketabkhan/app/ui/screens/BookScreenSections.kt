package com.ketabkhan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.domain.tts.TtsAvailability
import com.ketabkhan.app.ui.components.KetabkhanPrimaryButton
import com.ketabkhan.app.ui.components.KetabkhanSecondaryButton
import com.ketabkhan.app.ui.components.WarningBanner

@Composable
internal fun PageTextCard(
  page: ExtractedPage,
  textSource: BookTextSource,
) {
  val displayedText = when (textSource) {
    BookTextSource.CLEANED -> page.cleanedText
    BookTextSource.RAW -> page.rawText
  }
  val isLegacyMissingRaw = textSource == BookTextSource.RAW && page.rawText.isBlank() && !page.isEmpty
  val effectiveCharCount = when {
    page.isEmpty -> 0
    textSource == BookTextSource.CLEANED -> page.charCount
    else -> displayedText.length
  }

  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (page.isEmpty) {
        MaterialTheme.colorScheme.surfaceContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("page_card_${page.pageNumber}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (textSource == BookTextSource.RAW) {
            MaterialTheme.colorScheme.secondaryContainer
          } else {
            MaterialTheme.colorScheme.primaryContainer
          },
        ) {
          val badgeLabel = if (textSource == BookTextSource.RAW) {
            "صفحهٔ ${PersianDigits.formatInt(page.pageNumber)} • خام"
          } else {
            "صفحهٔ ${PersianDigits.formatInt(page.pageNumber)}"
          }
          Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (textSource == BookTextSource.RAW) {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }

        if (!page.isEmpty && !isLegacyMissingRaw) {
          Text(
            text = "${PersianDigits.formatInt(effectiveCharCount)} نویسه",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      when {
        page.isEmpty -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Default.ImageNotSupported,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp),
            )
            Text(
              text = "صفحهٔ بدون متن",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        isLegacyMissingRaw -> {
          Text(
            text = "متن خام برای این صفحه در نسخهٔ قدیمی ذخیره نشده است. برای مشاهده و مقایسهٔ متن خام، «استخراج دوباره» را بزنید.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        else -> {
          Text(
            text = displayedText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("page_text_${page.pageNumber}"),
          )
        }
      }
    }
  }
}

@Composable
internal fun RangePreviewContent(
  state: BookUiState.Loaded,
  onReextractClick: () -> Unit,
  onDismissError: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val summary = state.rangeSummary
  val textSource = state.textSource
  LazyColumn(
    modifier = modifier.testTag("range_preview_list"),
    contentPadding = PaddingValues(bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.isReextracting && state.reextractionProgress != null) {
      item(key = "range_reextract_progress") {
        com.ketabkhan.app.ui.components.ExtractionProgressCard(progress = state.reextractionProgress)
      }
    }

    if (state.reextractionError != null) {
      item(key = "range_reextract_error") {
        com.ketabkhan.app.ui.components.ErrorBanner(
          message = state.reextractionError,
          onDismiss = onDismissError,
        )
      }
    }

    if (state.content.isCacheOutdated) {
      item(key = "range_outdated_cache_banner") {
        com.ketabkhan.app.ui.components.OutdatedCacheBanner(
          onReextractClick = onReextractClick,
          isReextracting = state.isReextracting,
        )
      }
    }

    if (summary.emptyPageCount > 0) {
      item {
        WarningBanner(
          title = "صفحه‌های بدون متن در بازه",
          message = "از ${PersianDigits.formatInt(summary.selectedPageCount)} صفحهٔ انتخاب‌شده، ${PersianDigits.formatInt(summary.emptyPageCount)} صفحه بدون متن است و به موتور صوتی ارسال نمی‌شود.",
        )
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val sourceLabel = if (textSource == BookTextSource.RAW) "متن خام" else "متن پاکسازیشده"
          Text(
            text = "پیشنمایش $sourceLabel بازهٔ ${PersianDigits.formatInt(summary.startPage)} تا ${PersianDigits.formatInt(summary.endPage)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = if (summary.previewText.isBlank()) {
              "متنی در این بازه یافت نشد."
            } else {
              summary.previewText
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("range_preview_body"),
          )
        }
      }
    }

    if (summary.sentences.isNotEmpty()) {
      item {
        Text(
          text = "جمله‌های آماده‌سازی‌شده برای سنتز (${PersianDigits.formatInt(summary.sentences.size)} جمله)",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      itemsIndexed(summary.sentences) { index, sentence ->
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
          ) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
              Text(
                text = PersianDigits.formatInt(index + 1),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              )
            }
            Text(
              text = sentence,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

@Composable
internal fun TtsActionFooter(
  ttsAvailability: TtsAvailability,
) {
  val disabledReason = when (ttsAvailability) {
    is TtsAvailability.Ready -> null
    is TtsAvailability.Unavailable -> ttsAvailability.reason
  }

  Surface(
    tonalElevation = 3.dp,
    shadowElevation = 6.dp,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        KetabkhanPrimaryButton(
          text = "خواندن صوتی",
          onClick = {},
          enabled = false,
          icon = Icons.Default.GraphicEq,
          modifier = Modifier
            .weight(1f)
            .testTag("read_aloud_button"),
        )
        KetabkhanSecondaryButton(
          text = "خروجی MP3",
          onClick = {},
          enabled = false,
          icon = Icons.Default.AudioFile,
          modifier = Modifier
            .weight(1f)
            .testTag("export_mp3_button"),
        )
      }

      if (disabledReason != null) {
        Text(
          text = disabledReason,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("tts_unavailable_reason"),
        )
      }
    }
  }
}
