package com.example.simrelay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serviceName: String = "SimRelay"

    interface DiscoveryListener {
        fun onServiceFound(name: String, host: String, port: Int)
        fun onServiceLost(name: String)
    }

    private var externalDiscoveryListener: DiscoveryListener? = null

    fun registerService(port: Int) {
        unregisterService() // Ensure old service is gone

        val serviceInfo = NsdServiceInfo().apply {
            serviceType = "_simrelay._tcp"
            this.serviceName = this@NsdHelper.serviceName
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                serviceName = NsdServiceInfo.serviceName
                Log.d("NsdHelper", "Service registered: $serviceName")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d("NsdHelper", "Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NsdHelper", "Unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e("NsdHelper", "Error registering service", e)
        }
    }

    fun unregisterService() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e("NsdHelper", "Error unregistering service", e)
            }
            registrationListener = null
        }
    }

    fun startDiscovery(listener: DiscoveryListener) {
        stopDiscovery()
        this.externalDiscoveryListener = listener

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NsdHelper", "Discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NsdHelper", "Service found: ${service.serviceName}")
                // Filter for our service type
                if (service.serviceType.contains("_simrelay")) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.e("NsdHelper", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            Log.d("NsdHelper", "Service resolved: ${info.host.hostAddress}:${info.port}")
                            externalDiscoveryListener?.onServiceFound(
                                info.serviceName,
                                info.host.hostAddress ?: "unknown",
                                info.port
                            )
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d("NsdHelper", "Service lost: ${service.serviceName}")
                externalDiscoveryListener?.onServiceLost(service.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("NsdHelper", "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Discovery start failed: $errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NsdHelper", "Discovery stop failed: $errorCode")
                stopDiscovery()
            }
        }

        try {
            nsdManager.discoverServices("_simrelay._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e("NsdHelper", "Error starting discovery", e)
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e("NsdHelper", "Error stopping discovery", e)
            }
            discoveryListener = null
        }
        externalDiscoveryListener = null
    }
}
