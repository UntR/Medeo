package com.untr.medeo.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkSnapshot(
    val online: Boolean,
    val wifiLike: Boolean
) {
    companion object {
        val Offline = NetworkSnapshot(online = false, wifiLike = false)
    }
}

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun snapshot(): NetworkSnapshot {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return NetworkSnapshot.Offline
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkSnapshot.Offline
        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val wifiLike = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return NetworkSnapshot(
            online = online,
            wifiLike = online && wifiLike
        )
    }
}
