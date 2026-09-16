package com.example.beaconpass.features.attendance.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beaconpass.features.attendance.domain.CourseAttendance

@Composable
fun StudentDashboardScreen(
    rollNo: String,
    onNavigateToAttendance: () -> Unit
) {
    // Mock courses list for demonstration
    val courses = remember {
        listOf(
            CourseAttendance("CS-402", "Computer Networks", "Dr. A. Sharma", 28, 32),
            CourseAttendance("CS-404", "Database Management", "Prof. R. Verma", 26, 30),
            CourseAttendance("CS-406", "Compiler Design", "Dr. K. Patel", 19, 28), // <75% Shortage
            CourseAttendance("CS-408", "Machine Learning", "Dr. S. Mehta", 25, 29)
        )
    }

    val totalAttended = courses.sumOf { it.attendedCount }
    val totalClasses = courses.sumOf { it.totalCount }
    val overallPercentage = if (totalClasses == 0) 0f else (totalAttended.toFloat() / totalClasses) * 100f

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAttendance,
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.Sensors, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mark Attendance (Room 402)", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Student Profile Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Welcome Back,",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "Student ($rollNo)",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rollNo.takeLast(2).ifEmpty { "ST" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Attendance Summary Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Overall Attendance",
                                fontSize = 13.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "%.1f%%".format(overallPercentage),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (overallPercentage >= 75f) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$totalAttended of $totalClasses classes attended",
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )
                        }

                        // Circular Progress Indicator
                        Box(contentAlignment = Alignment.Center) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = overallPercentage / 100f,
                                label = "progressAnimation"
                            )
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(68.dp),
                                strokeWidth = 7.dp,
                                color = if (overallPercentage >= 75f) Color(0xFF10B981) else Color(0xFFEF4444),
                                trackColor = Color(0xFFF1F5F9)
                            )
                            Text(
                                text = if (overallPercentage >= 75f) "SAFE" else "ALERT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (overallPercentage >= 75f) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            // Active Class Banner (Triggered by ESP32 window)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF2563EB), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LIVE SESSION • ROOM 402",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "CS-402: Computer Networks",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Beacon active: ~01:45 remaining",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // Section Title
            item {
                Text(
                    text = "Enrolled Courses",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Course Cards List
            items(courses) { course ->
                CourseAttendanceItem(course)
            }

            item {
                Spacer(modifier = Modifier.height(72.dp)) // Padding for FAB
            }
        }
    }
}

@Composable
fun CourseAttendanceItem(course: CourseAttendance) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = course.courseCode,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                    Text(
                        text = course.courseName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = course.facultyName,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "%.0f%%".format(course.percentage),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (course.isShortage) Color(0xFFEF4444) else Color(0xFF10B981)
                    )
                    Text(
                        text = "${course.attendedCount}/${course.totalCount}",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { course.percentage / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (course.isShortage) Color(0xFFEF4444) else Color(0xFF10B981),
                trackColor = Color(0xFFF1F5F9)
            )

            if (course.isShortage) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Below 75% requirement. Attendance shortage alert!",
                        fontSize = 11.sp,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}