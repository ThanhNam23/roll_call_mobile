package com.example.roll_call.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.roll_call.ui.screens.AddStudentScreen
import com.example.roll_call.ui.screens.AttendanceHistoryScreen
import com.example.roll_call.ui.screens.AttendanceSessionListScreen
import com.example.roll_call.ui.screens.AttendanceSummaryScreen
import com.example.roll_call.ui.screens.ClassListScreen
import com.example.roll_call.ui.screens.FaceScannerScreen
import com.example.roll_call.ui.screens.LoginScreen
import com.example.roll_call.ui.screens.StudentListScreen
import com.example.roll_call.ui.screens.TeacherFaceRegistrationScreen

@Composable
fun AppNavHost(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.ClassList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ClassList.route) {
            ClassListScreen(
                onClassClick = { classId, className ->
                    navController.navigate(Screen.AttendanceSessionList.createRoute(classId, className))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRegisterTeacherFace = {
                    navController.navigate(Screen.TeacherFaceRegistration.route)
                }
            )
        }

        composable(
            route = Screen.AttendanceSessionList.route,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            AttendanceSessionListScreen(
                classId = classId,
                className = className,
                onSessionClick = { sessionId, classIdParam, classNameParam ->
                    navController.navigate(Screen.FaceScanner.createRoute(sessionId, classIdParam, classNameParam))
                },
                onManageStudents = {
                    navController.navigate(Screen.AddStudent.createRoute(classId, className))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.StudentList.route,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            StudentListScreen(
                classId = classId,
                className = className,
                onStartAttendance = {
                    navController.navigate(Screen.FaceScanner.createRoute("", classId, className))
                },
                onManageStudents = {
                    navController.navigate(Screen.AddStudent.createRoute(classId, className))
                },
                onViewHistory = {
                    navController.navigate(Screen.AttendanceHistory.createRoute(classId, className))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddStudent.route,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            AddStudentScreen(
                classId = classId,
                className = className,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.FaceScanner.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            FaceScannerScreen(
                sessionId = sessionId,
                classId = classId,
                className = className,
                onFinish = { finalSessionId ->
                    navController.navigate(Screen.AttendanceSummary.createRoute(finalSessionId)) {
                        popUpTo(Screen.FaceScanner.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AttendanceSummary.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            AttendanceSummaryScreen(
                sessionId = sessionId,
                onDone = {
                    navController.navigate(Screen.ClassList.route) {
                        popUpTo(Screen.ClassList.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.AttendanceHistory.route,
            arguments = listOf(
                navArgument("classId") { type = NavType.StringType },
                navArgument("className") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val classId = backStackEntry.arguments?.getString("classId") ?: ""
            val className = backStackEntry.arguments?.getString("className") ?: ""
            AttendanceHistoryScreen(
                classId = classId,
                className = className,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TeacherFaceRegistration.route) {
            TeacherFaceRegistrationScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
