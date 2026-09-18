package com.example.beaconpass.features.attendance.presentation

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.beaconpass.core.ble.BleAttendanceScanner
import com.example.beaconpass.core.ble.BleScanStatus
import com.example.beaconpass.core.ble.VerifiedBeaconResult
import com.example.beaconpass.core.security.DeviceFingerprint
import com.example.beaconpass.core.vision.CameraViewfinder
import com.example.beaconpass.core.vision.FaceLivenessAnalyzer
import com.example.beaconpass.features.attendance.data.AttendanceRepository
import kotlinx.coroutines.launch

sealed class AttendanceStepState {
    object BleScanning : AttendanceStepState()
    data class ScanFailed(val title: String, val message: String, val isOutOfBounds: Boolean) : AttendanceStepState()
    data class FaceLiveness(val verifiedBeacon: VerifiedBeaconResult, val promptText: String) : AttendanceStepState()
    object Submitting : AttendanceStepState() // Server verification state
    data class SuccessReceipt(val room: String, val rssi: Double, val token: Long, val timestamp: String) : AttendanceStepState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceProcessScreen(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentState by remember { mutableStateOf<AttendanceStepState>(AttendanceStepState.BleScanning) }

    val attendanceRepository = remember { AttendanceRepository() }
    val deviceFingerprint = remember { DeviceFingerprint.getHardwareFingerprint(context) }

    val bluetoothManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    val bleScanner = remember { BleAttendanceScanner(bluetoothManager?.adapter) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    val triggerRealBleScan: () -> Unit = {
        currentState = AttendanceStepState.BleScanning
        bleScanner.startBurstScan { status ->
            when (status) {
                is BleScanStatus.Scanning -> {
                    currentState = AttendanceStepState.BleScanning
                }
                is BleScanStatus.Success -> {
                    currentState = AttendanceStepState.FaceLiveness(
                        verifiedBeacon = status.result,
                        promptText = "Action Required: Blink both eyes slowly"
                    )
                }
                is BleScanStatus.OutOfBounds -> {
                    currentState = AttendanceStepState.ScanFailed(
                        title = "Outside Classroom Boundary",
                        message = "Signal strength (%.1f dBm) indicates you are outside Room 402. Attendance rejected.".format(status.avgRssi),
                        isOutOfBounds = true
                    )
                }
                is BleScanStatus.BeaconNotFound -> {
                    currentState = AttendanceStepState.ScanFailed(
                        title = "Beacon Not Detected",
                        message = status.reason,
                        isOutOfBounds = false
                    )
                }
                is BleScanStatus.Error -> {
                    currentState = AttendanceStepState.ScanFailed(
                        title = "Scanner Error",
                        message = status.message,
                        isOutOfBounds = false
                    )
                }
                else -> Unit
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) {
            triggerRealBleScan()
        } else {
            currentState = AttendanceStepState.ScanFailed(
                title = "Permission Required",
                message = "Camera, Bluetooth, and Location are mandatory to verify physical presence.",
                isOutOfBounds = false
            )
        }
    }

    LaunchedEffect(Unit) {
        val hasPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermissions) {
            triggerRealBleScan()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Attendance Verification", fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
            AnimatedContent(targetState = currentState, label = "StepTransition") { state ->
                when (state) {
                    is AttendanceStepState.BleScanning -> BleScanningView()

                    is AttendanceStepState.FaceLiveness -> FaceLivenessView(
                        rssi = state.verifiedBeacon.trimmedRssi.toInt(),
                        token = state.verifiedBeacon.rotatingToken,
                        onLivenessSuccess = {
                            val beacon = state.verifiedBeacon
                            currentState = AttendanceStepState.Submitting

                            scope.launch {
                                // Pehle teacher ka live active session dhoondo
                                val sessionResult = attendanceRepository.getActiveSession()

                                sessionResult.onSuccess { liveSessionId ->
                                    // Live ID ke sath server RPC submit karo
                                    val result = attendanceRepository.submitAttendance(
                                        sessionId = liveSessionId,
                                        token = beacon.rotatingToken,
                                        rssi = beacon.trimmedRssi,
                                        deviceId = deviceFingerprint
                                    )

                                    result.onSuccess { rpcRes ->
                                        if (rpcRes.success) {
                                            currentState = AttendanceStepState.SuccessReceipt(
                                                room = rpcRes.room ?: "CS-402",
                                                rssi = beacon.trimmedRssi,
                                                token = beacon.rotatingToken,
                                                timestamp = rpcRes.verifiedAt?.take(19)?.replace("T", " ") ?: "Verified Just Now"
                                            )
                                        } else {
                                            currentState = AttendanceStepState.ScanFailed(
                                                title = "Verification Rejected",
                                                message = rpcRes.message,
                                                isOutOfBounds = rpcRes.error == "OUT_OF_BOUNDS"
                                            )
                                        }
                                    }.onFailure { error ->
                                        currentState = AttendanceStepState.ScanFailed(
                                            title = "Network Error",
                                            message = error.localizedMessage ?: "Failed to reach Supabase server.",
                                            isOutOfBounds = false
                                        )
                                    }
                                }.onFailure { noSession ->
                                    currentState = AttendanceStepState.ScanFailed(
                                        title = "No Active Session",
                                        message = noSession.localizedMessage ?: "Teacher window is closed.",
                                        isOutOfBounds = false
                                    )
                                }
                            }
                        }
                    )
                    is AttendanceStepState.Submitting -> SubmittingView()

                    is AttendanceStepState.SuccessReceipt -> SuccessReceiptView(
                        room = state.room,
                        rssi = state.rssi,
                        token = state.token,
                        timestamp = state.timestamp,
                        onDone = onDismiss
                    )

                    is AttendanceStepState.ScanFailed -> ScanFailedView(
                        title = state.title,
                        message = state.message,
                        isOutOfBounds = state.isOutOfBounds,
                        onRetry = triggerRealBleScan,
                        onCancel = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun SubmittingView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 4.dp,
            color = Color(0xFF2563EB)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Validating Presence...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Text(
            text = "Executing atomic token and device lock verification",
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ScanFailedView(
    title: String,
    message: String,
    isOutOfBounds: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Color(0xFFFEE2E2), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            fontSize = 13.sp,
            color = Color(0xFF64748B),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Text("Retry Scan", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Cancel", fontWeight = FontWeight.Bold, color = Color(0xFF475569))
        }
    }
}

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
            text = "Detecting Physical ESP32 Beacon",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A),
            modifier = Modifier.padding(top = 4.dp, bottom = 36.dp)
        )

        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2
                drawCircle(
                    color = Color(0xFF2563EB).copy(alpha = waveAlpha),
                    radius = radius * waveScale,
                    style = Stroke(width = 4.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFFDBEAFE),
                    radius = radius * 0.65f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

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
            text = "Listening for 0xFFFE packet from ESP32...",
            fontSize = 13.5.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Running Trimmed Mean RSSI proximity filter[cite: 1, 2]",
            fontSize = 11.5.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun FaceLivenessView(
    rssi: Int,
    token: Long,
    onLivenessSuccess: () -> Unit
) {
    var livenessPrompt by remember { mutableStateOf("Position your face inside the oval[cite: 1, 2]") }

    val faceAnalyzer = remember {
        FaceLivenessAnalyzer(
            onLivenessPassed = {
                onLivenessSuccess()
            },
            onFaceStatusUpdate = { status ->
                livenessPrompt = status
            }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Beacon Verified Tag
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
                text = "ESP32 Verified: $rssi dBm | Token: %06d".format(token),
                fontSize = 11.5.sp,
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
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        // Real CameraX Front Viewfinder with Oval Boundary Mask[cite: 1, 2]
        Box(
            modifier = Modifier
                .width(230.dp)
                .height(290.dp)
                .clip(RoundedCornerShape(115.dp))
                .border(3.dp, Color(0xFF2563EB), RoundedCornerShape(115.dp)),
            contentAlignment = Alignment.Center
        ) {
            CameraViewfinder(
                modifier = Modifier.fillMaxSize(),
                analyzer = faceAnalyzer
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Dynamic Interactive Challenge Chip
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE))
        ) {
            Text(
                text = livenessPrompt,
                color = Color(0xFF1E40AF),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun SuccessReceiptView(
    room: String,
    rssi: Double,
    token: Long,
    timestamp: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
            text = "Verified via BeaconPass Hardware Proximity[cite: 1, 2]",
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ReceiptRow("Verified Room", "$room (Computer Networks)")
                Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 10.dp))
                ReceiptRow("Trimmed Mean RSSI", "%.1f dBm (Inside Boundary)".format(rssi))
                Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 10.dp))
                ReceiptRow("Dynamic Hash/Token", "%06d (Synchronized)".format(token))
                Divider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 10.dp))
                ReceiptRow("Capture Time", timestamp)
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
        ) {
            Text("Back to Dashboard", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
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
        Text(text = label, fontSize = 12.5.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}