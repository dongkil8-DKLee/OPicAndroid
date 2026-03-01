package com.opic.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.opic.android.ui.assessment.SelfAssessmentScreen
import com.opic.android.ui.settings.SettingsScreen
import com.opic.android.ui.start.StartScreen
import com.opic.android.ui.survey.SurveyScreen
import com.opic.android.ui.review.ReviewListScreen
import com.opic.android.ui.review.ReviewScreen
import com.opic.android.ui.practice.PracticeScreen
import com.opic.android.ui.report.ReportScreen
import com.opic.android.ui.study.StudyScreen
import com.opic.android.ui.test.TestScreen
import com.opic.android.ui.vocabulary.VocabularyScreen

@Composable
fun OPicNavGraph(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Start.route) {

        composable(Screen.Start.route) {
            StartScreen(
                onStudy = { navController.navigate(Screen.Study.route) },
                onNext = { navController.navigate(Screen.Survey.route) },
                onReview = { navController.navigate(Screen.ReviewList.route) },
                onReport = { navController.navigate(Screen.Report.route) },
                onVocabulary = { navController.navigate(Screen.Vocabulary.route) },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Survey.route) {
            SurveyScreen(
                onBack = { navController.popBackStack() },
                onNext = { navController.navigate(Screen.SelfAssessment.route) }
            )
        }

        composable(Screen.SelfAssessment.route) {
            SelfAssessmentScreen(
                onBack = { navController.popBackStack() },
                onNext = { difficulty ->
                    navController.navigate(Screen.Test.createRoute(difficulty)) {
                        popUpTo(Screen.Start.route)
                    }
                }
            )
        }

        composable(
            route = Screen.Test.route,
            arguments = listOf(navArgument("difficulty") { type = NavType.IntType })
        ) {
            TestScreen(
                onHome = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                },
                onFinish = { sessionId ->
                    navController.navigate(Screen.Review.createRoute(sessionId)) {
                        popUpTo(Screen.Start.route)
                    }
                }
            )
        }

        // 세션 목록
        composable(Screen.ReviewList.route) {
            ReviewListScreen(
                onHome = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                },
                onSessionClick = { sessionId ->
                    navController.navigate(Screen.Review.createRoute(sessionId))
                }
            )
        }

        // 특정 세션 리뷰
        composable(
            route = Screen.Review.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.IntType })
        ) {
            ReviewScreen(
                onHome = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                }
            )
        }

        composable(Screen.Study.route) {
            StudyScreen(
                onHome = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                },
                onPractice = { questionId ->
                    navController.navigate(Screen.Practice.createRoute(questionId))
                },
                onSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Practice.route,
            arguments = listOf(navArgument("questionId") { type = NavType.IntType })
        ) {
            PracticeScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Report.route) {
            ReportScreen(
                onHome = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                }
            )
        }

        composable(Screen.Vocabulary.route) {
            VocabularyScreen(
                onBack = {
                    navController.popBackStack(Screen.Start.route, inclusive = false)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onHome = {
                    navController.popBackStack()
                }
            )
        }
    }
}
