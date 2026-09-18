package com.example.beaconpass.core.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class BleTeacherTrigger(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter = bluetoothManager?.adapter
    private val TAG = "BLE_TRIGGER"

    private val serviceUuid = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val charUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    suspend fun triggerEsp32(durationSeconds: Int): Result<Unit> = suspendCancellableCoroutine { cont ->
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || !adapter?.isEnabled!!) {
            cont.resume(Result.failure(IllegalStateException("Bluetooth is disabled.")))
            return@suspendCancellableCoroutine
        }

        val scanFilter = ScanFilter.Builder()
            .setDeviceName("BeaconPass_ESP32")
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        var isDone = false
        val handler = Handler(Looper.getMainLooper())

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (isDone) return
                val device = result.device
                Log.d(TAG, "Found ESP32: ${device.address}. Connecting GATT...")
                isDone = true
                scanner.stopScan(this)

                // Connect to ESP32 GATT
                device.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d(TAG, "Connected to ESP32. Discovering services...")
                            gatt.discoverServices()
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            gatt.close()
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        val characteristic = gatt.getService(serviceUuid)?.getCharacteristic(charUuid)
                        if (characteristic != null) {
                            characteristic.value = byteArrayOf(durationSeconds.toByte())
                            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                            gatt.writeCharacteristic(characteristic)
                            Log.d(TAG, "Sent duration: ${durationSeconds}s to ESP32.")
                        } else {
                            gatt.disconnect()
                            cont.resume(Result.failure(IllegalStateException("ESP32 Service not found.")))
                        }
                    }

                    override fun onCharacteristicWrite(gatt: BluetoothGatt, char: BluetoothGattCharacteristic, status: Int) {
                        Log.d(TAG, "ESP32 acknowledged trigger write. Disconnecting.")
                        gatt.disconnect()
                        cont.resume(Result.success(Unit))
                    }
                })
            }

            override fun onScanFailed(errorCode: Int) {
                if (!isDone) {
                    isDone = true
                    cont.resume(Result.failure(IllegalStateException("Scan failed with error: $errorCode")))
                }
            }
        }

        scanner.startScan(listOf(scanFilter), settings, callback)

        // 7-second scan timeout
        handler.postDelayed({
            if (!isDone) {
                isDone = true
                scanner.stopScan(callback)
                cont.resume(Result.failure(IllegalStateException("ESP32 not found. Make sure it is powered on.")))
            }
        }, 7000)
    }
}