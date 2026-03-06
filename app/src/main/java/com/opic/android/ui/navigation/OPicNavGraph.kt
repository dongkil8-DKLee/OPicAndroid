package com.opic.android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.opic.android.ui.assessment.SelfAssessmentScreen
import com.opic.android.ui.settings.SettingsScreen
import com.opic.android.ui.survey.SurveyScreen
import com.opic.android.ui.review.ReviewListScreen
import com.opic.android.ui.review.ReviewScreen
import com.opic.android.ui.practice.PracticeScreen
import com.opic.android.ui.report.ReportScreen
import com.opic.android.ui.study.StudyScreen
import com.opic.android.ui.test.TestScreen
import com.opic.android.ui.vocabulary.VocabularyScreen

@Composable
fun OPicNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        NavHost(navController, startDestination = Screen.Report.route) {

            composable(Screen.Survey.route) {
                SurveyScreen(
                    onBack = { navController.popBackStack() },
                    onHome = {
                        navController.popBackStack(Screen.Report.route, inclusive = false)
                    },
                    onNext = { navController.navigate(Screen.SelfAssessment.route) }
                )
            }

            composable(Screen.SelfAssessment.route) {
                SelfAssessmentScreen(
                    onBack = { navController.popBackStack() },
                    onHome = {
                        navController.popBackStack(Screen.Report.route, inclusive = false)
                    },
                    onNext = { difficulty ->
                        navController.navigate(Screen.Test.createRoute(difficulty)) {
                            popUpTo(Screen.Report.route)
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
                        navController.popBackStack(Screen.Report.route, inclusive = false)
                    },
                    onFinish = { sessionId ->
                        navController.navigate(Screen.Review.createRoute(sessionId)) {
                            popUpTo(Screen.Report.route)
                        }
                    }
                )
            }

            // 세션 목록
            composable(Screen.ReviewList.route) {
                ReviewListScreen(
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
                    onNavigateToStudy = { type, set ->
                        // popUpTo 없음 → Back 시 Review로 복귀
                        navController.navigate(Screen.Study.createRoute(type = type, set = set)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.Study.route,
                arguments = listOf(
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("grade") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("set") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val initialTopicType = backStackEntry.arguments?.getString("type")
                val initialTopicSet  = backStackEntry.arguments?.getString("set")
                val initialGrade     = backStackEntry.arguments?.getString("grade")
                StudyScreen(
                    initialTopicType = initialTopicType,
                    initialTopicSet  = initialTopicSet,
                    initialGrade = initialGrade,
                    onPractice = { questionId ->
                        navController.navigate(Screen.Practice.createRoute(questionId))
                    }
                )
            }

            composable(
                route = Screen.Practice.route,
                arguments = listOf(navArgument("questionId") { type = NavType.IntType })
            ) {
                PracticeScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToQuestion = { questionId ->
                        navController.navigate(Screen.Practice.createRoute(questionId)) {
                            popUpTo(Screen.Practice.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Report.route) {
                ReportScreen(
                    onSessionClick = { sessionId ->
                        navController.navigate(Screen.Review.createRoute(sessionId))
                    },
                    onVocabClick = {
                        navController.navigate(Screen.Vocabulary.route) {
                            popUpTo(Screen.Report.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onTopicClick = { type ->
                        navController.navigate(Screen.Study.createRoute(type = type)) {
                            popUpTo(Screen.Report.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onGradeClick = { grade ->
                        navController.navigate(Screen.Study.createRoute(grade = grade)) {
                            popUpTo(Screen.Report.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Vocabulary.route) {
                VocabularyScreen()
            }

            composable(Screen.StudyFromSettings.route) {
                StudyScreen(
                    fromSettings = true,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onStudyLink = { navController.navigate(Screen.StudyFromSettings.route) }
                )
            }
        }
    }
}
