package com.example.beaconpass.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Atomic mark_attendance_secure RPC ka JSON response
@Serializable
data class AttendanceRpcResponse(
    val success: Boolean,
    val message: String,
    val error: String? = null,
    val room: String? = null,
    @SerialName("record_id") val recordId: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null
)

// Profiles table model
@Serializable
data class UserProfile(
    val id: String,
    @SerialName("roll_no") val rollNo: String,
    val name: String,
    val role: String,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("face_embedding") val faceEmbedding: List<Double>? = null
)
// Active Session model
@Serializable
data class SessionInfo(
    val id: String,
    @SerialName("course_id") val courseId: String,
    @SerialName("room_id") val roomId: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("expires_at") val expiresAt: String
)

