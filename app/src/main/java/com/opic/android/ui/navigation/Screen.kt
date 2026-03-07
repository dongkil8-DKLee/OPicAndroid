package com.opic.android.ui.navigation

/**
 * 라우트 정의 — Python show_frame() 이름과 1:1 대응.
 */
sealed class Screen(val route: String) {
    data object Start : Screen("StartPage")
    data object Survey : Screen("SurveyPage")
    data object SelfAssessment : Screen("SelfAssessmentPage")
    data object BeginTest : Screen("BeginTestPage/{difficulty}") {
        fun createRoute(difficulty: Int) = "BeginTestPage/$difficulty"
    }
    data object Test : Screen("TestScreen/{difficulty}") {
        fun createRoute(difficulty: Int) = "TestScreen/$difficulty"
    }
    data object ReviewList : Screen("ReviewListScreen")
    data object Review : Screen("ReviewScreen/{sessionId}") {
        fun createRoute(sessionId: Int) = "ReviewScreen/$sessionId"
    }
    data object Study : Screen("StudyScreen?type={type}&grade={grade}&set={set}") {
        fun createRoute(type: String? = null, grade: String? = null, set: String? = null): String {
            val params = mutableListOf<String>()
            if (type != null) params.add("type=$type")
            if (grade != null) params.add("grade=$grade")
            if (set != null) params.add("set=$set")
            return if (params.isEmpty()) "StudyScreen" else "StudyScreen?${params.joinToString("&")}"
        }
    }
    data object Practice : Screen("PracticeScreen/{questionId}") {
        fun createRoute(questionId: Int) = "PracticeScreen/$questionId"
    }
    data object PracticeOverlay : Screen("PracticeOverlay/{questionId}") {
        fun createRoute(questionId: Int) = "PracticeOverlay/$questionId"
    }
    data object StudyOverlay : Screen("StudyOverlay?set={set}&type={type}&title={title}") {
        fun createRoute(set: String? = null, type: String? = null, title: String? = null): String {
            val params = mutableListOf<String>()
            if (!set.isNullOrBlank() && set != "전체") params.add("set=${set.encode()}")
            if (!type.isNullOrBlank() && type != "전체") params.add("type=${type.encode()}")
            if (!title.isNullOrBlank()) params.add("title=${title.encode()}")
            return if (params.isEmpty()) "StudyOverlay" else "StudyOverlay?${params.joinToString("&")}"
        }
        private fun String.encode() = java.net.URLEncoder.encode(this, "UTF-8")
    }
    data object SurveyOverlay : Screen("SurveyOverlay")
    data object Settings : Screen("SettingsScreen")
    data object Report : Screen("ReportScreen")
    data object Vocabulary : Screen("VocabularyScreen")
}
