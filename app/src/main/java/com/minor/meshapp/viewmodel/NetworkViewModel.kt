package com.minor.meshapp.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minor.network.NetworkInfo
import com.minor.network.NetworkInterfaceInfo
import com.minor.network.NetworkScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val networkInfo = NetworkInfo(application)

    var interfaces by mutableStateOf<List<NetworkInterfaceInfo>>(emptyList())
        private set

    val isStaApSupported = networkInfo.isStaApSupported()
    val isLikelySupported = networkInfo.isLikelySupported()

    init {
        viewModelScope.launch {
            while (true) {
                interfaces = NetworkScanner.getNetworkInterfaceInfo()
                delay(2000.milliseconds)
            }
        }
    }
}