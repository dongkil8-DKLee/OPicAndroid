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
    data object Study : Screen("StudyScreen")
    data object Practice : Screen("PracticeScreen/{questionId}") {
        fun createRoute(questionId: Int) = "PracticeScreen/$questionId"
    }
    data object Settings : Screen("SettingsScreen")
    data object Report : Screen("ReportScreen")
}
