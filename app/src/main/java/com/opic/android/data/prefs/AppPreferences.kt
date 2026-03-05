package com.opic.android.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 글로벌 앱 설정. opic_app_settings SharedPreferences 사용.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("opic_app_settings", Context.MODE_PRIVATE)

    private val _themeModeFlow = MutableStateFlow(
        prefs.getString("theme_mode", "light") ?: "light"
    )
    val themeModeFlow: StateFlow<String> = _themeModeFlow.asStateFlow()

    var textSize: Int
        get() = prefs.getInt("text_size", 18)
        set(v) = prefs.edit().putInt("text_size", v).apply()

    var levelImageDir: String
        get() = prefs.getString("level_image_dir", "") ?: ""
        set(v) = prefs.edit().putString("level_image_dir", v).apply()

    var soundDir: String
        get() = prefs.getString("sound_dir", "") ?: ""
        set(v) = prefs.edit().putString("sound_dir", v).apply()

    var targetGrade: String
        get() = prefs.getString("target_grade", "IM2") ?: "IM2"
        set(v) = prefs.edit().putString("target_grade", v).apply()

    var selectedVoice: String
        get() = prefs.getString("selected_voice", "") ?: ""
        set(v) = prefs.edit().putString("selected_voice", v).apply()

    var ttsEnginePackage: String
        get() = prefs.getString("tts_engine_package", "") ?: ""
        set(v) = prefs.edit().putString("tts_engine_package", v).apply()

    var themeMode: String
        get() = prefs.getString("theme_mode", "light") ?: "light"
        set(v) {
            prefs.edit().putString("theme_mode", v).apply()
            _themeModeFlow.value = v
        }

    // ─── Claude AI 설정 ───

    var claudeApiKey: String
        get() = prefs.getString("claude_api_key", "") ?: ""
        set(v) = prefs.edit().putString("claude_api_key", v).apply()

    var userProfile: UserProfile
        get() = UserProfile(
            job        = prefs.getString("profile_job", "") ?: "",
            hobbies    = prefs.getString("profile_hobbies", "") ?: "",
            family     = prefs.getString("profile_family", "") ?: "",
            country    = prefs.getString("profile_country", "") ?: "",
            background = prefs.getString("profile_background", "") ?: ""
        )
        set(v) {
            prefs.edit()
                .putString("profile_job", v.job)
                .putString("profile_hobbies", v.hobbies)
                .putString("profile_family", v.family)
                .putString("profile_country", v.country)
                .putString("profile_background", v.background)
                .apply()
        }

    // ─── Practice 화면 문제 목록 공유 (in-memory, Study 필터 변경 시 갱신) ───
    /** 현재 Study 필터 기준 정렬된 (questionId, title) 목록 */
    var practiceQuestionList: List<Pair<Int, String>> = emptyList()
    /** 현재 적용된 필터 요약 문자열 (Practice 상단에 표시) */
    var practiceFilterSummary: String = ""
}
