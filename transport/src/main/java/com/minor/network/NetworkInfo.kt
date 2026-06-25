package com.minor.network

import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build

class NetworkInfo(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * Checks if the device supports Station (STA) and Access Point (AP) concurrency.
     * This allows the device to be connected to a Wi-Fi network while simultaneously
     * acting as a hotspot.
     */
    fun isStaApSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wifiManager.isStaApConcurrencySupported
        } else {
            false
        }
    }

    /**
     * For older devices (pre-Android 11), checks if concurrency is likely supported
     * based on hardware features like WiFi Direct.
     */
    fun isLikelySupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            isStaApSupported()
        } else {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        }
    }
}
