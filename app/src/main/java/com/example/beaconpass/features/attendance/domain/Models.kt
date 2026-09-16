package com.example.beaconpass.features.attendance.domain

data class CourseAttendance(
    val courseCode: String,
    val courseName: String,
    val facultyName: String,
    val attendedCount: Int,
    val totalCount: Int
) {
    val percentage: Float
        get() = if (totalCount == 0) 0f else (attendedCount.toFloat() / totalCount) * 100f

    val isShortage: Boolean
        get() = percentage < 75.0f
}