package com.ketabkhan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.data.pdf.PdfQualityStatus
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.domain.tts.TtsAvailability
import com.ketabkhan.app.ui.components.ErrorBanner
import com.ketabkhan.app.ui.components.KetabkhanPrimaryButton
import com.ketabkhan.app.ui.components.KetabkhanSecondaryButton
import com.ketabkhan.app.ui.components.WarningBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
  viewModel: BookViewModel,
  onNavigateBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showRangePicker by rememberSaveable { mutableStateOf(false) }

  val topTitle = when (val state = uiState) {
    is BookUiState.Loaded -> state.content.meta.title
    else -> "کتاب"
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = topTitle,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        navigationIcon = {
          IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("book_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "بازگشت به کتابخانه",
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    bottomBar = {
      val loaded = uiState as? BookUiState.Loaded
      if (loaded != null) {
        TtsActionFooter(ttsAvailability = loaded.ttsAvailability)
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      when (val state = uiState) {
        is BookUiState.Loading -> {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }

        is BookUiState.Error -> {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 640.dp)
              .padding(24.dp),
          ) {
            ErrorBanner(message = state.message)
          }
        }

        is BookUiState.Loaded -> {
          BookLoadedContent(
            state = state,
            onViewModeChange = { viewModel.setViewMode(it) },
            onOpenRangePicker = { showRangePicker = true },
          )

          if (showRangePicker) {
            RangePickerSheet(
              pages = state.content.pages,
              initialStartPage = state.rangeStart,
              initialEndPage = state.rangeEnd,
              onComputePreview = { pages, s, e -> viewModel.previewRange(pages, s, e) },
              onConfirmRange = { start, end ->
                viewModel.applyPageRange(start, end)
                showRangePicker = false
              },
              onDismiss = { showRangePicker = false },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BookLoadedContent(
  state: BookUiState.Loaded,
  onViewModeChange: (BookViewMode) -> Unit,
  onOpenRangePicker: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .widthIn(max = 680.dp)
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val quality = state.content.meta.qualityReport
    if (quality.status != PdfQualityStatus.OK && quality.warningMessage != null) {
      val title = if (quality.status == PdfQualityStatus.LIKELY_SCANNED_IMAGE) {
        "هشدار PDF تصویری (OCR)"
      } else {
        "هشدار خرابی نگاشت فونت"
      }
      WarningBanner(
        title = title,
        message = quality.warningMessage,
      )
    }

    SingleChoiceSegmentedButtonRow(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("book_mode_segmented"),
    ) {
      SegmentedButton(
        selected = state.viewMode == BookViewMode.FULL_PAGES,
        onClick = { onViewModeChange(BookViewMode.FULL_PAGES) },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text("متن کامل کتاب")
      }
      SegmentedButton(
        selected = state.viewMode == BookViewMode.SELECTED_RANGE,
        onClick = { onViewModeChange(BookViewMode.SELECTED_RANGE) },
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Text("پیشنمایش بازه")
      }
    }

    Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "بازهٔ فعال: صفحهٔ ${PersianDigits.formatInt(state.rangeStart)} تا ${PersianDigits.formatInt(state.rangeEnd)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${PersianDigits.formatInt(state.rangeSummary.selectedPageCount)} صفحه • ${PersianDigits.formatInt(state.rangeSummary.sentences.size)} جمله",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        OutlinedButton(
          onClick = onOpenRangePicker,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("open_range_picker_button"),
          shape = RoundedCornerShape(12.dp),
        ) {
          Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.size(6.dp))
          Text("انتخاب بازه")
        }
      }
    }

    when (state.viewMode) {
      BookViewMode.FULL_PAGES -> {
        LazyColumn(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag("full_pages_list"),
          contentPadding = PaddingValues(bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(state.content.pages, key = { it.pageNumber }) { page ->
            PageTextCard(page = page)
          }
        }
      }

      BookViewMode.SELECTED_RANGE -> {
        RangePreviewContent(
          summary = state.rangeSummary,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        )
      }
    }
  }
}

@Composable
private fun PageTextCard(page: ExtractedPage) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (page.isEmpty) {
        MaterialTheme.colorScheme.surfaceContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainerLow
      },
    ),
    modifier = Modifier.fillMaxWidth(),
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
          color = MaterialTheme.colorScheme.primaryContainer,
        ) {
          Text(
            text = "صفحهٔ ${PersianDigits.formatInt(page.pageNumber)}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }

        if (!page.isEmpty) {
          Text(
            text = "${PersianDigits.formatInt(page.charCount)} نویسه",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (page.isEmpty) {
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
      } else {
        Text(
          text = page.cleanedText,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    }
  }
}

@Composable
private fun RangePreviewContent(
  summary: PageRangeSummary,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.testTag("range_preview_list"),
    contentPadding = PaddingValues(bottom = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
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
          Text(
            text = "پیشنمایش متنی بازهٔ ${PersianDigits.formatInt(summary.startPage)} تا ${PersianDigits.formatInt(summary.endPage)}",
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
private fun TtsActionFooter(
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
