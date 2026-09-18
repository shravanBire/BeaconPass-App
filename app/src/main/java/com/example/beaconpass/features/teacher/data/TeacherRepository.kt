package com.example.beaconpass.features.teacher.data

import android.util.Log
import com.example.beaconpass.core.network.SupabaseNetworkClient
import com.example.beaconpass.features.teacher.presentation.LiveStudentEntry
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable
data class LiveAttendanceRecordDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("rssi_recorded") val rssiRecorded: Double,
    @SerialName("token_submitted") val tokenSubmitted: Long,
    @SerialName("verified_at") val verifiedAt: String
)

@Serializable
data class StudentProfileDto(
    val id: String,
    val name: String,
    @SerialName("roll_no") val rollNo: String
)

class TeacherRepository {

    private val postgrest = SupabaseNetworkClient.postgrest
    private val realtime = SupabaseNetworkClient.realtime
    private val TAG = "BEACON_TEACHER"

    suspend fun startAttendanceSession(
        courseId: String = "22222222-2222-2222-2222-222222222222",
        roomId: String = "11111111-1111-1111-1111-111111111111",
        durationSeconds: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sessionId = UUID.randomUUID().toString()
            val now = Instant.now()
            val expiresAt = now.plus(durationSeconds.toLong(), ChronoUnit.SECONDS).toString()

            val sessionPayload = buildJsonObject {
                put("id", sessionId)
                put("course_id", courseId)
                put("room_id", roomId)
                put("secret_key", "SECRET_ROOM_402_KEY")
                put("session_id_hex", "0x4102")
                put("is_active", true)
                put("starts_at", now.toString())
                put("expires_at", expiresAt)
            }

            postgrest.from("attendance_sessions").insert(sessionPayload)
            Log.d(TAG, "Session created successfully in DB: $sessionId")
            sessionId
        }
    }

    suspend fun stopAttendanceSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            postgrest.from("attendance_sessions").update(
                buildJsonObject {
                    put("is_active", false)
                }
            ) {
                filter {
                    eq("id", sessionId)
                }
            }
            Log.d(TAG, "Session stopped: $sessionId")
            Unit
        }
    }

    suspend fun subscribeToLiveAttendance(sessionId: String): Flow<LiveStudentEntry> {
        val channel = realtime.channel("attendance_feed_$sessionId")

        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "attendance_records"
        }

        // Connect and subscribe asynchronously without blocking the thread
        if (realtime.status.value != Realtime.Status.CONNECTED) {
            Log.d(TAG, "Connecting to Realtime WebSocket...")
            realtime.connect()
        }

        channel.subscribe()
        Log.d(TAG, "Subscription requested for channel: attendance_feed_$sessionId")

        return changeFlow.mapNotNull { action ->
            Log.d(TAG, "Realtime event raw received: $action")
            try {
                val record = action.decodeRecord<LiveAttendanceRecordDto>()
                Log.d(TAG, "Decoded record session: ${record.sessionId}")

                if (record.sessionId == sessionId) {
                    val student = postgrest.from("profiles")
                        .select(columns = Columns.list("id", "name", "roll_no")) {
                            filter {
                                eq("id", record.studentId)
                            }
                        }.decodeSingleOrNull<StudentProfileDto>()

                    val timeStr = record.verifiedAt.take(19).replace("T", " ")

                    LiveStudentEntry(
                        rollNo = student?.rollNo ?: "UNKNOWN",
                        name = student?.name ?: "Verified Student",
                        timestamp = timeStr,
                        rssi = record.rssiRecorded.toInt()
                    )
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error mapping realtime record: ${e.message}", e)
                null
            }
        }.flowOn(Dispatchers.IO)
    }
}