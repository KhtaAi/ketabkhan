package com.ketabkhan.app.domain.tts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class UnavailableTtsEngine : TtsEngine {

  val unavailabilityReason: String =
    "موتور صوتی (TTS) و مدل فارسی در این مرحله متصل نیستند. خواندن صوتی و خروجی MP3 در مرحلهٔ بعدی فعال می‌شوند."

  private val _engineState = MutableStateFlow<TtsAvailability>(
    TtsAvailability.Unavailable(
      reason = unavailabilityReason,
      missingModel = true,
    )
  )
  override val engineState: StateFlow<TtsAvailability> = _engineState.asStateFlow()

  private val _selectedVoice = MutableStateFlow<TtsVoice?>(null)
  override val selectedVoice: StateFlow<TtsVoice?> = _selectedVoice.asStateFlow()

  override suspend fun checkAvailability(): TtsAvailability = _engineState.value

  override suspend fun isModelPresent(): Boolean = false

  override suspend fun listAvailableVoices(): List<TtsVoice> = emptyList()

  override suspend fun selectVoice(voiceId: String): Boolean = false

  override fun synthesizeSentence(sentence: String): Flow<TtsSynthesisEvent> = flow {
    emit(TtsSynthesisEvent.Failed(unavailabilityReason))
  }

  override fun cancel() {
    // No active native synthesis in Phase 1
  }

  override fun release() {
    // No native resources allocated in Phase 1
  }
}
