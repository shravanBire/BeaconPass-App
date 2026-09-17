package com.example.beaconpass.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VerifiedBeaconResult(
    val sessionId: Long,
    val rotatingToken: Long,
    val trimmedRssi: Double,
    val isInside: Boolean,
    val remainingSec: Int
)

sealed class BleScanStatus {
    object Idle : BleScanStatus()
    object Scanning : BleScanStatus()
    data class Success(val result: VerifiedBeaconResult) : BleScanStatus()
    data class OutOfBounds(val avgRssi: Double) : BleScanStatus()
    data class BeaconNotFound(val reason: String) : BleScanStatus()
    data class Error(val message: String) : BleScanStatus()
}

class BleAttendanceScanner(private val bluetoothAdapter: BluetoothAdapter?) {

    private val scanner: BluetoothLeScanner? get() = bluetoothAdapter?.bluetoothLeScanner
    private val rssiSamples = mutableListOf<Int>()
    private var capturedMfgBytes: ByteArray? = null
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun startBurstScan(onStatusUpdate: (BleScanStatus) -> Unit) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            onStatusUpdate(BleScanStatus.Error("Bluetooth is turned off. Please enable Bluetooth."))
            return
        }

        val bleScanner = scanner
        if (bleScanner == null) {
            onStatusUpdate(BleScanStatus.Error("BLE Hardware Scanner unavailable on this device."))
            return
        }

        rssiSamples.clear()
        capturedMfgBytes = null
        onStatusUpdate(BleScanStatus.Scanning)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { res ->
                    val name = res.device.name ?: res.scanRecord?.deviceName
                    val mfgMap = res.scanRecord?.manufacturerSpecificData

                    // 0xFFFE company ID match check (our ESP32 firmware)
                    val beaconBytes = mfgMap?.get(0xFFFE)

                    if (name == "BeaconPass_ESP32" || beaconBytes != null) {
                        rssiSamples.add(res.rssi)
                        if (beaconBytes != null && beaconBytes.size >= 10) {
                            capturedMfgBytes = beaconBytes
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                onStatusUpdate(BleScanStatus.Error("Scan failed error code: $errorCode"))
            }
        }

        // Run hardware scan
        bleScanner.startScan(null, settings, scanCallback)

        // 3.5 second scan burst cutoff
        handler.postDelayed({
            try {
                bleScanner.stopScan(scanCallback)
            } catch (_: Exception) {}

            val bytes = capturedMfgBytes
            if (rssiSamples.isEmpty() || bytes == null) {
                onStatusUpdate(BleScanStatus.BeaconNotFound("ESP32 beacon not detected. Ensure you are in Room 402."))
                return@postDelayed
            }

            // Trimmed Mean calculation (top & bottom 20% outliers dropped)
            val avgRssi = calculateTrimmedMean(rssiSamples)
            val isInsideRoom = avgRssi >= -68.0 // Boundary cutoff

            // Parse ESP32 14-byte Little Endian payload[cite: 1, 2]
            val sessionBuf = ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN)
            val sessionId = sessionBuf.int.toLong() and 0xFFFFFFFFL

            val tokenBuf = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN)
            val rollingToken = tokenBuf.int.toLong() and 0xFFFFFFFFL

            val remainingSec = if (bytes.size >= 11) bytes[10].toInt() and 0xFF else 0

            val result = VerifiedBeaconResult(
                sessionId = sessionId,
                rotatingToken = rollingToken,
                trimmedRssi = avgRssi,
                isInside = isInsideRoom,
                remainingSec = remainingSec
            )

            if (!isInsideRoom) {
                onStatusUpdate(BleScanStatus.OutOfBounds(avgRssi))
            } else {
                onStatusUpdate(BleScanStatus.Success(result))
            }
        }, 3500)
    }

    private fun calculateTrimmedMean(samples: List<Int>): Double {
        if (samples.isEmpty()) return -100.0
        if (samples.size < 5) return samples.average()

        val sorted = samples.sorted()
        val trim = (sorted.size * 0.20).toInt() // 20%[cite: 1, 2]
        val center = sorted.subList(trim, sorted.size - trim)
        return center.average()
    }
}