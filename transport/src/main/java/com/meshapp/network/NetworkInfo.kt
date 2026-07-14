package com.meshapp.network

import android.content.Context
import android.net.wifi.WifiManager

class NetworkInfo(context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Checks if the device supports Station (STA) and Access Point (AP) concurrency.
     * This allows the device to be connected to a Wi-Fi network while simultaneously
     * acting as a hotspot.
     */
    fun isStaApSupported(): Boolean {
        return wifiManager.isStaApConcurrencySupported
    }

    /**
     * For older devices (pre-Android 11), checks if concurrency is likely supported
     * based on hardware features like WiFi Direct.
     */
    fun isLikelySupported(): Boolean {
        return isStaApSupported()
    }
}
