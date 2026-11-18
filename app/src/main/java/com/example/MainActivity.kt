package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.AiRepository
import com.example.data.ai.GeminiRestClient
import com.example.data.ai.HybridMayaAiRepository
import com.example.data.ai.LocalMayaAiRepository
import com.example.data.local.ConversationStore
import com.example.data.local.PreferencesRepository
import com.example.missioncontrol.android.MayaAppCommandRouter
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserPreferences
import com.example.ui.navigation.MayaAppShell
import com.example.ui.screens.chat.ChatViewModel
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.tools.ToolsViewModel
import com.example.ui.screens.voice.VoiceViewModel
import com.example.ui.theme.MayaTheme
import com.example.util.SpeechHelper
import com.example.util.SpeechRecognizerHelper

class MainActivity : ComponentActivity() {

    private lateinit var speechHelper: SpeechHelper
    private lateinit var speechRecognizerHelper: SpeechRecognizerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        val conversationStore = ConversationStore(applicationContext)
        val preferencesRepository = PreferencesRepository(applicationContext)
        val geminiRestClient = GeminiRestClient(applicationContext)
        val aiRepository: AiRepository = HybridMayaAiRepository(
            preferencesRepository = preferencesRepository,
            localRepository = LocalMayaAiRepository(),
            geminiRestClient = geminiRestClient
        )
        speechHelper = SpeechHelper(applicationContext)
        speechRecognizerHelper = SpeechRecognizerHelper(applicationContext)

        val chatViewModel = ViewModelProvider(
            this,
            ChatViewModel.Factory(
                aiRepository = aiRepository,
                conversationStore = conversationStore,
                preferencesRepository = preferencesRepository,
                speechHelper = speechHelper,
                speechRecognizerHelper = speechRecognizerHelper
            )
        )[ChatViewModel::class.java]

        val voiceViewModel = ViewModelProvider(
            this,
            VoiceViewModel.Factory(
                aiRepository = aiRepository,
                preferencesRepository = preferencesRepository,
                speechHelper = speechHelper,
                speechRecognizerHelper = speechRecognizerHelper,
                appCommandRouter = MayaAppCommandRouter(applicationContext)
            )
        )[VoiceViewModel::class.java]

        val toolsViewModel = ViewModelProvider(
            this,
            ToolsViewModel.Factory(
                aiRepository = aiRepository,
                conversationStore = conversationStore,
                preferencesRepository = preferencesRepository
            )
        )[ToolsViewModel::class.java]

        val historyViewModel = ViewModelProvider(
            this,
            HistoryViewModel.Factory(conversationStore = conversationStore)
        )[HistoryViewModel::class.java]

        val settingsViewModel = ViewModelProvider(
            this,
            SettingsViewModel.Factory(
                preferencesRepository = preferencesRepository,
                conversationStore = conversationStore,
                geminiRestClient = geminiRestClient
            )
        )[SettingsViewModel::class.java]

        setContent {
            val prefs by settingsViewModel.preferences.collectAsStateWithLifecycle(
                initialValue = UserPreferences()
            )
            val isDark = when (prefs.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(isDark) {
                if (isDark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                    )
                }
            }
            MayaTheme(themeMode = prefs.themeMode) {
                MayaAppShell(
                    chatViewModel = chatViewModel,
                    voiceViewModel = voiceViewModel,
                    toolsViewModel = toolsViewModel,
                    historyViewModel = historyViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechHelper.isInitialized) {
            speechHelper.shutdown()
        }
        if (::speechRecognizerHelper.isInitialized) {
            speechRecognizerHelper.destroy()
        }
    }
}
