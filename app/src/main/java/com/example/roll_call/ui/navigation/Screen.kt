package com.example.roll_call.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object ClassList : Screen("class_list")
    object StudentList : Screen("student_list/{classId}/{className}") {
        fun createRoute(classId: String, className: String) = "student_list/$classId/$className"
    }
    object AddStudent : Screen("add_student/{classId}/{className}") {
        fun createRoute(classId: String, className: String) = "add_student/$classId/$className"
    }
    object FaceScanner : Screen("face_scanner/{classId}/{className}") {
        fun createRoute(classId: String, className: String) = "face_scanner/$classId/$className"
    }
    object AttendanceSummary : Screen("attendance_summary/{sessionId}") {
        fun createRoute(sessionId: String) = "attendance_summary/$sessionId"
    }
    object AttendanceHistory : Screen("attendance_history/{classId}/{className}") {
        fun createRoute(classId: String, className: String) = "attendance_history/$classId/$className"
    }
}

