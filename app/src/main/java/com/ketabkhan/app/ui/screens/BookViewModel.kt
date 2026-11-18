package com.ketabkhan.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ketabkhan.app.data.pdf.BookContent
import com.ketabkhan.app.data.pdf.BookRepository
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.domain.text.SentenceSplitter
import com.ketabkhan.app.domain.tts.TtsAvailability
import com.ketabkhan.app.domain.tts.TtsEngine
import com.ketabkhan.app.domain.tts.UnavailableTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BookViewMode {
  FULL_PAGES,
  SELECTED_RANGE,
}

data class PageRangeSummary(
  val startPage: Int,
  val endPage: Int,
  val selectedPageCount: Int,
  val emptyPageCount: Int,
  val nonEmptyPageCount: Int,
  val sentences: List<String>,
  val previewText: String,
)

sealed interface BookUiState {
  data object Loading : BookUiState
  data class Error(val message: String) : BookUiState
  data class Loaded(
    val content: BookContent,
    val viewMode: BookViewMode,
    val rangeStart: Int,
    val rangeEnd: Int,
    val rangeSummary: PageRangeSummary,
    val ttsAvailability: TtsAvailability,
  ) : BookUiState
}

class BookViewModel(
  private val bookId: String,
  private val repository: BookRepository,
  private val ttsEngine: TtsEngine = UnavailableTtsEngine(),
) : ViewModel() {

  private val _uiState = MutableStateFlow<BookUiState>(BookUiState.Loading)
  val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

  init {
    loadBook()
  }

  private fun loadBook() {
    viewModelScope.launch {
      _uiState.value = BookUiState.Loading
      val content = repository.getBookContent(bookId)
      if (content == null) {
        _uiState.value = BookUiState.Error("اطلاعات کتاب یا کش متن آن یافت نشد.")
        return@launch
      }

      val total = content.pages.size.coerceAtLeast(1)
      val initialEnd = minOf(total, 5)
      val summary = computeRangeSummary(content.pages, 1, initialEnd)
      val ttsAvailability = ttsEngine.checkAvailability()

      _uiState.value = BookUiState.Loaded(
        content = content,
        viewMode = BookViewMode.FULL_PAGES,
        rangeStart = 1,
        rangeEnd = initialEnd,
        rangeSummary = summary,
        ttsAvailability = ttsAvailability,
      )
    }
  }

  fun setViewMode(mode: BookViewMode) {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    _uiState.value = current.copy(viewMode = mode)
  }

  fun applyPageRange(startPage: Int, endPage: Int) {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    val total = current.content.pages.size.coerceAtLeast(1)
    val clampedStart = startPage.coerceIn(1, total)
    val clampedEnd = endPage.coerceIn(clampedStart, total)

    viewModelScope.launch {
      val summary = computeRangeSummary(current.content.pages, clampedStart, clampedEnd)
      _uiState.value = current.copy(
        viewMode = BookViewMode.SELECTED_RANGE,
        rangeStart = clampedStart,
        rangeEnd = clampedEnd,
        rangeSummary = summary,
      )
    }
  }

  suspend fun previewRange(pages: List<ExtractedPage>, startPage: Int, endPage: Int): PageRangeSummary {
    return computeRangeSummary(pages, startPage, endPage)
  }

  private suspend fun computeRangeSummary(
    pages: List<ExtractedPage>,
    startPage: Int,
    endPage: Int,
  ): PageRangeSummary = withContext(Dispatchers.Default) {
    val slice = pages.filter { it.pageNumber in startPage..endPage }
    val emptyCount = slice.count { it.isEmpty }
    val nonEmptyPages = slice.filter { !it.isEmpty }
    val combinedText = nonEmptyPages.joinToString(separator = "\n\n") { it.cleanedText }
    val sentences = SentenceSplitter.split(combinedText)
    val previewLimit = 360
    val preview = if (combinedText.length <= previewLimit) {
      combinedText
    } else {
      combinedText.take(previewLimit).trimEnd() + "…"
    }

    PageRangeSummary(
      startPage = startPage,
      endPage = endPage,
      selectedPageCount = slice.size,
      emptyPageCount = emptyCount,
      nonEmptyPageCount = nonEmptyPages.size,
      sentences = sentences,
      previewText = preview,
    )
  }

  override fun onCleared() {
    ttsEngine.release()
    super.onCleared()
  }

  class Factory(
    private val bookId: String,
    private val repository: BookRepository,
    private val ttsEngine: TtsEngine = UnavailableTtsEngine(),
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return BookViewModel(bookId, repository, ttsEngine) as T
    }
  }
}
