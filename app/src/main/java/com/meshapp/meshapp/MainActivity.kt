package com.meshapp.meshapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.meshapp.ui.navigation.MeshAppNavHost
import com.meshapp.ui.state.SoundFeedbackSettings
import com.meshapp.ui.theme.MeshAppTheme
import com.meshapp.ui.theme.MeshThemeMode
import com.meshapp.ui.theme.THEME_MODE_KEY
import com.meshapp.ui.theme.THEME_PREFERENCES
import com.meshapp.ui.viewmodel.ChatsViewModelFactory
import com.meshapp.ui.viewmodel.ConversationViewModelFactory
import com.meshapp.ui.viewmodel.HomeViewModelFactory
import com.meshapp.ui.viewmodel.MeshController

class MainActivity : ComponentActivity() {

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* no-op */ }

    private class AppFactories(
        val home: HomeViewModelFactory,
        val chats: ChatsViewModelFactory,
        val conversation: ConversationViewModelFactory
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+ requires runtime permission to post notifications
        // Voice simulation requires RECORD_AUDIO
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            requestPermissions.launch(permissions.toTypedArray())
        }

        val meshApp = application as MeshApplication

        // Bridges the UI toggle to the foreground service (Option 1).
        val meshController = object : MeshController {
            override fun start() = MeshForegroundService.start(applicationContext)
            override fun stop() = MeshForegroundService.stop(applicationContext)
            override fun startVoiceSim() = MeshForegroundService.startVoiceSim(applicationContext)
            override fun stopVoiceSim() = MeshForegroundService.stopVoiceSim(applicationContext)
            override val isVoiceSimActive = MeshForegroundService.isVoiceSimActive
        }

        setContent {
            val themePreferences = remember {
                getSharedPreferences(THEME_PREFERENCES, MODE_PRIVATE)
            }
            val soundPreferences = remember {
                SoundPreferences(applicationContext)
            }
            var soundSettings by remember(soundPreferences) {
                mutableStateOf(soundPreferences.load())
            }
            var themeMode by remember(themePreferences) {
                mutableStateOf(MeshThemeMode.fromPreference(themePreferences.getString(THEME_MODE_KEY, null)))
            }

            MeshAppTheme(themeMode = themeMode) {
                val isDarkTheme = when (themeMode) {
                    MeshThemeMode.System -> isSystemInDarkTheme()
                    MeshThemeMode.Light -> false
                    MeshThemeMode.Dark -> true
                }
                val systemBarBackground = MaterialTheme.colorScheme.background
                SideEffect {
                    updateSystemBars(systemBarBackground, isDarkTheme)
                }

                // The DI container is built off the main thread; await it without blocking.
                val factories by produceState<AppFactories?>(initialValue = null) {
                    try {
                        val container = meshApp.awaitContainer()
                        value = AppFactories(
                            home = HomeViewModelFactory(
                                application = meshApp,
                                meshService = container.meshService,
                                meshController = meshController,
                                voiceCallManager = container.voiceCallManager,
                                identityManager = container.identityManager,
                                nodesStore = container.nodesStore,
                                appName = getString(R.string.app_name),
                                deviceName = container.identity.name,
                                nodeId = container.identity.nodeId.toString(),
                                showFirstRunNameDialog = container.shouldShowFirstRunNameDialog
                            ),
                            chats = ChatsViewModelFactory(
                                messagingService = container.messagingService,
                                meshService = container.meshService,
                                nodesStore = container.nodesStore,
                                ownNodeId = container.identity.nodeId
                            ),
                            conversation = ConversationViewModelFactory(
                                ownNodeId = container.identity.nodeId,
                                messagingService = container.messagingService,
                                meshService = container.meshService,
                                nodesStore = container.nodesStore,
                                voiceCallManager = container.voiceCallManager,
                                fileTransferService = container.fileTransferService,
                                voiceMessageRecorder = container.voiceMessageRecorder,
                                voiceMessagePlayer = container.voiceMessagePlayer
                            )
                        )
                    } catch (e: Exception) {
                        // Handle initialization failure. Possible causes:
                        // - SQLiteException (database corruption or storage full)
                        // - KeyStoreException (device-specific hardware key issues)
                        android.util.Log.e("MainActivity", "Critical failure during app initialization", e)
                        // In a real app, we would show an error screen here.
                    }
                }

                val ready = factories
                if (ready == null) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    MeshAppNavHost(
                        homeViewModelFactory = ready.home,
                        chatsViewModelFactory = ready.chats,
                        conversationViewModelFactory = ready.conversation,
                        themeMode = themeMode,
                        soundSettings = soundSettings,
                        onSoundSettingsChanged = { selectedSettings: SoundFeedbackSettings ->
                            soundPreferences.save(selectedSettings)
                            soundSettings = selectedSettings
                            MeshForegroundService.refreshNotification(applicationContext)
                        },
                        onThemeModeChanged = { selectedMode ->
                            themePreferences.edit()
                                .putString(THEME_MODE_KEY, selectedMode.name)
                                .apply()
                            themeMode = selectedMode
                        }
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun updateSystemBars(background: Color, isDarkTheme: Boolean) {
        window.statusBarColor = background.toArgb()
        window.navigationBarColor = background.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
    }
}
