package com.ketabkhan.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ketabkhan.app.data.pdf.DocumentBlockType
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.domain.reading.ReadingItem
import com.ketabkhan.app.domain.reading.SkippedReadingBlock
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.domain.tts.TtsAvailability
import com.ketabkhan.app.ui.components.ErrorBanner
import com.ketabkhan.app.ui.components.ExtractionProgressCard
import com.ketabkhan.app.ui.components.KetabkhanPrimaryButton
import com.ketabkhan.app.ui.components.KetabkhanSecondaryButton
import com.ketabkhan.app.ui.components.OutdatedCacheBanner
import com.ketabkhan.app.ui.components.WarningBanner

@Composable
internal fun PageTextCard(
  page: ExtractedPage,
  textSource: BookTextSource,
  displayFormat: BookDisplayFormat = BookDisplayFormat.STRUCTURED,
) {
  var isSpacingExpanded by rememberSaveable(page.pageNumber) { mutableStateOf(false) }
  val displayedText = when (textSource) {
    BookTextSource.CLEANED -> page.cleanedText
    BookTextSource.RAW -> page.rawText
  }
  val isLegacyMissingRaw = textSource == BookTextSource.RAW && page.rawText.isBlank() && !page.isEmpty
  val showStructured = textSource == BookTextSource.CLEANED &&
    displayFormat == BookDisplayFormat.STRUCTURED &&
    page.blocks.isNotEmpty()
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
          val badgeLabel = when {
            textSource == BookTextSource.RAW -> "صفحهٔ ${PersianDigits.formatInt(page.pageNumber)} • خام"
            showStructured -> "صفحهٔ ${PersianDigits.formatInt(page.pageNumber)} • ساختاردار"
            else -> "صفحهٔ ${PersianDigits.formatInt(page.pageNumber)} • ساده"
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

        showStructured -> {
          StructuredPageBlocksColumn(page = page)
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

      val metrics = page.spacingMetrics
      if (metrics != null) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .clickable { isSpacingExpanded = !isSpacingExpanded }
              .testTag("page_spacing_toggle_${page.pageNumber}")
              .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
              )
              Text(
                text = "گزارش فاصله‌گذاری صفحه",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
              )
            }
            Icon(
              imageVector = if (isSpacingExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = if (isSpacingExpanded) "بستن گزارش" else "مشاهدهٔ گزارش",
              modifier = Modifier.size(20.dp),
              tint = MaterialTheme.colorScheme.primary,
            )
          }

          if (isSpacingExpanded) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("page_spacing_panel_${page.pageNumber}"),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                SpacingMetricRow("عرض مرجع فاصله:", "${PersianDigits.formatFloat(metrics.referenceSpaceWidthPt, 1)} پوینت")
                SpacingMetricRow("منبع عرض مرجع:", metrics.referenceSource.persianLabel)
                SpacingMetricRow("میانهٔ شکاف‌های سطر:", "${PersianDigits.formatFloat(metrics.medianGapPt, 1)} پوینت")
                SpacingMetricRow("تعداد فاصله‌های کامل:", PersianDigits.formatInt(metrics.fullSpaceCount))
                SpacingMetricRow("تعداد نیم‌فاصله‌ها:", PersianDigits.formatInt(metrics.zwnjCount))
                SpacingMetricRow("تعداد پیوستگی‌های نانویسه:", PersianDigits.formatInt(metrics.gluedJoinCount))
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SpacingMetricRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
      color = MaterialTheme.colorScheme.onSurface,
    )
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
  val plan = summary.readingPlan
  LazyColumn(
    modifier = modifier.testTag("range_preview_list"),
    contentPadding = PaddingValues(bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.isReextracting && state.reextractionProgress != null) {
      item(key = "range_reextract_progress") {
        ExtractionProgressCard(progress = state.reextractionProgress)
      }
    }

    if (state.reextractionError != null) {
      item(key = "range_reextract_error") {
        ErrorBanner(
          message = state.reextractionError,
          onDismiss = onDismissError,
        )
      }
    }

    if (state.content.isCacheOutdated) {
      item(key = "range_outdated_cache_banner") {
        OutdatedCacheBanner(
          onReextractClick = onReextractClick,
          isReextracting = state.isReextracting,
        )
      }
    }

    if (summary.emptyPageCount > 0) {
      item(key = "empty_pages_warning") {
        WarningBanner(
          title = "صفحه‌های بدون متن در بازه",
          message = "از ${PersianDigits.formatInt(summary.selectedPageCount)} صفحهٔ انتخاب‌شده، ${PersianDigits.formatInt(summary.emptyPageCount)} صفحه بدون متن است و به موتور صوتی ارسال نمی‌شود.",
        )
      }
    }

    item(key = "range_summary_card") {
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

    if (textSource == BookTextSource.CLEANED) {
      if (plan.items.isNotEmpty()) {
        item(key = "reading_plan_header") {
          Text(
            text = "برنامهٔ خواندن ساختاردار (${PersianDigits.formatInt(plan.items.size)} بخش • مکث تیتر: ${state.readingSettings.titlePauseDuration.persianLabel})",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
              .padding(top = 4.dp)
              .testTag("reading_plan_header"),
          )
        }

        items(plan.items, key = { "ri_${it.order}_${it.sourceBlockId}" }) { item ->
          ReadingPlanItemCard(item = item)
        }
      }

      if (plan.skippedBlocks.isNotEmpty()) {
        item(key = "skipped_blocks_header") {
          Text(
            text = "بخش‌های ردشده طبق تنظیمات (${PersianDigits.formatInt(plan.skippedBlocks.size)} مورد)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .padding(top = 6.dp)
              .testTag("skipped_blocks_header"),
          )
        }

        itemsIndexed(plan.skippedBlocks, key = { idx, blk -> "skip_${idx}_${blk.block.id}" }) { idx, skipped ->
          SkippedBlockCard(skipped = skipped, index = idx)
        }
      }
    } else if (summary.sentences.isNotEmpty()) {
      item(key = "raw_sentences_header") {
        Text(
          text = "بندهای متن خام (${PersianDigits.formatInt(summary.sentences.size)} مورد)",
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
private fun ReadingPlanItemCard(item: ReadingItem) {
  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (item.role == DocumentBlockType.PAGE_TITLE || item.role == DocumentBlockType.SUBTITLE) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
    ),
    modifier = Modifier
      .fillMaxWidth()
      .semantics(mergeDescendants = true) {}
      .testTag("reading_item_${item.order}"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
          ) {
            Text(
              text = PersianDigits.formatInt(item.order),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
          }
          BlockRoleBadge(type = item.role)
        }

        val pauseSec = PersianDigits.toPersianDigits(
          String.format(java.util.Locale.US, "%.1f", item.pauseAfterMillis / 1000f).replace('.', '٫')
        )
        Text(
          text = "ص ${PersianDigits.formatInt(item.pageNumber)} • مکث ${pauseSec}ث",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Text(
        text = item.spokenText,
        style = if (item.role == DocumentBlockType.PAGE_TITLE) {
          MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        } else {
          MaterialTheme.typography.bodyMedium
        },
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun SkippedBlockCard(
  skipped: SkippedReadingBlock,
  index: Int,
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    ),
    modifier = Modifier
      .fillMaxWidth()
      .semantics(mergeDescendants = true) {}
      .testTag("skipped_block_$index"),
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        BlockRoleBadge(type = skipped.block.type)
        Text(
          text = "ص ${PersianDigits.formatInt(skipped.pageNumber)} • ${skipped.reason}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Text(
        text = skipped.block.text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
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
