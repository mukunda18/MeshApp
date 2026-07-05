package com.minor.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.minor.meshcontrol.MeshService

class HomeViewModelFactory(
    private val application: Application,
    private val meshService: MeshService,
    private val meshController: MeshController,
    private val appName: String,
    private val deviceName: String,
    private val nodeId: String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                application = application,
                meshService = meshService,
                meshController = meshController,
                appName = appName,
                deviceName = deviceName,
                nodeId = nodeId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}