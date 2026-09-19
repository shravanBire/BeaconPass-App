package com.example.beaconpass.core.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login_screen")
    object StudentDashboard : Screen("student_dashboard_screen/{rollNo}") {
        fun createRoute(rollNo: String) = "student_dashboard_screen/$rollNo"
    }
    object AttendanceProcess : Screen("attendance_process_screen")
    object TeacherDashboard : Screen("teacher_dashboard_screen/{facultyEmail}") {
        fun createRoute(facultyEmail: String) = "teacher_dashboard_screen/$facultyEmail"
    }
    object FaceEnrollment : Screen("face_enrollment/{userId}") {
        fun createRoute(userId: String) = "face_enrollment/$userId"
    }
}

