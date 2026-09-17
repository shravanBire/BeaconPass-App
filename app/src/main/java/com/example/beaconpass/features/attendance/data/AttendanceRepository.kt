package com.example.beaconpass.features.attendance.data

import com.example.beaconpass.core.network.AttendanceRpcResponse
import com.example.beaconpass.core.network.SupabaseNetworkClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AttendanceRepository {

    private val postgrest = SupabaseNetworkClient.postgrest

    /**
     * Server-side PostgreSQL RPC function 'mark_attendance_secure' ko call karta hai.
     */
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