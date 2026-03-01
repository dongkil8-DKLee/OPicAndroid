package com.opic.android.audio

import android.content.Context
import android.util.Log
import com.opic.android.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 오디오 소스 타입 (외부 파일 vs assets 내 파일 구분)
 */
sealed class AudioSource {
    data class FilePath(val path: String) : AudioSource()   // 외부 파일 시스템
    data class AssetPath(val path: String) : AudioSource()  // assets/Sound/
}

/**
 * DB link_name(확장자 없음)으로 오디오 파일을 탐색.
 *
 * 탐색 순서:
 *   1순위: AppPreferences.soundDir 외부 폴더 (.mp3 → .wav)
 *   2순위: assets/Sound/ (.mp3 → .wav)
 *   없으면: null (호출자가 TTS 폴백 처리)
 */
@Singleton
class AudioFileResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPrefs: AppPreferences
) {
    companion object {
        private const val TAG = "AudioFileResolver"
        private const val SOUND_DIR = "Sound"
    }

    // assets/Sound/ 파일 목록 캐시 (앱 실행 중 불변)
    private val soundFiles: Set<String> by lazy {
        try {
            context.assets.list(SOUND_DIR)?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Sound 디렉토리 목록 실패", e)
            emptySet()
        }
    }

    /**
     * @param linkName DB의 question_audio 또는 answer_audio 값 (확장자 없음)
     * @return AudioSource (FilePath 또는 AssetPath), 없으면 null
     */
    fun resolve(linkName: String?): AudioSource? {
        if (linkName.isNullOrBlank()) return null

        // 1순위: 외부 soundDir 폴더
        val externalDir = appPrefs.soundDir
        if (externalDir.isNotBlank()) {
            for (ext in listOf("mp3", "wav")) {
                val file = File(externalDir, "$linkName.$ext")
                if (file.exists()) {
                    Log.d(TAG, "외부 오디오 파일 사용: ${file.absolutePath}")
                    return AudioSource.FilePath(file.absolutePath)
                }
            }
        }

        // 2순위: assets/Sound/
        val mp3 = "$linkName.mp3"
        if (mp3 in soundFiles) return AudioSource.AssetPath("$SOUND_DIR/$mp3")

        val wav = "$linkName.wav"
        if (wav in soundFiles) return AudioSource.AssetPath("$SOUND_DIR/$wav")

        Log.w(TAG, "오디오 파일 없음: $linkName")
        return null
    }
}
