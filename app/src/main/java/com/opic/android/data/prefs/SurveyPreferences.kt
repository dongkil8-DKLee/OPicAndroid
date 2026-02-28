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

    var selectedTopics: Set<String>
        get() = prefs.getStringSet("selected_topics", emptySet()) ?: emptySet()
        set(v) = prefs.edit().putStringSet("selected_topics", v).apply()
}
