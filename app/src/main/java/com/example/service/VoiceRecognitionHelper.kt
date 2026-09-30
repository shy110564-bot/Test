package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceRecognitionHelper(
    private val context: Context,
    private val onAudioLevel: (Float) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit,
    private val onPartialResult: ((String) -> Unit)? = null,
    private val isMutedProvider: () -> Boolean,
    private val isPowerOnlineProvider: () -> Boolean = { true },
    private val onFallbackRequested: (() -> Unit)? = null,
    private val onUserBargeIn: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "VoiceRecognitionHelper"
        // RULE #1 — SUNNE KA TAREEKA:
        // User ki baat PEHLE PURI suno — beech mein mat kaato.
        // Sunne ke baad 1 second (1000ms) ka pause lo (sochne ke liye), phir bolo.
        private const val PARTIAL_SILENCE_FINALIZE_MS = 2200L
        private const val END_OF_SPEECH_FINALIZE_MS = 1000L
        private const val TTS_SAFETY_UNLOCK_MS = 15000L

        private val HIGH_PRIORITY_COMMAND_TOKENS = listOf(
            "jarvis", "jaan", "baby", "shona", "sun na", "jarv", "youtube", "whatsapp", "instagram", "chrome", "google", "spotify", "gaana",
            "telegram", "camera", "calculator", "settings", "flashlight", "torch", "alarm", "timer", "battery",
            "kholo", "khol", "open", "chalao", "search", "play", "message", "bhejo", "lagao",
            "call", "screen share", "reels", "volume", "awaaz", "aawaz", "ji",
            "जार्विस", "यूट्यूब", "व्हाट्सएप", "इंस्टाग्राम", "क्रोम", "गूगल", "कैमरा", "खोलो", "चलाओ", "सर्च"
        )

        private val COMMON_ASR_CORRECTIONS = listOf(
            Regex("\\b(what's up|whats up|what sup|whatsap|watsapp|watts app)\\b", RegexOption.IGNORE_CASE) to "WhatsApp",
            Regex("\\b(you tube|u tube|utube)\\b", RegexOption.IGNORE_CASE) to "YouTube",
            Regex("\\b(insta gram|in stagram)\\b", RegexOption.IGNORE_CASE) to "Instagram",
            Regex("\\b(jar vis|charvis|garvis|javis|jarvice|jarviz)\\b", RegexOption.IGNORE_CASE) to "Jarvis",
            Regex("\\b(colo|kolo|call lo|hollow)\\b", RegexOption.IGNORE_CASE) to "kholo"
        )
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isContinuousListening = false
    private var isListening = false
    @Volatile
    private var isTtsCurrentlySpeaking = false
    private var lastTtsSpokenText = ""
    private var isStarting = false
    private var latestPartialText = ""
    private var hasDispatchedCurrentSession = false

    private val restartRunnable = Runnable {
        if (isContinuousListening && !isMutedProvider() && isPowerOnlineProvider() && !isTtsCurrentlySpeaking) {
            startListeningInternal()
        }
    }

    // Watchdog so if Android TTS ever drops onDone (e.g. when launching external apps), mic never stays locked
    private val ttsSafetyUnlockRunnable = Runnable {
        if (isTtsCurrentlySpeaking) {
            Log.w(TAG, "TTS safety watchdog triggered — unlocking microphone listener")
            isTtsCurrentlySpeaking = false
            if (isContinuousListening && !isMutedProvider() && isPowerOnlineProvider() && !isListening && !isStarting) {
                scheduleRestart(100L)
            }
        }
    }

    private val partialFinalizeRunnable = Runnable {
        val candidate = normalizeTranscript(latestPartialText.trim())
        if (candidate.isNotBlank() && !hasDispatchedCurrentSession && !isTtsCurrentlySpeaking) {
            hasDispatchedCurrentSession = true
            latestPartialText = ""
            isStarting = false
            isListening = false
            isTtsCurrentlySpeaking = true
            mainHandler.removeCallbacks(ttsSafetyUnlockRunnable)
            mainHandler.postDelayed(ttsSafetyUnlockRunnable, TTS_SAFETY_UNLOCK_MS)
            onListeningStateChanged(false)
            cancelSession()
            onResult(candidate)
        }
    }

    fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }
    }

    fun startContinuousListening() {
        isContinuousListening = true
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.removeCallbacks(partialFinalizeRunnable)
        if (!isTtsCurrentlySpeaking) {
            startListeningInternal()
        }
    }

    fun startListening() {
        isTtsCurrentlySpeaking = false
        mainHandler.removeCallbacks(ttsSafetyUnlockRunnable)
        startContinuousListening()
    }

    /**
     * Notifies the voice helper when TTS starts or finishes speaking.
     * Includes a safety watchdog so the microphone never gets stuck if an external app takes focus.
     */
    fun notifyTtsSpeaking(speaking: Boolean, spokenText: String = "") {
        isTtsCurrentlySpeaking = speaking
        mainHandler.removeCallbacks(ttsSafetyUnlockRunnable)
        if (speaking) {
            if (spokenText.isNotBlank()) {
                lastTtsSpokenText = spokenText
            }
            mainHandler.removeCallbacks(restartRunnable)
            mainHandler.removeCallbacks(partialFinalizeRunnable)
            latestPartialText = ""
            cancelSession()
            mainHandler.postDelayed(ttsSafetyUnlockRunnable, TTS_SAFETY_UNLOCK_MS)
        } else {
            if (isContinuousListening && !isMutedProvider() && isPowerOnlineProvider() && !isListening && !isStarting) {
                scheduleRestart(90L)
            }
        }
    }

    fun pauseForSpeech() {
        notifyTtsSpeaking(true)
    }

    fun resumeAfterSpeech() {
        notifyTtsSpeaking(false)
    }

    private fun scheduleRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartRunnable)
        if (isContinuousListening && !isMutedProvider() && isPowerOnlineProvider() && !isTtsCurrentlySpeaking) {
            mainHandler.postDelayed(restartRunnable, delayMs)
        }
    }

    private fun stopRecognizerCleanly() {
        mainHandler.removeCallbacks(partialFinalizeRunnable)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
        isStarting = false
    }

    private fun cancelSession() {
        mainHandler.removeCallbacks(partialFinalizeRunnable)
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        isListening = false
        isStarting = false
    }

    /**
     * Chooses the most accurate recognition hypothesis from Android SpeechRecognizer's top N candidates
     * and fixes common Hinglish ASR misrecognitions (like "what's up kholo" -> "WhatsApp kholo").
     */
    private fun selectBestTranscriptCandidate(matches: List<String>?): String {
        if (matches.isNullOrEmpty()) return normalizeTranscript(latestPartialText)
        val cleanedList = matches.map { normalizeTranscript(it) }.filter { it.isNotBlank() }
        if (cleanedList.isEmpty()) return normalizeTranscript(latestPartialText)

        // Score each candidate: index 0 has natural acoustic priority, but boost candidates that
        // contain clear command/app tokens or match the user's partial transcript length.
        var bestCandidate = cleanedList[0]
        var bestScore = -100

        for ((index, candidate) in cleanedList.withIndex()) {
            val lower = candidate.lowercase(Locale.ROOT)
            var score = (10 - index * 2) // Base rank score: 10, 8, 6, 4, 2
            for (token in HIGH_PRIORITY_COMMAND_TOKENS) {
                if (lower.contains(token)) {
                    score += 5
                }
            }
            // Prefer fuller utterances over accidental 1-2 char fragments
            if (candidate.length >= 4) score += 2
            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        // If the final result accidentally truncated the beginning/end that was clearly present in partial
        val normPartial = normalizeTranscript(latestPartialText)
        if (normPartial.length > bestCandidate.length + 6 && normPartial.contains(bestCandidate, ignoreCase = true)) {
            return normPartial
        }

        return bestCandidate
    }

    private fun normalizeTranscript(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        for ((pattern, replacement) in COMMON_ASR_CORRECTIONS) {
            s = s.replace(pattern, replacement)
        }
        return s.replace(Regex("\\s+"), " ").trim()
    }

    private fun startListeningInternal() {
        mainHandler.post {
            if (isMutedProvider() || !isPowerOnlineProvider() || isTtsCurrentlySpeaking) {
                isListening = false
                if (!isTtsCurrentlySpeaking) {
                    onListeningStateChanged(false)
                }
                return@post
            }

            if (isStarting || isListening) return@post
            isStarting = true
            latestPartialText = ""
            hasDispatchedCurrentSession = false

            try {
                if (!isRecognitionAvailable()) {
                    Log.w(TAG, "SpeechRecognizer not directly available on device")
                    isStarting = false
                    isContinuousListening = false
                    onError("Speech recognition not available on this system.")
                    return@post
                }

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                isStarting = false
                                isListening = true
                                latestPartialText = ""
                                hasDispatchedCurrentSession = false
                                if (!isTtsCurrentlySpeaking) {
                                    onListeningStateChanged(true)
                                }
                            }

                            override fun onBeginningOfSpeech() {
                                isListening = true
                                mainHandler.removeCallbacks(partialFinalizeRunnable)
                            }

                            override fun onRmsChanged(rmsdB: Float) {
                                val normalized = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
                                onAudioLevel(normalized)
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                isListening = false
                                onListeningStateChanged(false)
                                // Wait END_OF_SPEECH_FINALIZE_MS (950ms) so Android's accurate onResults() arrives first!
                                if (latestPartialText.isNotBlank() && !hasDispatchedCurrentSession && !isTtsCurrentlySpeaking) {
                                    mainHandler.removeCallbacks(partialFinalizeRunnable)
                                    mainHandler.postDelayed(partialFinalizeRunnable, END_OF_SPEECH_FINALIZE_MS)
                                }
                            }

                            override fun onError(error: Int) {
                                mainHandler.removeCallbacks(partialFinalizeRunnable)
                                isStarting = false
                                isListening = false
                                if (!isTtsCurrentlySpeaking) {
                                    onListeningStateChanged(false)
                                }

                                val fallbackPartial = normalizeTranscript(latestPartialText)
                                if (fallbackPartial.isNotBlank() && !hasDispatchedCurrentSession && !isTtsCurrentlySpeaking) {
                                    hasDispatchedCurrentSession = true
                                    latestPartialText = ""
                                    isTtsCurrentlySpeaking = true
                                    mainHandler.removeCallbacks(ttsSafetyUnlockRunnable)
                                    mainHandler.postDelayed(ttsSafetyUnlockRunnable, TTS_SAFETY_UNLOCK_MS)
                                    cancelSession()
                                    onResult(fallbackPartial)
                                    return
                                }

                                if (isTtsCurrentlySpeaking) {
                                    return
                                }

                                when (error) {
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                                    SpeechRecognizer.ERROR_NO_MATCH -> {
                                        cancelSession()
                                        scheduleRestart(120L)
                                    }
                                    SpeechRecognizer.ERROR_AUDIO,
                                    SpeechRecognizer.ERROR_CLIENT,
                                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                        stopRecognizerCleanly()
                                        scheduleRestart(250L)
                                    }
                                    SpeechRecognizer.ERROR_NETWORK,
                                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                        cancelSession()
                                        scheduleRestart(400L)
                                    }
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                        stopRecognizerCleanly()
                                        isContinuousListening = false
                                        onError("Microphone permission required")
                                    }
                                    else -> {
                                        cancelSession()
                                        scheduleRestart(220L)
                                    }
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                mainHandler.removeCallbacks(partialFinalizeRunnable)
                                isStarting = false
                                isListening = false
                                if (!isTtsCurrentlySpeaking) {
                                    onListeningStateChanged(false)
                                }

                                if (hasDispatchedCurrentSession || isTtsCurrentlySpeaking) {
                                    return
                                }

                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = selectBestTranscriptCandidate(matches)
                                latestPartialText = ""

                                cancelSession()

                                if (text.isNotBlank()) {
                                    hasDispatchedCurrentSession = true
                                    isTtsCurrentlySpeaking = true
                                    mainHandler.removeCallbacks(ttsSafetyUnlockRunnable)
                                    mainHandler.postDelayed(ttsSafetyUnlockRunnable, TTS_SAFETY_UNLOCK_MS)
                                    onResult(text)
                                } else {
                                    scheduleRestart(120L)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                if (isTtsCurrentlySpeaking || hasDispatchedCurrentSession) return
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val partialText = matches?.firstOrNull()?.trim() ?: ""
                                if (partialText.isNotBlank()) {
                                    latestPartialText = partialText
                                    onPartialResult?.invoke(normalizeTranscript(partialText))
                                    mainHandler.removeCallbacks(partialFinalizeRunnable)
                                    mainHandler.postDelayed(partialFinalizeRunnable, PARTIAL_SILENCE_FINALIZE_MS)
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN", "hi", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1250L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1050L)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isStarting = false
                isListening = false
                onListeningStateChanged(false)
                Log.e(TAG, "Exception in speech startListening: ${e.message}")
                stopRecognizerCleanly()
                scheduleRestart(250L)
            }
        }
    }

    fun stopListening() {
        isContinuousListening = false
        latestPartialText = ""
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.removeCallbacks(partialFinalizeRunnable)
        mainHandler.removeCallbacks(ttsSafetyUnlockRunnable)
        mainHandler.post {
            stopRecognizerCleanly()
            onListeningStateChanged(false)
        }
    }

    fun isCurrentlyListening(): Boolean = isListening
}
