package com.ketabkhan.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ketabkhan.app.data.pdf.BookMeta
import com.ketabkhan.app.data.pdf.PdfQualityStatus
import com.ketabkhan.app.domain.text.PersianDigits
import com.ketabkhan.app.ui.components.DeleteBookConfirmSheet
import com.ketabkhan.app.ui.components.EmptyLibraryState
import com.ketabkhan.app.ui.components.ErrorBanner
import com.ketabkhan.app.ui.components.ExtractionProgressCard
import com.ketabkhan.app.ui.components.PdfPasswordSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
  viewModel: LibraryViewModel,
  onOpenBook: (String) -> Unit,
) {
  val books by viewModel.books.collectAsStateWithLifecycle()
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  val pdfPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) {
      viewModel.importPdf(uri = uri, onBookReady = onOpenBook)
    }
  }

  val launchPdfPicker = {
    pdfPickerLauncher.launch(arrayOf("application/pdf"))
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      LargeTopAppBar(
        title = {
          Column {
            Text(
              text = "کتابخانهٔ کتابخوان",
              style = MaterialTheme.typography.headlineMedium,
            )
            if (books.isNotEmpty()) {
              Text(
                text = "${PersianDigits.formatInt(books.size)} کتاب آمادهٔ مطالعه",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
      )
    },
    floatingActionButton = {
      if (books.isNotEmpty()) {
        ExtendedFloatingActionButton(
          onClick = launchPdfPicker,
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          text = {
            Text(
              text = "افزودن کتاب",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
          },
          containerColor = MaterialTheme.colorScheme.primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("add_book_fab"),
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 640.dp)
          .padding(horizontal = 16.dp),
      ) {
        AnimatedVisibility(visible = uiState.extractionProgress != null) {
          uiState.extractionProgress?.let { progress ->
            ExtractionProgressCard(
              progress = progress,
              modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
          }
        }

        AnimatedVisibility(visible = uiState.errorMessage != null) {
          uiState.errorMessage?.let { error ->
            ErrorBanner(
              message = error,
              onDismiss = { viewModel.dismissError() },
              modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
          }
        }

        if (books.isEmpty() && !uiState.isImporting) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            EmptyLibraryState(onAddBookClick = launchPdfPicker)
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .testTag("library_books_list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(books, key = { it.id }) { book ->
              BookLibraryCard(
                book = book,
                onClick = { onOpenBook(book.id) },
                onDeleteClick = { viewModel.requestDeleteBook(book) },
              )
            }
          }
        }
      }
    }
  }

  uiState.passwordPrompt?.let { prompt ->
    PdfPasswordSheet(
      bookTitle = prompt.bookTitle,
      errorMessage = prompt.errorMessage,
      onSubmitPassword = { password ->
        viewModel.submitPassword(password = password, onBookReady = onOpenBook)
      },
      onDismiss = { viewModel.cancelPasswordPrompt() },
    )
  }

  uiState.bookPendingDeletion?.let { target ->
    DeleteBookConfirmSheet(
      bookTitle = target.title,
      onConfirmDelete = { viewModel.confirmDeleteBook() },
      onDismiss = { viewModel.dismissDeleteDialog() },
    )
  }
}

@Composable
private fun BookLibraryCard(
  book: BookMeta,
  onClick: () -> Unit,
  onDeleteClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("book_card_${book.id}"),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(48.dp),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(12.dp),
          )
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = book.fileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        IconButton(
          onClick = onDeleteClick,
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("delete_book_${book.id}"),
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = "حذف کتاب ${book.title}",
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MetadataChip(label = "${PersianDigits.formatInt(book.pageCount)} صفحه")
        MetadataChip(label = PersianDigits.formatFileSize(book.fileSizeBytes))
        MetadataChip(label = PersianDigits.formatPersianDate(book.addedAtMillis))
      }

      if (book.isPasswordProtected || book.qualityReport.status != PdfQualityStatus.OK) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (book.isPasswordProtected) {
            StatusBadge(
              icon = Icons.Default.Lock,
              text = "رمزدار",
              isWarning = false,
            )
          }
          if (book.qualityReport.status == PdfQualityStatus.LIKELY_SCANNED_IMAGE) {
            StatusBadge(
              icon = Icons.Default.WarningAmber,
              text = "احتمالاً تصویری (نیازمند OCR)",
              isWarning = true,
            )
          } else if (book.qualityReport.status == PdfQualityStatus.CORRUPTED_FONT_MAPPING) {
            StatusBadge(
              icon = Icons.Default.WarningAmber,
              text = "نگاشت فونت خراب",
              isWarning = true,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MetadataChip(label: String) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun StatusBadge(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  isWarning: Boolean,
) {
  val container = if (isWarning) {
    MaterialTheme.colorScheme.errorContainer
  } else {
    MaterialTheme.colorScheme.secondaryContainer
  }
  val content = if (isWarning) {
    MaterialTheme.colorScheme.onErrorContainer
  } else {
    MaterialTheme.colorScheme.onSecondaryContainer
  }
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = container,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = content,
        modifier = Modifier.size(14.dp),
      )
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
      )
    }
  }
}
