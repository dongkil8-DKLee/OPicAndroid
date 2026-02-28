package com.opic.android.audio

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Python find_audio_file() 1:1 이식.
 *
 * DB에 저장된 link_name(확장자 없음)을 받아
 * assets/Sound/ 에서 .mp3 → .wav 순으로 검색.
 * 전체 asset 경로("Sound/xxx.mp3")를 반환.
 */
@Singleton
class AudioFileResolver @Inject constructor(
    @ApplicationContext private val context: Context
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
     * @return "Sound/xxx.mp3" 형태의 asset 경로, 없으면 null
     */
    fun resolve(linkName: String?): String? {
        if (linkName.isNullOrBlank()) return null

        val mp3 = "$linkName.mp3"
        if (mp3 in soundFiles) return "$SOUND_DIR/$mp3"

        val wav = "$linkName.wav"
        if (wav in soundFiles) return "$SOUND_DIR/$wav"

        Log.w(TAG, "오디오 파일 없음: $linkName")
        return null
    }
}
