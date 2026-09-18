package com.example.beaconpass.features.attendance.data

import com.example.beaconpass.core.network.AttendanceRpcResponse
import com.example.beaconpass.core.network.SessionInfo
import com.example.beaconpass.core.network.SupabaseNetworkClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class AttendanceRepository {

    private val postgrest = SupabaseNetworkClient.postgrest

    // 1. Fetch currently active session created by Teacher
    suspend fun getActiveSession(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sessions = postgrest.from("attendance_sessions")
                .select {
                    filter {
                        eq("is_active", true)
                    }
                }.decodeList<SessionInfo>()

            val validSession = sessions.firstOrNull {
                Instant.parse(it.expiresAt).isAfter(Instant.now())
            } ?: throw IllegalStateException("Teacher has not opened an active window yet.")

            validSession.id
        }
    }

    // 2. Submit attendance RPC call
    suspend fun submitAttendance(
        sessionId: String,
        token: Long,
        rssi: Double,
        deviceId: String
    ): Result<AttendanceRpcResponse> {
        return runCatching {
            val params = buildJsonObject {
                put("p_session_id", sessionId)
                put("p_token", token)
                put("p_rssi", rssi)
                put("p_device_id", deviceId)
            }

            postgrest.rpc(
                function = "mark_attendance_secure",
                parameters = params
            ).decodeAs<AttendanceRpcResponse>()
        }
    }
}