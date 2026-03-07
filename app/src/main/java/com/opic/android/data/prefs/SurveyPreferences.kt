package com.opic.android.data.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 서베이 Part 4 선택 항목 저장.
 * QuestionGenerator에서 참조하여 선택 유형 문제 선정 시 사용.
 */
@Singleton
class SurveyPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("opic_survey", Context.MODE_PRIVATE)

    var part1Main: Int
        get() = prefs.getInt("part1_main", -1)
        set(v) = prefs.edit().putInt("part1_main", v).apply()

    var part1Sub: Int
        get() = prefs.getInt("part1_sub", -1)
        set(v) = prefs.edit().putInt("part1_sub", v).apply()

    var part2Main: Int
        get() = prefs.getInt("part2_main", -1)
        set(v) = prefs.edit().putInt("part2_main", v).apply()

    var part2Sub: Int
        get() = prefs.getInt("part2_sub", -1)
        set(v) = prefs.edit().putInt("part2_sub", v).apply()

    var part3Selection: Int
        get() = prefs.getInt("part3_selection", -1)
        set(v) = prefs.edit().putInt("part3_selection", v).apply()

    var selectedTopics: Set<String>
        get() = prefs.getStringSet("selected_topics", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("selected_topics", v).apply()
}
