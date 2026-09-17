package com.example.beaconpass.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime

object SupabaseNetworkClient {

    private const val SUPABASE_URL = "https://pjsafgknjnmobgucyaon.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_secret_cjujOQ0ISOPF5Nh_6igsAg_tWEzRAWM"

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    val auth: Auth get() = client.auth
    val postgrest: Postgrest get() = client.postgrest
    val realtime: Realtime get() = client.realtime
}