package com.example.beaconpass.features.teacher.data

import com.example.beaconpass.core.network.SupabaseNetworkClient
import com.example.beaconpass.features.teacher.presentation.LiveStudentEntry
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.PostgresAction
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

    // 1. Create a dynamic active session with duration
    suspend fun startAttendanceSession(
        courseId: String = "22222222-2222-2222-2222-222222222222", // CS-402
        roomId: String = "11111111-1111-1111-1111-111111111111",   // Room 402
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
            sessionId
        }
    }

    // 2. Stop/Close the active session
    // 2. Stop/Close the active session
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
            Unit // Explicitly return Unit so runCatching evaluates to Result<Unit>
        }
    }

    // 3. Listen to incoming attendance records via Supabase Realtime WebSocket
    suspend fun subscribeToLiveAttendance(sessionId: String): Flow<LiveStudentEntry> {
        val channel = realtime.channel("session_$sessionId")

        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "attendance_records"
        }

        channel.subscribe()

        return changeFlow.mapNotNull { action ->
            val record = action.decodeRecord<LiveAttendanceRecordDto>()

            if (record.sessionId == sessionId) {
                // Fetch student name & roll number from profiles
                val student = postgrest.from("profiles")
                    .select(columns = Columns.list("id", "name", "roll_no")) {
                        filter {
                            eq("id", record.studentId)
                        }
                    }.decodeSingleOrNull<StudentProfileDto>()

                val timeStr = record.verifiedAt.take(19).replace("T", " ")

                LiveStudentEntry(
                    rollNo = student?.rollNo ?: "UNKNOWN",
                    name = student?.name ?: "Student",
                    timestamp = timeStr,
                    rssi = record.rssiRecorded.toInt()
                )
            } else null
        }.flowOn(Dispatchers.IO)
    }
}