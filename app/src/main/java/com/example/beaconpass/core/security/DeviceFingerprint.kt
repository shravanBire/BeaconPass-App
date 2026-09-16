package com.example.beaconpass.core.security

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

object DeviceFingerprint {
    @SuppressLint("HardwareIds")
    fun getHardwareFingerprint(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_DEVICE"

        val rawSignature = buildString {
            append(androidId)
            append(":")
            append(Build.MANUFACTURER)
            append(":")
            append(Build.MODEL)
            append(":")
            append(Build.BOARD)
            append(":")
            append(Build.FINGERPRINT)
        }

        return hashSha256(rawSignature)
    }

    private fun hashSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}