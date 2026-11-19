package com.ketabkhan.app.data.reading

import android.content.Context
import android.content.SharedPreferences
import com.ketabkhan.app.domain.reading.ReadingSettings
import com.ketabkhan.app.domain.reading.TitlePauseDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight persistent store for reading settings backed by Android [SharedPreferences],
 * requiring zero additional Gradle dependencies.
 */
class ReadingSettingsRepository(context: Context) {

  private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
    PREFS_NAME,
    Context.MODE_PRIVATE,
  )

  private val _settings = MutableStateFlow(readFromPrefs())
  val settings: StateFlow<ReadingSettings> = _settings.asStateFlow()

  fun updateSettings(newSettings: ReadingSettings) {
    prefs.edit()
      .putBoolean(KEY_READ_TOC, newSettings.readTocEntries)
      .putBoolean(KEY_READ_HEADERS_FOOTERS, newSettings.readHeadersAndFooters)
      .putBoolean(KEY_READ_FOOTNOTES, newSettings.readFootnotes)
      .putBoolean(KEY_READ_PAGE_NUMBERS, newSettings.readPageNumbers)
      .putString(KEY_TITLE_PAUSE, newSettings.titlePauseDuration.name)
      .apply()
    _settings.value = newSettings
  }

  private fun readFromPrefs(): ReadingSettings {
    val pauseName = prefs.getString(KEY_TITLE_PAUSE, TitlePauseDuration.LONG.name)
      ?: TitlePauseDuration.LONG.name
    val pauseDuration = runCatching { TitlePauseDuration.valueOf(pauseName) }
      .getOrDefault(TitlePauseDuration.LONG)

    return ReadingSettings(
      readTocEntries = prefs.getBoolean(KEY_READ_TOC, true),
      readHeadersAndFooters = prefs.getBoolean(KEY_READ_HEADERS_FOOTERS, false),
      readFootnotes = prefs.getBoolean(KEY_READ_FOOTNOTES, false),
      readPageNumbers = prefs.getBoolean(KEY_READ_PAGE_NUMBERS, false),
      titlePauseDuration = pauseDuration,
    )
  }

  private companion object {
    const val PREFS_NAME = "ketabkhan_reading_settings"
    const val KEY_READ_TOC = "read_toc_entries"
    const val KEY_READ_HEADERS_FOOTERS = "read_headers_and_footers"
    const val KEY_READ_FOOTNOTES = "read_footnotes"
    const val KEY_READ_PAGE_NUMBERS = "read_page_numbers"
    const val KEY_TITLE_PAUSE = "title_pause_duration"
  }
}
