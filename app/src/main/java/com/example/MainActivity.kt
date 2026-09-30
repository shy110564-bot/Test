package com.example

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.data.AppDatabase
import com.example.data.JarvisPreferences
import com.example.data.MemoryEntity
import com.example.service.BorderlightOverlayService
import com.example.service.GeminiService
import com.example.service.JarvisAccessibilityService
import com.example.service.JarvisBackgroundService
import com.example.service.JarvisMindEngine
import com.example.service.JarvisNotificationListenerService
import com.example.service.JarvisServiceListener
import com.example.service.TtsService
import com.example.service.VoiceRecognitionHelper
import com.example.ui.ChatMessageItem
import com.example.ui.JarvisMainScreen
import com.example.ui.JarvisUiState
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        @Volatile
        var instance: MainActivity? = null
            private set
    }

    private lateinit var prefs: JarvisPreferences
    private lateinit var database: AppDatabase
    private lateinit var geminiService: GeminiService
    private lateinit var ttsService: TtsService
    private lateinit var voiceHelper: VoiceRecognitionHelper
    private lateinit var mindEngine: JarvisMindEngine

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRequestingSequentialAll = false

    private var uiState by mutableStateOf(JarvisUiState())

    private val serviceListener = object : JarvisServiceListener {
        override fun onOrbStateChanged(state: String, level: Float) {
            runOnUiThread {
                if (state == "speaking" || state == "thinking") {
                    voiceHelper.pauseForSpeech()
                }
                uiState = uiState.copy(
                    orbState = state,
                    audioLevel = level,
                    partialTranscript = if (state != "listening") "" else uiState.partialTranscript,
                    isScreenShareActive = JarvisAccessibilityService.isAiScreenShareActive,
                    screenShareSummary = JarvisAccessibilityService.getScreenShareBannerText()
                )
            }
        }

        override fun onAudioLevel(level: Float) {
            runOnUiThread {
                uiState = uiState.copy(audioLevel = level)
            }
        }

        override fun onUserQuery(query: String, timeStr: String) {
            runOnUiThread {
                appendMessage("you", query, timeStr)
                uiState = uiState.copy(partialTranscript = "")
            }
        }

        override fun onJarvisResponse(response: String, timeStr: String) {
            runOnUiThread {
                appendMessage("jarvis", response, timeStr)
                uiState = uiState.copy(
                    isScreenShareActive = JarvisAccessibilityService.isAiScreenShareActive,
                    screenShareSummary = JarvisAccessibilityService.getScreenShareBannerText()
                )
            }
        }

        override fun onPartialTranscript(text: String) {
            runOnUiThread {
                uiState = uiState.copy(partialTranscript = text)
            }
        }

        override fun onMicMutedChanged(isMuted: Boolean) {
            runOnUiThread {
                uiState = uiState.copy(isMicMuted = isMuted)
            }
        }

        override fun onPowerChanged(isOnline: Boolean) {
            runOnUiThread {
                uiState = uiState.copy(isPowerOnline = isOnline)
            }
        }

        override fun onSpeechError(message: String) {
            runOnUiThread {
                uiState = uiState.copy(orbState = "idle", audioLevel = 0f)
            }
        }
    }

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Microphone enabled", Toast.LENGTH_SHORT).show()
            val service = JarvisBackgroundService.instance
            if (service != null) {
                voiceHelper.stopListening()
                service.setMicMuted(false)
                service.onMicrophonePermissionGranted()
            } else {
                JarvisBackgroundService.startService(this)
            }
        } else {
            Toast.makeText(this, "Microphone permission is required for voice commands", Toast.LENGTH_LONG).show()
            uiState = uiState.copy(orbState = "idle", audioLevel = 0f)
        }
        refreshUiPermissionsAndConfig()
    }

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshUiPermissionsAndConfig()
        if (isRequestingSequentialAll) {
            isRequestingSequentialAll = false
            mainHandler.postDelayed({
                checkAndPromptNextSpecialPermission()
            }, 500)
        }
    }

    private val speechFallbackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenMatches?.firstOrNull()?.trim() ?: ""
            if (spokenText.isNotBlank()) {
                handleUserSpeechQuery(spokenText)
            } else {
                uiState = uiState.copy(orbState = "idle", audioLevel = 0f)
            }
        } else {
            uiState = uiState.copy(orbState = "idle", audioLevel = 0f)
        }
    }

    fun attachBackgroundService(service: JarvisBackgroundService) {
        runOnUiThread {
            // Stop local Activity recognizer so ONLY JarvisBackgroundService holds the microphone
            voiceHelper.stopListening()
            service.setUiListener(serviceListener)
            if (!prefs.isMicMuted && prefs.isPowerOnline && hasAudioPermission()) {
                service.voiceHelper.startContinuousListening()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        instance = this
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        prefs = JarvisPreferences(this)
        database = AppDatabase.getDatabase(this)

        JarvisBackgroundService.startService(this)

        geminiService = GeminiService(
            context = this,
            onVoiceChanged = { newVoice ->
                runOnUiThread {
                    ttsService.applyVoiceProfile(newVoice, prefs.voiceSpeed, prefs.voicePitch)
                    JarvisBackgroundService.instance?.ttsService?.applyVoiceProfile(newVoice, prefs.voiceSpeed, prefs.voicePitch)
                    refreshUiPermissionsAndConfig()
                }
            },
            onMuteToggled = { isMuted ->
                runOnUiThread {
                    uiState = uiState.copy(isMicMuted = isMuted)
                }
            }
        )

        ttsService = TtsService(this) { isSpeaking ->
            runOnUiThread {
                if (isSpeaking) {
                    uiState = uiState.copy(orbState = "speaking", audioLevel = 0.7f)
                    voiceHelper.pauseForSpeech()
                    JarvisBackgroundService.instance?.voiceHelper?.pauseForSpeech()
                } else {
                    uiState = uiState.copy(orbState = "idle", audioLevel = 0f)
                    val bgService = JarvisBackgroundService.instance
                    if (bgService != null) {
                        bgService.voiceHelper.resumeAfterSpeech()
                    } else {
                        voiceHelper.resumeAfterSpeech()
                    }
                }
            }
        }
        ttsService.applyVoiceProfile(prefs.voice, prefs.voiceSpeed, prefs.voicePitch)
        prefs.isMicMuted = false
        prefs.isPowerOnline = true

        mindEngine = JarvisMindEngine(
            context = this,
            ttsService = ttsService,
            appManagerHelper = geminiService.appManager,
            onThoughtGenerated = { thought, speakAloud ->
                runOnUiThread {
                    val timeNow = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
                    appendMessage("jarvis", thought, timeNow)
                    if (speakAloud && prefs.isPowerOnline && !prefs.isMicMuted) {
                        speakWithSingleEngine(thought)
                    }
                }
            }
        )

        voiceHelper = VoiceRecognitionHelper(
            context = this,
            onAudioLevel = { level ->
                runOnUiThread {
                    uiState = uiState.copy(audioLevel = level)
                }
            },
            onResult = { query ->
                runOnUiThread {
                    handleUserSpeechQuery(query)
                }
            },
            onError = { _ ->
                runOnUiThread {
                    uiState = uiState.copy(orbState = "idle", audioLevel = 0f)
                }
            },
            onListeningStateChanged = { isListening ->
                runOnUiThread {
                    if (isListening) {
                        uiState = uiState.copy(orbState = "listening", audioLevel = 0.5f)
                    }
                }
            },
            onPartialResult = { partialText ->
                runOnUiThread {
                    uiState = uiState.copy(partialTranscript = partialText)
                }
            },
            isMutedProvider = { prefs.isMicMuted },
            isPowerOnlineProvider = { prefs.isPowerOnline },
            onFallbackRequested = {
                runOnUiThread {
                    launchSpeechFallbackDialog()
                }
            },
            onUserBargeIn = {
                runOnUiThread {
                    stopAllSpeakingImmediately()
                }
            }
        )

        refreshUiPermissionsAndConfig()
        syncRecentChatHistory()

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                JarvisMainScreen(
                    uiState = uiState,
                    onOrbTap = { toggleVoiceInput() },
                    onStopSpeaking = { stopAllSpeakingImmediately() },
                    onToggleMute = { handleMicOrMuteClick() },
                    onTogglePower = { togglePower() },
                    onToggleScreenShare = { toggleAiScreenShare() },
                    onSendCommand = { cmd -> handleUserSpeechQuery(cmd) },
                    onSelectVoice = { voiceName ->
                        prefs.voice = voiceName
                        ttsService.applyVoiceProfile(voiceName, prefs.voiceSpeed, prefs.voicePitch)
                        JarvisBackgroundService.instance?.ttsService?.applyVoiceProfile(voiceName, prefs.voiceSpeed, prefs.voicePitch)
                        refreshUiPermissionsAndConfig()
                    },
                    onToggleGfMode = { enabled ->
                        prefs.setGirlfriendMode(enabled)
                        val activeV = prefs.voice
                        ttsService.applyVoiceProfile(activeV, prefs.voiceSpeed, prefs.voicePitch)
                        JarvisBackgroundService.instance?.ttsService?.applyVoiceProfile(activeV, prefs.voiceSpeed, prefs.voicePitch)
                        JarvisBackgroundService.instance?.updateNotification()
                        refreshUiPermissionsAndConfig()
                        val msg = if (enabled) {
                            "Ji… (soft) romantic mode on ho gaya jaan… main aapki JARVIS hun. Sun na… ji bataiye baby, kya karna hai 💕"
                        } else {
                            "Ji… main aapki JARVIS active hun."
                        }
                        speakFromUI(msg)
                    },
                    onTestVoice = { testVoiceOutput() },
                    onSpeakMessage = { text ->
                        speakWithSingleEngine(text)
                    },
                    onClearChat = { clearChatHistory() },
                    onOpenSettings = {
                        refreshUiPermissionsAndConfig()
                        uiState = uiState.copy(currentScreen = "settings")
                    },
                    onCloseSettings = {
                        uiState = uiState.copy(currentScreen = "home")
                    },
                    onSaveSettings = { apiKey, model, voice, speed, pitch, edgeLight ->
                        prefs.apiKey = apiKey
                        prefs.model = model
                        prefs.voice = voice
                        prefs.voiceSpeed = speed
                        prefs.voicePitch = pitch
                        prefs.isEdgeLightingEnabled = edgeLight
                        ttsService.applyVoiceProfile(voice, speed, pitch)
                        JarvisBackgroundService.instance?.ttsService?.applyVoiceProfile(voice, speed, pitch)
                        if (edgeLight && Settings.canDrawOverlays(this)) {
                            BorderlightOverlayService.start(this)
                        } else if (!edgeLight) {
                            BorderlightOverlayService.stop(this)
                        }
                        refreshUiPermissionsAndConfig()
                        Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
                    },
                    onPasteApiKey = { pasteKeyFromClipboard() },
                    onPreviewVoice = { profile, speed, pitch ->
                        voiceHelper.pauseForSpeech()
                        JarvisBackgroundService.instance?.voiceHelper?.pauseForSpeech()
                        val activeTts = JarvisBackgroundService.instance?.ttsService ?: ttsService
                        activeTts.previewVoiceProfile(profile, speed, pitch)
                    },
                    onRequestPermission = { perm -> requestSpecificPermission(perm) },
                    onRequestAllPermissions = { requestAllPermissions() },
                    onOpenTelegram = { openTelegramChannel() },
                    onOpenHistoryDialog = { showHistoryDialog() }
                )
            }
        }

        if (!hasAudioPermission()) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun speakWithSingleEngine(text: String) {
        if (text.isBlank()) return
        voiceHelper.notifyTtsSpeaking(true, text)
        val service = JarvisBackgroundService.instance
        if (service != null) {
            ttsService.stop()
            service.voiceHelper.notifyTtsSpeaking(true, text)
            service.ttsService.speak(text)
        } else {
            ttsService.speak(text)
        }
    }

    private fun toggleAiScreenShare() {
        val currentlyActive = JarvisAccessibilityService.isAiScreenShareActive
        if (currentlyActive) {
            JarvisAccessibilityService.isAiScreenShareActive = false
            JarvisAccessibilityService.screenSharePartnerName = ""
            JarvisAccessibilityService.latestScreenSummary = "Screen share band kar diya"
            JarvisBackgroundService.instance?.updateNotification()
            uiState = uiState.copy(
                isScreenShareActive = false,
                screenShareSummary = "Screen share band kar diya"
            )
            speakFromUI("Screen share band kar diya")
            return
        }

        // Rule 4: "Permission maango pehle — privacy important hai"
        if (!JarvisAccessibilityService.isScreenSharePrivacyApproved) {
            AlertDialog.Builder(this)
                .setTitle("Screen Share Privacy Permission")
                .setMessage("Jaan, privacy important hai. Kya aap apni screen share karna chahte hain? Incoming notifications chhupaye nahi jayenge.")
                .setPositiveButton("Allow & Share") { _, _ ->
                    JarvisAccessibilityService.isScreenSharePrivacyApproved = true
                    activateScreenShareWithPartner("Rahul")
                }
                .setNegativeButton("Cancel") { _, _ ->
                    speakFromUI("Theek hai jaan, screen share nahi chalu kiya 💕")
                }
                .show()
            return
        }

        activateScreenShareWithPartner(JarvisAccessibilityService.screenSharePartnerName.ifBlank { "Rahul" })
    }

    private fun activateScreenShareWithPartner(partnerName: String) {
        val cleanPartner = partnerName.trim().ifBlank { "Rahul" }
        JarvisAccessibilityService.isAiScreenShareActive = true
        JarvisAccessibilityService.screenSharePartnerName = cleanPartner
        val banner = "Screen share ON hai, $cleanPartner dekh rahe hain"
        JarvisAccessibilityService.latestScreenSummary = banner
        JarvisBackgroundService.instance?.updateNotification()

        uiState = uiState.copy(
            isScreenShareActive = true,
            screenShareSummary = banner
        )
        speakFromUI("$cleanPartner ko call laga rahi hun, screen share ke saath. Ho gaya, $cleanPartner ab tumhari screen dekh sakte hain")
    }

    private fun stopAllSpeakingImmediately() {
        ttsService.stop()
        JarvisBackgroundService.instance?.ttsService?.stop()
        uiState = uiState.copy(orbState = "listening", audioLevel = 0.5f)
        val service = JarvisBackgroundService.instance
        if (service != null && hasAudioPermission() && !prefs.isMicMuted && prefs.isPowerOnline) {
            voiceHelper.stopListening()
            service.voiceHelper.notifyTtsSpeaking(false)
            service.voiceHelper.startContinuousListening()
        } else if (hasAudioPermission() && !prefs.isMicMuted && prefs.isPowerOnline) {
            voiceHelper.notifyTtsSpeaking(false)
            voiceHelper.startContinuousListening()
        }
    }

    private fun appendMessage(role: String, text: String, timeStr: String) {
        if (text.isBlank()) return
        val updated = (uiState.messages + ChatMessageItem(role = role, text = text, time = timeStr)).takeLast(60)
        uiState = uiState.copy(messages = updated)
    }

    fun handleMicOrMuteClick() {
        if (!prefs.isPowerOnline) {
            Toast.makeText(this, "Jarvis is in Sleep mode. Tap Power to activate.", Toast.LENGTH_SHORT).show()
            return
        }

        val service = JarvisBackgroundService.instance
        if (service != null) {
            voiceHelper.stopListening()
            service.toggleMute()
            val isMuted = prefs.isMicMuted
            uiState = uiState.copy(isMicMuted = isMuted)
            Toast.makeText(
                this,
                if (isMuted) "Microphone MUTED" else "Microphone UNMUTED (Listening continuously)",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (prefs.isMicMuted) {
            prefs.isMicMuted = false
            uiState = uiState.copy(isMicMuted = false)
            Toast.makeText(this, "Microphone UNMUTED (Listening continuously)", Toast.LENGTH_SHORT).show()
            if (hasAudioPermission()) {
                ttsService.stop()
                voiceHelper.startContinuousListening()
            } else {
                requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            prefs.isMicMuted = true
            voiceHelper.stopListening()
            ttsService.stop()
            uiState = uiState.copy(isMicMuted = true, orbState = "idle", audioLevel = 0f)
            Toast.makeText(this, "Microphone MUTED", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleMute() {
        handleMicOrMuteClick()
    }

    private fun launchSpeechFallbackDialog() {
        try {
            val userLang = Locale.getDefault().toLanguageTag().ifBlank { "en-IN" }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Boliye Jarvis...")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, userLang)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, userLang)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "en-US", "hi"))
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechFallbackLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Use the command bar below to type or tap quick actions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openTelegramChannel() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+R9EwUE03GRswZDM9")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Opening Telegram channel...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openScreenControlSettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "Enable 'Jarvis AI' for Live Screen Share & 100% Screen Control", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Please open Accessibility Settings manually", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        if (JarvisAccessibilityService.isRunning() && !JarvisAccessibilityService.isAiScreenShareActive) {
            // Automatically activate AI Screen Share when Accessibility Service is connected
            JarvisAccessibilityService.isAiScreenShareActive = true
            JarvisAccessibilityService.instance?.inspectFullScreenState()
        }
        refreshUiPermissionsAndConfig()
        val service = JarvisBackgroundService.instance
        if (service != null) {
            attachBackgroundService(service)
        } else {
            JarvisBackgroundService.startService(this)
        }
        if (prefs.isEdgeLightingEnabled && Settings.canDrawOverlays(this)) {
            BorderlightOverlayService.start(this)
        }
        syncRecentChatHistory()
    }

    override fun onPause() {
        super.onPause()
        JarvisBackgroundService.instance?.setUiListener(null)
    }

    private fun toggleVoiceInput() {
        if (!prefs.isPowerOnline) {
            togglePower()
        }
        if (prefs.isMicMuted) {
            prefs.isMicMuted = false
            JarvisBackgroundService.instance?.setMicMuted(false)
            uiState = uiState.copy(isMicMuted = false)
        }
        if (!hasAudioPermission()) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        stopAllSpeakingImmediately()
        val service = JarvisBackgroundService.instance
        if (service != null && service.voiceHelper.isRecognitionAvailable()) {
            voiceHelper.stopListening()
            service.voiceHelper.startListening()
            uiState = uiState.copy(orbState = "listening", audioLevel = 0.6f)
            Toast.makeText(this, "Listening... Boliye", Toast.LENGTH_SHORT).show()
        } else if (voiceHelper.isRecognitionAvailable()) {
            voiceHelper.startListening()
            uiState = uiState.copy(orbState = "listening", audioLevel = 0.6f)
            Toast.makeText(this, "Listening... Boliye", Toast.LENGTH_SHORT).show()
        } else {
            launchSpeechFallbackDialog()
        }
    }

    fun testVoiceOutput() {
        val msg = "Ji… main aapki JARVIS bol rahi hun 💕 Hehe… aap jo bolenge main poore dhyan se sunungi aur exact wahi karungi jaan 💕"
        val timeNow = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        appendMessage("jarvis", msg, timeNow)
        speakWithSingleEngine(msg)
        Toast.makeText(this, "Testing JARVIS Real 21-Yr-Old Girl Voice...", Toast.LENGTH_SHORT).show()
    }

    fun testWhatsAppAnnouncement() {
        val testSender = "Rohit Sharma"
        val testMessage = "Bhai kahan ho? Meeting kab shuru hogi?"
        displayIncomingNotification("WhatsApp", testSender, testMessage)
        val text = "WhatsApp par $testSender ne message kiya hai, $testMessage"
        speakWithSingleEngine(text)
    }

    private fun togglePower() {
        val service = JarvisBackgroundService.instance
        if (service != null) {
            service.togglePower()
            uiState = uiState.copy(isPowerOnline = prefs.isPowerOnline)
            Toast.makeText(
                this,
                if (prefs.isPowerOnline) "Jarvis online" else "Jarvis in sleep mode",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        prefs.isPowerOnline = !prefs.isPowerOnline
        uiState = uiState.copy(isPowerOnline = prefs.isPowerOnline)
        if (!prefs.isPowerOnline) {
            ttsService.stop()
            voiceHelper.stopListening()
            uiState = uiState.copy(orbState = "idle", audioLevel = 0f)
        }
    }

    fun speakFromUI(text: String) {
        if (text.isBlank()) return
        val timeNow = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        runOnUiThread {
            appendMessage("jarvis", text, timeNow)
            speakWithSingleEngine(text)
        }
    }

    fun displayIncomingNotification(source: String, sender: String, message: String) {
        val timeNow = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        val formatted = "[$source] $sender: $message"
        runOnUiThread {
            appendMessage("jarvis", formatted, timeNow)
        }
    }

    private fun handleUserSpeechQuery(query: String) {
        if (query.isBlank()) return
        voiceHelper.notifyTtsSpeaking(true, query)
        ttsService.stop()

        val service = JarvisBackgroundService.instance
        if (service != null) {
            service.processUserQuery(query)
            return
        }

        val timeNow = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        appendMessage("you", query, timeNow)
        uiState = uiState.copy(orbState = "thinking", audioLevel = 0.6f, partialTranscript = "")

        lifecycleScope.launch(Dispatchers.IO) {
            database.memoryDao().insertMemory(
                MemoryEntity(category = "CHAT_USER", content = query)
            )

            val response = geminiService.processQuery(query)

            database.memoryDao().insertMemory(
                MemoryEntity(category = "CHAT_JARVIS", content = response)
            )

            withContext(Dispatchers.Main) {
                appendMessage("jarvis", response, timeNow)
                uiState = uiState.copy(
                    isScreenShareActive = JarvisAccessibilityService.isAiScreenShareActive,
                    screenShareSummary = JarvisAccessibilityService.getScreenShareBannerText()
                )
                if (prefs.isPowerOnline && response.isNotBlank()) {
                    speakWithSingleEngine(response)
                } else {
                    uiState = uiState.copy(orbState = "idle", audioLevel = 0f)
                    if (!prefs.isMicMuted && prefs.isPowerOnline) {
                        voiceHelper.resumeAfterSpeech()
                    }
                }
            }
        }
    }

    fun clearChatHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            database.memoryDao().clearChatMemories()
            withContext(Dispatchers.Main) {
                uiState = uiState.copy(messages = emptyList())
                Toast.makeText(this@MainActivity, "Conversation cleared", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncRecentChatHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val recentMemories = database.memoryDao().getRecentMemories(30)
            withContext(Dispatchers.Main) {
                val loaded = recentMemories.reversed().mapNotNull { mem ->
                    if (mem.category == "CHAT_USER" || mem.category == "CHAT_JARVIS") {
                        ChatMessageItem(
                            id = mem.id,
                            role = if (mem.category == "CHAT_USER") "you" else "jarvis",
                            text = mem.content,
                            time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(mem.timestamp))
                        )
                    } else null
                }
                uiState = uiState.copy(messages = loaded)
            }
        }
    }

    private fun requestSpecificPermission(perm: String) {
        when (perm) {
            "record_audio", "mic" -> requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            "camera" -> requestAudioPermissionLauncher.launch(Manifest.permission.CAMERA)
            "phone" -> requestMultiplePermissionsLauncher.launch(
                arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CONTACTS)
            )
            "contacts" -> requestAudioPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            "location" -> requestMultiplePermissionsLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            "sms" -> requestMultiplePermissionsLauncher.launch(
                arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS)
            )
            "calendar" -> requestMultiplePermissionsLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
            "bluetooth" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestMultiplePermissionsLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                } else {
                    requestMultiplePermissionsLauncher.launch(
                        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
                    )
                }
            }
            "notifications" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestAudioPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            "accessibility" -> openScreenControlSettings()
            "whatsapp_reader" -> JarvisNotificationListenerService.openSettings(this)
            "overlay" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
            }
            "battery" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
            "settings" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        isRequestingSequentialAll = true
        requestMultiplePermissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun checkAndPromptNextSpecialPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Screen Overlay Permission")
                .setMessage("Allow Jarvis to display on top of other apps for hands-free assistance.")
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
                .setNegativeButton("Later", null)
                .show()
            return
        }

        if (!JarvisAccessibilityService.isRunning()) {
            AlertDialog.Builder(this)
                .setTitle("AI Screen Share & Control Service")
                .setMessage("Enable 'Jarvis AI' under Accessibility so Jarvis can see your screen and control clicks, scrolls, and typing.")
                .setPositiveButton("Open Settings") { _, _ ->
                    openScreenControlSettings()
                }
                .setNegativeButton("Later", null)
                .show()
        }
    }

    private fun refreshUiPermissionsAndConfig() {
        fun has(perm: String): Boolean =
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

        val isAudio = has(Manifest.permission.RECORD_AUDIO)
        val isCamera = has(Manifest.permission.CAMERA)
        val isPhone = has(Manifest.permission.CALL_PHONE)
        val isContacts = has(Manifest.permission.READ_CONTACTS)
        val isLocation = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
        val isNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            has(Manifest.permission.POST_NOTIFICATIONS)
        } else true
        val isOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        val isSettings = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.System.canWrite(this) else true
        val isAccessibility = JarvisAccessibilityService.isRunning()
        val isWhatsApp = JarvisNotificationListenerService.isPermissionGranted(this)

        val appCount = try {
            geminiService.appManager.scanInstalledApps().size
        } catch (_: Exception) {
            0
        }

        uiState = uiState.copy(
            isPowerOnline = prefs.isPowerOnline,
            isMicMuted = prefs.isMicMuted,
            isGfMode = prefs.isGfMode,
            isScreenShareActive = JarvisAccessibilityService.isAiScreenShareActive,
            screenShareSummary = JarvisAccessibilityService.getScreenShareBannerText(),
            activeVoice = prefs.voice,
            voiceSpeed = prefs.voiceSpeed,
            voicePitch = prefs.voicePitch,
            apiKey = prefs.apiKey,
            model = prefs.model,
            personality = prefs.personality,
            edgeLightingEnabled = prefs.isEdgeLightingEnabled,
            installedAppsCount = appCount,
            permAudio = isAudio,
            permCamera = isCamera,
            permPhone = isPhone,
            permContacts = isContacts,
            permLocation = isLocation,
            permNotifications = isNotif,
            permOverlay = isOverlay,
            permAccessibility = isAccessibility,
            permWhatsAppReader = isWhatsApp,
            permSettings = isSettings
        )
    }

    private fun pasteKeyFromClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val pasted = clipData.getItemAt(0).text?.toString()?.trim() ?: ""
                if (pasted.isNotBlank()) {
                    prefs.apiKey = pasted
                    refreshUiPermissionsAndConfig()
                    Toast.makeText(this, "API Key pasted & saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Could not paste from clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHistoryDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = database.memoryDao().getRecentMemories(40)
            withContext(Dispatchers.Main) {
                if (list.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No saved history yet.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                val items = list.map { "${it.category}: ${it.content}" }.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Memory & History (${list.size})")
                    .setItems(items, null)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Clear History") { _, _ -> clearChatHistory() }
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        JarvisBackgroundService.instance?.setUiListener(null)
        ttsService.shutdown()
        voiceHelper.stopListening()
    }
}
