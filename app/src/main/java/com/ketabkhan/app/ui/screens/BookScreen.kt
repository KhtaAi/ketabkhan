package com.ketabkhan.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ketabkhan.app.data.pdf.PdfQualityStatus
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.ui.components.ErrorBanner
import com.ketabkhan.app.ui.components.ExtractionProgressCard
import com.ketabkhan.app.ui.components.OutdatedCacheBanner
import com.ketabkhan.app.ui.components.PdfPasswordSheet
import com.ketabkhan.app.ui.components.WarningBanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
  viewModel: BookViewModel,
  onNavigateBack: () -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  var showRangePicker by rememberSaveable { mutableStateOf(false) }

  val loadedState = uiState as? BookUiState.Loaded
  val topTitle = loadedState?.content?.meta?.title ?: "کتاب"

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
        actions = {
          if (loadedState != null) {
            IconButton(
              onClick = { viewModel.reextractBook() },
              enabled = !loadedState.isReextracting,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("reextract_book_button"),
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "استخراج دوباره",
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    bottomBar = {
      if (loadedState != null) {
        TtsActionFooter(ttsAvailability = loadedState.ttsAvailability)
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
            onTextSourceChange = { viewModel.setTextSource(it) },
            onOpenRangePicker = { showRangePicker = true },
            onReextractClick = { viewModel.reextractBook() },
            onDismissError = { viewModel.dismissReextractionError() },
          )

          if (showRangePicker) {
            RangePickerSheet(
              pages = state.content.pages,
              initialStartPage = state.rangeStart,
              initialEndPage = state.rangeEnd,
              textSource = state.textSource,
              onComputePreview = { pages, s, e -> viewModel.previewRange(pages, s, e, state.textSource) },
              onConfirmRange = { start, end ->
                viewModel.applyPageRange(start, end)
                showRangePicker = false
              },
              onDismiss = { showRangePicker = false },
            )
          }

          state.passwordPrompt?.let { prompt ->
            PdfPasswordSheet(
              bookTitle = prompt.bookTitle,
              errorMessage = prompt.errorMessage,
              onSubmitPassword = { password -> viewModel.submitPassword(password) },
              onDismiss = { viewModel.cancelPasswordPrompt() },
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
  onTextSourceChange: (BookTextSource) -> Unit,
  onOpenRangePicker: () -> Unit,
  onReextractClick: () -> Unit,
  onDismissError: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .widthIn(max = 680.dp)
      .padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
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

    SingleChoiceSegmentedButtonRow(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("text_source_segmented"),
    ) {
      SegmentedButton(
        selected = state.textSource == BookTextSource.CLEANED,
        onClick = { onTextSourceChange(BookTextSource.CLEANED) },
        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("cleaned_text_tab"),
      ) {
        Text("متن پاکسازیشده")
      }
      SegmentedButton(
        selected = state.textSource == BookTextSource.RAW,
        onClick = { onTextSourceChange(BookTextSource.RAW) },
        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("raw_text_tab"),
      ) {
        Text("متن خام")
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
          if (state.isReextracting && state.reextractionProgress != null) {
            item(key = "reextract_progress") {
              ExtractionProgressCard(progress = state.reextractionProgress)
            }
          }
          if (state.reextractionError != null) {
            item(key = "reextract_error") {
              ErrorBanner(
                message = state.reextractionError,
                onDismiss = onDismissError,
              )
            }
          }
          if (state.content.isCacheOutdated) {
            item(key = "outdated_cache_banner") {
              OutdatedCacheBanner(
                onReextractClick = onReextractClick,
                isReextracting = state.isReextracting,
              )
            }
          }
          val quality = state.content.meta.qualityReport
          if (quality.status != PdfQualityStatus.OK && quality.warningMessage != null) {
            item(key = "quality_warning_banner") {
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
          }
          items(state.content.pages, key = { it.pageNumber }) { page ->
            PageTextCard(
              page = page,
              textSource = state.textSource,
            )
          }
        }
      }

      BookViewMode.SELECTED_RANGE -> {
        RangePreviewContent(
          state = state,
          onReextractClick = onReextractClick,
          onDismissError = onDismissError,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        )
      }
    }
  }
}
