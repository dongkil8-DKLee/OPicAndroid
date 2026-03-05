package com.opic.android.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.opic.android.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** 음성 선택 UI용 데이터: raw name(저장용) + displayName(표시용) */
data class VoiceOption(val name: String, val displayName: String)

/** TTS 엔진 정보 */
data class EngineOption(val packageName: String, val label: String)

/**
 * Python pyttsx3 대응.
 * Android TextToSpeech 엔진으로 텍스트 → WAV 파일 생성.
 * SHA1 해시 기반 캐시 (Python tts_cache_path와 동일).
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPrefs: AppPreferences
) {
    companion object {
        private const val TAG = "TtsManager"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var currentEnginePackage: String? = null

    private val cacheDir: File by lazy {
        File(context.cacheDir, "tts").also { it.mkdirs() }
    }

    /**
     * TTS 초기화. enginePackage가 null이면 시스템 기본 엔진 사용.
     * 이미 같은 엔진으로 초기화되어 있으면 재초기화하지 않음.
     */
    fun init(enginePackage: String? = null) {
        val target = enginePackage?.ifBlank { null }
        if (tts != null && currentEnginePackage == target) return

        // 기존 엔진 해제 후 재초기화
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        currentEnginePackage = target

        val callback: (Int) -> Unit = { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                val savedVoice = appPrefs.selectedVoice
                if (savedVoice.isNotBlank()) setVoice(savedVoice)
                isInitialized = true
                Log.d(TAG, "TTS 초기화 완료 (엔진: ${target ?: "기본"})")
            } else {
                Log.e(TAG, "TTS 초기화 실패: status=$status (엔진: ${target ?: "기본"})")
            }
        }

        tts = if (target != null) {
            TextToSpeech(context, callback, target)
        } else {
            TextToSpeech(context, callback)
        }
    }

    /** 기기에 설치된 TTS 엔진 목록 */
    fun getInstalledEngines(): List<EngineOption> {
        val engine = tts ?: return emptyList()
        return engine.engines
            .sortedBy { it.label }
            .map { EngineOption(packageName = it.name, label = it.label) }
    }

    /** TTS 음성 변경 */
    fun setVoice(voiceName: String) {
        val engine = tts ?: return
        val voices = engine.voices ?: return
        val voice = voices.find { it.name == voiceName }
        if (voice != null) {
            engine.voice = voice
            Log.d(TAG, "TTS 음성 설정: $voiceName")
        } else {
            Log.w(TAG, "TTS 음성 찾기 실패: $voiceName")
        }
    }

    /** 사용 가능한 영어 음성 목록 — raw name 리스트 (하위 호환용) */
    fun getAvailableEnglishVoices(): List<String> =
        getAvailableEnglishVoiceOptions().map { it.name }

    /**
     * 사용 가능한 영어 음성 목록 (온라인 포함).
     * 오프라인 우선 → 지역(US/GB/…) → 이름 순 정렬.
     * displayName은 기기 locale 정보를 활용해 사람이 읽기 쉽게 생성.
     */
    fun getAvailableEnglishVoiceOptions(): List<VoiceOption> {
        val engine = tts ?: return emptyList()
        val voices = engine.voices ?: return emptyList()
        return voices
            .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.country }, { it.name }))
            .map { v ->
                val country = when (v.locale.country.uppercase()) {
                    "US" -> "미국"
                    "GB" -> "영국"
                    "AU" -> "호주"
                    "IN" -> "인도"
                    "CA" -> "캐나다"
                    else -> v.locale.country.ifBlank { v.locale.displayLanguage }
                }
                // Google TTS 패턴: en-us-x-sfg-local
                val googleMatch = Regex("""^en-[a-z]{2}-x-([a-z0-9]+)-local$""")
                    .find(v.name.lowercase())
                val label = googleMatch?.groupValues?.get(1)?.uppercase()
                    ?: v.name
                        .replace(Regex("""^en[-_][a-zA-Z]{2}[-_](x[-_])?""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""[-_](local|network)$""", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("""[-_]"""), " ")
                        .trim()
                        .ifBlank { v.name }

                VoiceOption(name = v.name, displayName = "$label · $country")
            }
    }

    /**
     * 텍스트 → WAV 파일 생성 (캐시 우선).
     * @return 생성된 WAV 파일 경로, 실패 시 null
     */
    suspend fun generateToFile(text: String): String? {
        if (text.isBlank()) return null

        val cachedFile = getCachePath(text)
        if (cachedFile.exists() && cachedFile.length() > 44) {
            Log.d(TAG, "TTS 캐시 사용: ${cachedFile.name}")
            return cachedFile.absolutePath
        }

        if (!isInitialized) {
            Log.w(TAG, "TTS 미초기화")
            return null
        }

        return suspendCoroutine { cont ->
            val utteranceId = "tts_${System.currentTimeMillis()}"

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        Log.d(TAG, "TTS 생성 완료: ${cachedFile.name}")
                        cont.resume(cachedFile.absolutePath)
                    }
                }
                override fun onError(id: String?) {
                    if (id == utteranceId) {
                        Log.e(TAG, "TTS 생성 실패")
                        cont.resume(null)
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            tts?.synthesizeToFile(text, params, cachedFile, utteranceId)
        }
    }

    /** Python tts_cache_path() 대응: SHA1 해시 기반 캐시 파일 경로 */
    private fun getCachePath(text: String): File {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "tts_$hash.wav")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        currentEnginePackage = null
    }
}
