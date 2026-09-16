package com.example.beaconpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.beaconpass.core.navigation.Screen
import com.example.beaconpass.features.attendance.presentation.AttendanceProcessScreen
import com.example.beaconpass.features.attendance.presentation.StudentDashboardScreen
import com.example.beaconpass.features.auth.presentation.LoginScreen
import com.example.beaconpass.features.auth.presentation.UserRole
import com.example.beaconpass.features.teacher.presentation.TeacherDashboardScreen
import com.example.beaconpass.ui.theme.BeaconPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeaconPassTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = Screen.Login.route
                ) {
                    // Login Screen Route
                    composable(Screen.Login.route) {
                        LoginScreen(
                            onLoginSuccess = { role, identifier, _ ->
                                if (role == UserRole.STUDENT) {
                                    navController.navigate(Screen.StudentDashboard.createRoute(identifier)) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(Screen.TeacherDashboard.createRoute(identifier)) {
                                        popUpTo(Screen.Login.route) { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    // Student Dashboard Screen Route
                    composable(Screen.StudentDashboard.route) { backStackEntry ->
                        val rollNo = backStackEntry.arguments?.getString("rollNo") ?: "2023CS01"
                        StudentDashboardScreen(
                            rollNo = rollNo,
                            onNavigateToAttendance = {
                                navController.navigate(Screen.AttendanceProcess.route)
                            }
                        )
                    }

                    // In MainActivity.kt inside NavHost:
                    composable(Screen.TeacherDashboard.route) { backStackEntry ->
                        val email = backStackEntry.arguments?.getString("facultyEmail") ?: "prof.sharma@college.edu"
                        TeacherDashboardScreen(
                            facultyEmail = email,
                            onLogout = {
                                navController.navigate(Screen.Login.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    // Attendance Stepper Route placeholder
                    composable(Screen.AttendanceProcess.route) {
                        AttendanceProcessScreen(
                            onDismiss = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}

