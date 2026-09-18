package com.example.beaconpass.features.teacher.presentation

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.beaconpass.core.ble.BleTeacherTrigger
import kotlinx.coroutines.delay
import com.example.beaconpass.features.teacher.data.TeacherRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class LiveStudentEntry(
    val rollNo: String,
    val name: String,
    val timestamp: String,
    val rssi: Int,
    val isManual: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    facultyEmail: String,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val teacherRepository = remember { TeacherRepository() }

    var isSessionActive by remember { mutableStateOf(false) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var isStartingSession by remember { mutableStateOf(false) }
    var selectedDuration by remember { mutableStateOf(120) }
    var remainingTime by remember { mutableStateOf(120) }

    val listState = rememberLazyListState()
    val attendedStudents = remember { mutableStateListOf<LiveStudentEntry>() }

    val triggerHelper = remember { BleTeacherTrigger(context) }

    // Realtime WebSocket Collector: triggers when activeSessionId is set
    LaunchedEffect(activeSessionId) {
        val currentId = activeSessionId
        if (currentId != null) {
            teacherRepository.subscribeToLiveAttendance(currentId)
                .collectLatest { liveStudent ->
                    // Prevent UI duplicates if WebSocket re-emits
                    if (attendedStudents.none { it.rollNo == liveStudent.rollNo }) {
                        attendedStudents.add(0, liveStudent)
                    }
                }
        }
    }

    // Countdown Timer logic: automatically closes session on 0s
    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            remainingTime = selectedDuration
            while (remainingTime > 0 && isSessionActive) {
                delay(1000)
                remainingTime--
            }
            if (remainingTime <= 0) {
                activeSessionId?.let { teacherRepository.stopAttendanceSession(it) }
                isSessionActive = false
                activeSessionId = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Faculty Console", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(facultyEmail, fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Close, contentDescription = "Logout", tint = Color(0xFFEF4444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8FAFC))
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState, // Fixed: listState passed properly
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "ACTIVE COURSE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "CS-402: Computer Networks",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Room 402 • Total Registered: 70 Students",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Select Window Duration:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(60, 120, 180).forEach { sec ->
                                FilterChip(
                                    selected = selectedDuration == sec,
                                    onClick = { if (!isSessionActive && !isStartingSession) selectedDuration = sec },
                                    label = { Text("${sec}s") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF2563EB),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (!isSessionActive) {
                                    isStartingSession = true
                                    scope.launch {
                                        // 1. First trigger ESP32 hardware via BLE GATT
                                        val bleTriggerResult = triggerHelper.triggerEsp32(selectedDuration)

                                        bleTriggerResult.onSuccess {
                                            // 2. Once ESP32 starts broadcasting, create the Supabase Session
                                            attendedStudents.clear()
                                            val result = teacherRepository.startAttendanceSession(
                                                durationSeconds = selectedDuration
                                            )
                                            isStartingSession = false
                                            result.onSuccess { sessionId ->
                                                activeSessionId = sessionId
                                                isSessionActive = true
                                            }.onFailure { error ->
                                                Toast.makeText(context, "DB Error: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        }.onFailure { bleError ->
                                            isStartingSession = false
                                            Toast.makeText(context, "Hardware Trigger Failed: ${bleError.localizedMessage}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        activeSessionId?.let { teacherRepository.stopAttendanceSession(it) }
                                        isSessionActive = false
                                        activeSessionId = null
                                    }
                                }
                            },
                            enabled = !isStartingSession, // Prevents multiple fast taps
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSessionActive) Color(0xFFEF4444) else Color(0xFF2563EB)
                            )
                        ) {
                            if (isStartingSession) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (isSessionActive) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSessionActive) "Stop Broadcast Window" else "Open Attendance Window",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(visible = isSessionActive) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF10B981), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "BEACON BROADCAST LIVE",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }
                                Text(
                                    text = "%02d:%02d".format(remainingTime / 60, remainingTime % 60),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Receiving live presence confirmations...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color(0xFF1E293B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${attendedStudents.size}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF10B981)
                                    )
                                    Text(
                                        text = "of 70",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Real-Time Incoming Stream (${attendedStudents.size})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    TextButton(onClick = { }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export .xlsx", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Empty state placeholder
            if (attendedStudents.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isSessionActive) "Waiting for students to verify presence..." else "No active attendance session",
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            items(attendedStudents, key = { it.rollNo }) { student ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(Color(0xFFDCFCE7), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF166534),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = student.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "${student.rollNo} • ${student.timestamp}",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF1F5F9)
                        ) {
                            Text(
                                text = "${student.rssi} dBm",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}