package com.example.beaconpass.features.attendance.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Reusable State Machine: Yahi states real BLE & ML Kit ke sath swap hongi
sealed class AttendanceStepState {
    object BleScanning : AttendanceStepState()
    data class FaceLiveness(val detectedRssi: Int, val promptText: String) : AttendanceStepState()
    data class SuccessReceipt(val room: String, val rssi: Int, val timestamp: String) : AttendanceStepState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceProcessScreen(
    onDismiss: () -> Unit
) {
    // Current Step State (Abhi testing ke liye timer se automatically step-by-step switch hoga)
    var currentState by remember { mutableStateOf<AttendanceStepState>(AttendanceStepState.BleScanning) }

    // Simulation Engine: Baad me sirf yeh Coroutine block hatega aur real sensors attach honge
    LaunchedEffect(Unit) {
        // 1. Simulating 3-second BLE burst scan[cite: 1, 2]
        delay(3000)
        currentState = AttendanceStepState.FaceLiveness(
            detectedRssi = -58,
            promptText = "Action Required: Blink both eyes"
        )

        // 2. Simulating 3-second face detection & blink verification[cite: 1, 2]
        delay(3000)
        currentState = AttendanceStepState.SuccessReceipt(
            room = "CS-402",
            rssi = -58,
            timestamp = "Today, 03:45 PM"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Attendance Verification",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8FAFC))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentState,
                label = "StepTransition"
            ) { state ->
                when (state) {
                    is AttendanceStepState.BleScanning -> BleScanningView()
                    is AttendanceStepState.FaceLiveness -> FaceLivenessView(state.detectedRssi, state.promptText)
                    is AttendanceStepState.SuccessReceipt -> SuccessReceiptView(
                        room = state.room,
                        rssi = state.rssi,
                        timestamp = state.timestamp,
                        onDone = onDismiss
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// STEP 1: Animated BLE Radar Waves View[cite: 1, 2]
// ----------------------------------------------------
@Composable
fun BleScanningView() {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarWave")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveScale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "STEP 1 OF 2",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2563EB),
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Detecting Classroom Beacon",
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            modifier = Modifier.padding(top = 4.dp, bottom = 36.dp)
        )

        // Pulsing Ripple Radar Canvas
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                // Outer expanding animated ring
                drawCircle(
                    color = Color(0xFF2563EB).copy(alpha = waveAlpha),
                    radius = radius * waveScale,
                    style = Stroke(width = 4.dp.toPx())
                )
                // Mid static boundary circle
                drawCircle(
                    color = Color(0xFFDBEAFE),
                    radius = radius * 0.65f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Central Beacon Icon
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Sensors,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Scanning for Room CS-402...",
            fontSize = 14.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Measuring physical signal strength (RSSI)[cite: 1, 2]",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ----------------------------------------------------
// STEP 2: Face Liveness Viewfinder View[cite: 1, 2]
// ----------------------------------------------------
@Composable
fun FaceLivenessView(rssi: Int, prompt: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Beacon Found Badge
        Row(
            modifier = Modifier
                .background(Color(0xFFDCFCE7), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF10B981), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Beacon Verified: $rssi dBm (Inside Room)[cite: 1, 2]",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF166534)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "STEP 2 OF 2",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2563EB),
            letterSpacing = 1.2.sp
        )
        Text(
            text = "Facial Liveness Check",
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Mock Camera Viewfinder with Oval Cutout
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(280.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(110.dp))
                .border(3.dp, Color(0xFF2563EB), RoundedCornerShape(110.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Face,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Camera Preview Area",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Dynamic Interactive Prompt Chip
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE))
        ) {
            Text(
                text = prompt,
                color = Color(0xFF1E40AF),
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

// ----------------------------------------------------
// STEP 3: Verification Receipt View[cite: 1, 2]
// ----------------------------------------------------
@Composable
fun SuccessReceiptView(
    room: String,
    rssi: Int,
    timestamp: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success Icon
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Color(0xFFDCFCE7), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Attendance Recorded!",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Verified via BeaconPass Secure Engine",
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Transaction Receipt Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ReceiptRow("Session / Room", "$room (Computer Networks)")
                Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 12.dp))
                ReceiptRow("Recorded RSSI", "$rssi dBm (Strong Presence)[cite: 1, 2]")
                Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 12.dp))
                ReceiptRow("Facial Liveness", "Passed (Blink Verified)[cite: 1, 2]")
                Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 12.dp))
                ReceiptRow("Timestamp", timestamp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Text(
                text = "Back to Dashboard",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}