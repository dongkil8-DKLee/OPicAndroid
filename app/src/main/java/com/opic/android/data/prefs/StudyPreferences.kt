package com.opic.android.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Python Config.ini [StudyScreenState] 1:1 대응.
 * SharedPreferences로 StudyScreen 필터/설정 상태 유지.
 */
@Singleton
class StudyPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("opic_study_state", Context.MODE_PRIVATE)

    // === 필터 상태 ===

    var set: String
        get() = prefs.getString("set", "전체") ?: "전체"
        set(v) = prefs.edit().putString("set", v).apply()

    var type: String
        get() = prefs.getString("type", "전체") ?: "전체"
        set(v) = prefs.edit().putString("type", v).apply()

    var title: String
        get() = prefs.getString("title", "") ?: ""
        set(v) = prefs.edit().putString("title", v).apply()

    var sort: String
        get() = prefs.getString("sort", "주제 순서") ?: "주제 순서"
        set(v) = prefs.edit().putString("sort", v).apply()

    var studyFilter: String
        get() = prefs.getString("study_filter", "전체") ?: "전체"
        set(v) = prefs.edit().putString("study_filter", v).apply()

    var slotFilter: String
        get() = prefs.getString("slot_filter", "Select") ?: "Select"
        set(v) = prefs.edit().putString("slot_filter", v).apply()

    // === 재생 설정 ===

    var repeat: Int
        get() = prefs.getInt("repeat", 1)
        set(v) = prefs.edit().putInt("repeat", v).apply()

    var groupPlayMode: String
        get() = prefs.getString("group_play_mode", "목록 재생") ?: "목록 재생"
        set(v) = prefs.edit().putString("group_play_mode", v).apply()

    // === 표시 설정 ===

    var fontSize: Int
        get() = prefs.getInt("font_size", 18)
        set(v) = prefs.edit().putInt("font_size", v).apply()

    // === 속도/집중모드 설정 ===

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1.0f)
        set(v) = prefs.edit().putFloat("playback_speed", v).apply()

    var focusMode: Boolean
        get() = prefs.getBoolean("focus_mode", true)
        set(v) = prefs.edit().putBoolean("focus_mode", v).apply()
}
