package com.example.beaconpass.features.auth.data

import com.example.beaconpass.core.network.SupabaseNetworkClient
import com.example.beaconpass.core.network.UserProfile
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed class AuthResult {
    data class Success(val profile: UserProfile) : AuthResult()
    data class DeviceMismatch(val message: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository {

    private val auth = SupabaseNetworkClient.auth
    private val postgrest = SupabaseNetworkClient.postgrest

    /**
     * Supabase Email Auth + Hardware Keystore Device Lock verification.
     */
    suspend fun signInAndVerifyDevice(
        email: String,
        pass: String,
        currentDeviceId: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            // 1. Supabase Auth Sign In
            auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }

            val user = auth.currentUserOrNull()
                ?: return@withContext AuthResult.Error("Authentication failed: User session null.")

            // 2. Fetch User Profile from PostgreSQL 'profiles' table
            val profile = postgrest.from("profiles")
                .select {
                    filter {
                        eq("id", user.id)
                    }
                }
                .decodeSingle<UserProfile>()

            // 3. Hardware Fingerprint Validation
            if (profile.deviceId.isNullOrBlank()) {
                // First-Time Login: Permanently lock this phone's hardware ID to account
                postgrest.from("profiles").update(
                    buildJsonObject {
                        put("device_id", currentDeviceId)
                    }
                ) {
                    filter {
                        eq("id", user.id)
                    }
                }
                AuthResult.Success(profile.copy(deviceId = currentDeviceId))
            } else if (profile.deviceId != currentDeviceId) {
                // Proxy Attempt Detected: Logged in from an unapproved phone
                auth.signOut()
                AuthResult.DeviceMismatch(
                    "Security Violation: This account is locked to another physical phone. Proxy attempt blocked."
                )
            } else {
                // Hardware ID Matches Perfectly
                AuthResult.Success(profile)
            }
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Network or Authentication error occurred.")
        }
    }
}