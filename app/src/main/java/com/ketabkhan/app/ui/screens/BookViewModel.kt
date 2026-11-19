package com.ketabkhan.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ketabkhan.app.data.pdf.BookContent
import com.ketabkhan.app.data.pdf.BookRepository
import com.ketabkhan.app.data.pdf.ExtractedPage
import com.ketabkhan.app.data.pdf.ExtractionProgress
import com.ketabkhan.app.data.pdf.ImportOutcome
import com.ketabkhan.app.data.reading.ReadingSettingsRepository
import com.ketabkhan.app.domain.reading.ReadingPlan
import com.ketabkhan.app.domain.reading.ReadingPlanBuilder
import com.ketabkhan.app.domain.reading.ReadingSettings
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

enum class BookTextSource {
  CLEANED,
  RAW,
}

enum class BookDisplayFormat {
  STRUCTURED,
  PLAIN,
}

data class PageRangeSummary(
  val startPage: Int,
  val endPage: Int,
  val selectedPageCount: Int,
  val emptyPageCount: Int,
  val nonEmptyPageCount: Int,
  val sentences: List<String>,
  val previewText: String,
  val readingPlan: ReadingPlan = ReadingPlan(emptyList(), emptyList(), startPage, endPage),
)

sealed interface BookUiState {
  data object Loading : BookUiState
  data class Error(val message: String) : BookUiState
  data class Loaded(
    val content: BookContent,
    val viewMode: BookViewMode,
    val textSource: BookTextSource = BookTextSource.CLEANED,
    val displayFormat: BookDisplayFormat = BookDisplayFormat.STRUCTURED,
    val readingSettings: ReadingSettings = ReadingSettings(),
    val rangeStart: Int,
    val rangeEnd: Int,
    val rangeSummary: PageRangeSummary,
    val ttsAvailability: TtsAvailability,
    val isReextracting: Boolean = false,
    val reextractionProgress: ExtractionProgress? = null,
    val reextractionError: String? = null,
    val passwordPrompt: PasswordPromptState? = null,
  ) : BookUiState
}

class BookViewModel(
  private val bookId: String,
  private val repository: BookRepository,
  private val ttsEngine: TtsEngine = UnavailableTtsEngine(),
  private val readingSettingsRepository: ReadingSettingsRepository? = null,
) : ViewModel() {

  private val _uiState = MutableStateFlow<BookUiState>(BookUiState.Loading)
  val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

  private var inMemorySettings: ReadingSettings = readingSettingsRepository?.settings?.value ?: ReadingSettings()

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

      val activeSettings = readingSettingsRepository?.settings?.value ?: inMemorySettings
      val total = content.pages.size.coerceAtLeast(1)
      val initialEnd = minOf(total, 5)
      val summary = computeRangeSummary(
        pages = content.pages,
        startPage = 1,
        endPage = initialEnd,
        textSource = BookTextSource.CLEANED,
        readingSettings = activeSettings,
      )
      val ttsAvailability = ttsEngine.checkAvailability()

      _uiState.value = BookUiState.Loaded(
        content = content,
        viewMode = BookViewMode.FULL_PAGES,
        textSource = BookTextSource.CLEANED,
        displayFormat = BookDisplayFormat.STRUCTURED,
        readingSettings = activeSettings,
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

  fun setDisplayFormat(format: BookDisplayFormat) {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    if (current.displayFormat == format) return
    _uiState.value = current.copy(displayFormat = format)
  }

  fun setTextSource(source: BookTextSource) {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    if (current.textSource == source) return
    viewModelScope.launch {
      val summary = computeRangeSummary(
        pages = current.content.pages,
        startPage = current.rangeStart,
        endPage = current.rangeEnd,
        textSource = source,
        readingSettings = current.readingSettings,
      )
      val latest = _uiState.value as? BookUiState.Loaded ?: return@launch
      _uiState.value = latest.copy(
        textSource = source,
        rangeSummary = summary,
      )
    }
  }

  fun updateReadingSettings(newSettings: ReadingSettings) {
    inMemorySettings = newSettings
    readingSettingsRepository?.updateSettings(newSettings)
    val current = _uiState.value as? BookUiState.Loaded ?: return
    viewModelScope.launch {
      val summary = computeRangeSummary(
        pages = current.content.pages,
        startPage = current.rangeStart,
        endPage = current.rangeEnd,
        textSource = current.textSource,
        readingSettings = newSettings,
      )
      val latest = _uiState.value as? BookUiState.Loaded ?: return@launch
      _uiState.value = latest.copy(
        readingSettings = newSettings,
        rangeSummary = summary,
      )
    }
  }

  fun applyPageRange(startPage: Int, endPage: Int) {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    val total = current.content.pages.size.coerceAtLeast(1)
    val clampedStart = startPage.coerceIn(1, total)
    val clampedEnd = endPage.coerceIn(clampedStart, total)

    viewModelScope.launch {
      val summary = computeRangeSummary(
        pages = current.content.pages,
        startPage = clampedStart,
        endPage = clampedEnd,
        textSource = current.textSource,
        readingSettings = current.readingSettings,
      )
      val latest = _uiState.value as? BookUiState.Loaded ?: return@launch
      _uiState.value = latest.copy(
        viewMode = BookViewMode.SELECTED_RANGE,
        rangeStart = clampedStart,
        rangeEnd = clampedEnd,
        rangeSummary = summary,
      )
    }
  }

  fun reextractBook() {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    if (current.isReextracting) return

    viewModelScope.launch {
      _uiState.value = current.copy(
        isReextracting = true,
        reextractionProgress = ExtractionProgress(0, current.content.meta.pageCount, current.content.meta.title),
        reextractionError = null,
        passwordPrompt = null,
      )

      val outcome = repository.reextractBook(bookId) { progress ->
        val latest = _uiState.value as? BookUiState.Loaded ?: return@reextractBook
        _uiState.value = latest.copy(reextractionProgress = progress)
      }

      handleReextractionOutcome(outcome)
    }
  }

  fun submitPassword(password: String) {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    val prompt = current.passwordPrompt ?: return
    if (current.isReextracting) return

    viewModelScope.launch {
      _uiState.value = current.copy(
        isReextracting = true,
        reextractionProgress = ExtractionProgress(0, current.content.meta.pageCount, prompt.bookTitle),
        passwordPrompt = null,
        reextractionError = null,
      )

      val outcome = repository.retryExtractWithPassword(
        bookId = prompt.bookId,
        password = password,
      ) { progress ->
        val latest = _uiState.value as? BookUiState.Loaded ?: return@retryExtractWithPassword
        _uiState.value = latest.copy(reextractionProgress = progress)
      }

      handleReextractionOutcome(outcome)
    }
  }

  fun cancelPasswordPrompt() {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    _uiState.value = current.copy(passwordPrompt = null)
    viewModelScope.launch {
      repository.cancelPendingImport(bookId)
    }
  }

  fun dismissReextractionError() {
    val current = _uiState.value as? BookUiState.Loaded ?: return
    _uiState.value = current.copy(reextractionError = null)
  }

  private suspend fun handleReextractionOutcome(outcome: ImportOutcome) {
    val latest = _uiState.value as? BookUiState.Loaded ?: return
    when (outcome) {
      is ImportOutcome.Success -> {
        val refreshed = repository.getBookContent(bookId)
        if (refreshed != null) {
          val total = refreshed.pages.size.coerceAtLeast(1)
          val clampedStart = latest.rangeStart.coerceIn(1, total)
          val clampedEnd = latest.rangeEnd.coerceIn(clampedStart, total)
          val summary = computeRangeSummary(
            pages = refreshed.pages,
            startPage = clampedStart,
            endPage = clampedEnd,
            textSource = latest.textSource,
            readingSettings = latest.readingSettings,
          )
          _uiState.value = latest.copy(
            content = refreshed,
            rangeStart = clampedStart,
            rangeEnd = clampedEnd,
            rangeSummary = summary,
            isReextracting = false,
            reextractionProgress = null,
            reextractionError = null,
            passwordPrompt = null,
          )
        } else {
          _uiState.value = latest.copy(
            isReextracting = false,
            reextractionProgress = null,
          )
        }
      }

      is ImportOutcome.NeedsPassword -> {
        _uiState.value = latest.copy(
          isReextracting = false,
          reextractionProgress = null,
          passwordPrompt = PasswordPromptState(
            bookId = outcome.bookId,
            bookTitle = outcome.displayTitle,
            errorMessage = outcome.errorMessage,
          ),
        )
      }

      is ImportOutcome.Failure -> {
        _uiState.value = latest.copy(
          isReextracting = false,
          reextractionProgress = null,
          reextractionError = outcome.message,
        )
      }
    }
  }

  suspend fun previewRange(
    pages: List<ExtractedPage>,
    startPage: Int,
    endPage: Int,
    textSource: BookTextSource = (_uiState.value as? BookUiState.Loaded)?.textSource ?: BookTextSource.CLEANED,
    readingSettings: ReadingSettings = (_uiState.value as? BookUiState.Loaded)?.readingSettings ?: inMemorySettings,
  ): PageRangeSummary {
    return computeRangeSummary(pages, startPage, endPage, textSource, readingSettings)
  }

  private suspend fun computeRangeSummary(
    pages: List<ExtractedPage>,
    startPage: Int,
    endPage: Int,
    textSource: BookTextSource,
    readingSettings: ReadingSettings,
  ): PageRangeSummary = withContext(Dispatchers.Default) {
    val slice = pages.filter { it.pageNumber in startPage..endPage }
    val emptyCount = slice.count { it.isEmpty }
    val nonEmptyPages = slice.filter { !it.isEmpty }
    val readingPlan = ReadingPlanBuilder.build(
      pages = pages,
      startPage = startPage,
      endPage = endPage,
      settings = readingSettings,
    )

    val rawCombined = nonEmptyPages.joinToString(separator = "\n\n") { page ->
      page.rawText.ifBlank { page.cleanedText }
    }
    val cleanedCombined = if (readingPlan.items.isNotEmpty()) {
      readingPlan.items.joinToString(separator = "\n") { it.spokenText }
    } else {
      nonEmptyPages.joinToString(separator = "\n\n") { it.cleanedText }
    }

    val combinedText = when (textSource) {
      BookTextSource.CLEANED -> cleanedCombined
      BookTextSource.RAW -> rawCombined
    }

    val sentences = when (textSource) {
      BookTextSource.CLEANED -> {
        if (readingPlan.items.isNotEmpty()) {
          readingPlan.items.map { it.spokenText }
        } else {
          SentenceSplitter.split(cleanedCombined)
        }
      }
      BookTextSource.RAW -> SentenceSplitter.split(rawCombined)
    }

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
      readingPlan = readingPlan,
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
    private val readingSettingsRepository: ReadingSettingsRepository? = null,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return BookViewModel(bookId, repository, ttsEngine, readingSettingsRepository) as T
    }
  }
}
