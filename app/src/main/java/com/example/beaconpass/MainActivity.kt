package com.example.beaconpass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.beaconpass.core.navigation.Screen
import com.example.beaconpass.features.attendance.presentation.AttendanceProcessScreen
import com.example.beaconpass.features.attendance.presentation.StudentDashboardScreen
import com.example.beaconpass.features.auth.presentation.LoginScreen
import com.example.beaconpass.features.teacher.presentation.TeacherDashboardScreen
import com.example.beaconpass.ui.theme.BeaconPassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeaconPassTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Login.route
                    ) {
                        // 1. Login Screen
                        composable(Screen.Login.route) {
                            LoginScreen(
                                onLoginSuccess = { profile ->
                                    if (profile.role.equals("teacher", ignoreCase = true)) {
                                        navController.navigate(Screen.TeacherDashboard.createRoute(profile.name)) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate(Screen.StudentDashboard.createRoute(profile.rollNo)) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }

                        // 2. Student Dashboard
                        composable(
                            route = Screen.StudentDashboard.route,
                            arguments = listOf(navArgument("rollNo") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val rollNo = backStackEntry.arguments?.getString("rollNo") ?: ""
                            StudentDashboardScreen(
                                rollNo = rollNo,
                                onNavigateToAttendance = {
                                    navController.navigate(Screen.AttendanceProcess.route)
                                }
                            )
                        }

                        // 3. Attendance Stepper (BLE + CameraX Liveness)
                        composable(Screen.AttendanceProcess.route) {
                            AttendanceProcessScreen(
                                onDismiss = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // 4. Teacher Live Radar Dashboard
                        composable(
                            route = Screen.TeacherDashboard.route,
                            arguments = listOf(navArgument("facultyEmail") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val facultyEmail = backStackEntry.arguments?.getString("facultyEmail") ?: "Faculty Console"
                            TeacherDashboardScreen(
                                facultyEmail = facultyEmail,
                                onLogout = {
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}