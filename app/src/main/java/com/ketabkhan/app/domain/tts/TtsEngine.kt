package com.ketabkhan.app.domain.tts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

const val TTS_SAMPLE_RATE_HZ = 24_000
const val TTS_CHANNEL_COUNT_MONO = 1

data class TtsVoice(
  val id: String,
  val displayName: String,
  val languageTag: String = "fa-IR",
  val genderLabel: String? = null,
)

sealed interface TtsAvailability {
  data object Ready : TtsAvailability

  data class Unavailable(
    val reason: String,
    val missingModel: Boolean = false,
  ) : TtsAvailability
}

data class TtsAudioChunk(
  val pcm16Mono: ShortArray,
  val sampleRateHz: Int = TTS_SAMPLE_RATE_HZ,
  val channelCount: Int = TTS_CHANNEL_COUNT_MONO,
  val isFinal: Boolean = false,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TtsAudioChunk) return false
    return sampleRateHz == other.sampleRateHz &&
      channelCount == other.channelCount &&
      isFinal == other.isFinal &&
      pcm16Mono.contentEquals(other.pcm16Mono)
  }

  override fun hashCode(): Int {
    var result = pcm16Mono.contentHashCode()
    result = 31 * result + sampleRateHz
    result = 31 * result + channelCount
    result = 31 * result + isFinal.hashCode()
    return result
  }
}

sealed interface TtsSynthesisEvent {
  data class Progress(
    val fraction: Float,
    val message: String? = null,
  ) : TtsSynthesisEvent

  data class AudioChunk(val chunk: TtsAudioChunk) : TtsSynthesisEvent

  data object Completed : TtsSynthesisEvent

  data class Failed(val message: String) : TtsSynthesisEvent

  data object Cancelled : TtsSynthesisEvent
}

/**
 * قرارداد موتور تبدیل متن به گفتار (TTS) برای اتصال رانتایم نیتیو در مرحلهٔ دوم.
 * خروجی جریانی PCM 16-bit مونو با نرخ نمونهٔ ۲۴۰۰۰ هرتز است.
 */
interface TtsEngine {
  val engineState: StateFlow<TtsAvailability>
  val selectedVoice: StateFlow<TtsVoice?>

  suspend fun checkAvailability(): TtsAvailability
  suspend fun isModelPresent(): Boolean
  suspend fun listAvailableVoices(): List<TtsVoice>
  suspend fun selectVoice(voiceId: String): Boolean
  fun synthesizeSentence(sentence: String): Flow<TtsSynthesisEvent>
  fun cancel()
  fun release()
}
