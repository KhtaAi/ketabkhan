package com.ketabkhan.app.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ketabkhan.app.data.pdf.BookMeta
import com.ketabkhan.app.data.pdf.BookRepository
import com.ketabkhan.app.data.pdf.ExtractionProgress
import com.ketabkhan.app.data.pdf.ImportOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PasswordPromptState(
  val bookId: String,
  val bookTitle: String,
  val errorMessage: String? = null,
)

data class LibraryUiState(
  val isImporting: Boolean = false,
  val extractionProgress: ExtractionProgress? = null,
  val errorMessage: String? = null,
  val passwordPrompt: PasswordPromptState? = null,
  val bookPendingDeletion: BookMeta? = null,
)

class LibraryViewModel(
  private val repository: BookRepository,
) : ViewModel() {

  val books: StateFlow<List<BookMeta>> = repository.books.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = emptyList(),
  )

  private val _uiState = MutableStateFlow(LibraryUiState())
  val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      repository.loadLibrary()
    }
  }

  fun importPdf(uri: Uri, onBookReady: (String) -> Unit = {}) {
    if (_uiState.value.isImporting) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isImporting = true,
        extractionProgress = ExtractionProgress(0, 0, "در حال آماده‌سازی..."),
        errorMessage = null,
        passwordPrompt = null,
      )

      val outcome = repository.importAndExtractBook(uri) { progress ->
        _uiState.value = _uiState.value.copy(extractionProgress = progress)
      }

      handleImportOutcome(outcome, onBookReady)
    }
  }

  fun submitPassword(password: String, onBookReady: (String) -> Unit = {}) {
    val prompt = _uiState.value.passwordPrompt ?: return
    if (_uiState.value.isImporting) return

    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(
        isImporting = true,
        extractionProgress = ExtractionProgress(0, 0, prompt.bookTitle),
        passwordPrompt = null,
        errorMessage = null,
      )

      val outcome = repository.retryExtractWithPassword(
        bookId = prompt.bookId,
        password = password,
      ) { progress ->
        _uiState.value = _uiState.value.copy(extractionProgress = progress)
      }

      handleImportOutcome(outcome, onBookReady)
    }
  }

  fun cancelPasswordPrompt() {
    val prompt = _uiState.value.passwordPrompt
    _uiState.value = _uiState.value.copy(passwordPrompt = null)
    if (prompt != null) {
      viewModelScope.launch {
        repository.cancelPendingImport(prompt.bookId)
      }
    }
  }

  fun requestDeleteBook(book: BookMeta) {
    _uiState.value = _uiState.value.copy(bookPendingDeletion = book)
  }

  fun dismissDeleteDialog() {
    _uiState.value = _uiState.value.copy(bookPendingDeletion = null)
  }

  fun confirmDeleteBook() {
    val target = _uiState.value.bookPendingDeletion ?: return
    _uiState.value = _uiState.value.copy(bookPendingDeletion = null)
    viewModelScope.launch {
      repository.deleteBook(target.id)
    }
  }

  fun dismissError() {
    _uiState.value = _uiState.value.copy(errorMessage = null)
  }

  private fun handleImportOutcome(outcome: ImportOutcome, onBookReady: (String) -> Unit) {
    when (outcome) {
      is ImportOutcome.Success -> {
        _uiState.value = _uiState.value.copy(
          isImporting = false,
          extractionProgress = null,
          errorMessage = null,
          passwordPrompt = null,
        )
        onBookReady(outcome.book.id)
      }

      is ImportOutcome.NeedsPassword -> {
        _uiState.value = _uiState.value.copy(
          isImporting = false,
          extractionProgress = null,
          passwordPrompt = PasswordPromptState(
            bookId = outcome.bookId,
            bookTitle = outcome.displayTitle,
            errorMessage = outcome.errorMessage,
          ),
        )
      }

      is ImportOutcome.Failure -> {
        _uiState.value = _uiState.value.copy(
          isImporting = false,
          extractionProgress = null,
          errorMessage = outcome.message,
        )
      }
    }
  }

  class Factory(private val repository: BookRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return LibraryViewModel(repository) as T
    }
  }
}
