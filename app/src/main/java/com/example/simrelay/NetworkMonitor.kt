package com.example.simrelay

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

class NetworkMonitor(
    context: Context,
    private val onNetworkChanged: (String?) -> Unit
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var lastIp: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val currentIp = ServerManager.getLocalIpAddress()
            Log.d("NetworkMonitor", "Network available: $network, IP: $currentIp")
            if (currentIp != lastIp) {
                lastIp = currentIp
                onNetworkChanged(currentIp)
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d("NetworkMonitor", "Network lost: $network")
            if (lastIp != null) {
                lastIp = null
                onNetworkChanged(null)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val currentIp = ServerManager.getLocalIpAddress()
                Log.d("NetworkMonitor", "WiFi capabilities changed, IP: $currentIp")
                if (currentIp != lastIp) {
                    lastIp = currentIp
                    onNetworkChanged(currentIp)
                }
            }
        }
    }

    fun start() {
        lastIp = ServerManager.getLocalIpAddress()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
