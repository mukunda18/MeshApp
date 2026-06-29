package com.minor.meshapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.minor.meshapp.ui.InterfacesScreen
import com.minor.meshapp.ui.theme.MeshAppTheme
import com.minor.ui.navigation.MeshAppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
//            MeshAppTheme {
//                Scaffold(modifier = Modifier.fillMaxSize()) { padding -> InterfacesScreen(modifier = Modifier.padding(padding))
//                }
//            }
            MeshAppNavHost()

        }
    }
}