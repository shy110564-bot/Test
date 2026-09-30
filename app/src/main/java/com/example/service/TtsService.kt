package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.data.JarvisPreferences
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

class TtsService(
    private val context: Context,
    private val onStateChanged: (isSpeaking: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsService"
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

        // Comprehensive Roman Hinglish to Devanagari dictionary so Google's hi-IN neural female voice
        // pronounces entire sentences with 100% smooth, natural Indian girlfriend flow without
        // mid-sentence script switching or robotic English spelling.
        private val HINGLISH_TO_DEVANAGARI = mapOf(
            "namaste" to "नमस्ते",
            "pranam" to "प्रणाम",
            "haan" to "हाँ",
            "ha" to "हाँ",
            "ji" to "जी",
            "main" to "मैं",
            "mai" to "मैं",
            "maine" to "मैंने",
            "mera" to "मेरा",
            "meri" to "मेरी",
            "mere" to "मेरे",
            "mujhe" to "मुझे",
            "mujhko" to "मुझको",
            "mujhse" to "मुझसे",
            "hum" to "हम",
            "hamara" to "हमारा",
            "hamari" to "हमारी",
            "hamare" to "हमारे",
            "aap" to "आप",
            "aapka" to "आपका",
            "aapki" to "आपकी",
            "aapke" to "आपके",
            "aapse" to "आपसे",
            "aapko" to "आपको",
            "tum" to "तुम",
            "tumhara" to "तुम्हारा",
            "tumhari" to "तुम्हारी",
            "tumhare" to "तुम्हारे",
            "tumse" to "तुमसे",
            "tumhe" to "तुम्हें",
            "tumko" to "तुमको",
            "tujhe" to "तुझे",
            "tera" to "तेरा",
            "teri" to "तेरी",
            "tere" to "तेरे",
            "pyar" to "प्यार",
            "pyaar" to "प्यार",
            "pyara" to "प्यारा",
            "pyari" to "प्यारी",
            "pyare" to "प्यारे",
            "meethi" to "मीठी",
            "meetha" to "मीठा",
            "meethe" to "मीठे",
            "aawaz" to "आवाज़",
            "awaz" to "आवाज़",
            "avaaj" to "आवाज़",
            "jaan" to "जान",
            "jaanu" to "जानू",
            "babu" to "बाबू",
            "shona" to "शोना",
            "baby" to "बेबी",
            "aanya" to "आन्या",
            "ananya" to "अनन्या",
            "jarvis" to "जार्विस",
            "hmm" to "हम्म",
            "arre" to "अरे",
            "arey" to "अरे",
            "uff" to "उफ़",
            "hehe" to "हेहे",
            "hihi" to "हीही",
            "waise" to "वैसे",
            "pata" to "पता",
            "bataiye" to "बताइए",
            "lagau" to "लगाऊँ",
            "dikhau" to "दिखाऊँ",
            "aaunga" to "आऊँगा",
            "kaunsa" to "कौनसा",
            "kaunsi" to "कौनसी",
            "matlab" to "मतलब",
            "hoon" to "हूँ",
            "hun" to "हूँ",
            "hu" to "हूँ",
            "hai" to "है",
            "hain" to "हैं",
            "tha" to "था",
            "thi" to "थी",
            "thee" to "थी",
            "ho" to "हो",
            "hoga" to "होगा",
            "hogi" to "होगी",
            "honge" to "होंगे",
            "hota" to "होता",
            "hoti" to "होती",
            "hote" to "होते",
            "hua" to "हुआ",
            "hui" to "हुई",
            "hue" to "हुए",
            "rahi" to "रही",
            "raha" to "रहा",
            "rahe" to "रहे",
            "raho" to "रहो",
            "rahna" to "रहना",
            "rehna" to "रहना",
            "rehti" to "रहती",
            "rehta" to "रहता",
            "kar" to "कर",
            "karo" to "करो",
            "karna" to "करना",
            "karne" to "करने",
            "karti" to "करती",
            "karta" to "करता",
            "karte" to "करते",
            "karein" to "करें",
            "karke" to "करके",
            "karungi" to "करूँगी",
            "karun" to "करूँ",
            "karoon" to "करूँ",
            "karenge" to "करेंगे",
            "kiya" to "किया",
            "kiye" to "किए",
            "diya" to "दिया",
            "diye" to "दिए",
            "dungi" to "दूँगी",
            "doon" to "दूँ",
            "dun" to "दूँ",
            "dena" to "देना",
            "deti" to "देती",
            "deta" to "देता",
            "liya" to "लिया",
            "liye" to "लिए",
            "lo" to "लो",
            "lena" to "लेना",
            "gaya" to "गया",
            "gayi" to "गई",
            "gaye" to "गए",
            "jao" to "जाओ",
            "jana" to "जाना",
            "jaata" to "जाता",
            "jaati" to "जाती",
            "jaungi" to "जाऊँगी",
            "aao" to "आओ",
            "aaya" to "आया",
            "aayi" to "आई",
            "aaye" to "आए",
            "aata" to "आता",
            "aati" to "आती",
            "aate" to "आते",
            "aayega" to "आएगा",
            "aayegi" to "आएगी",
            "khol" to "खोल",
            "kholo" to "खोलो",
            "kholna" to "खोलना",
            "khola" to "खोला",
            "chalu" to "चालू",
            "chalao" to "चलाओ",
            "chala" to "चला",
            "chal" to "चल",
            "chalo" to "चलो",
            "chalna" to "चलना",
            "chalaungi" to "चलाऊँगी",
            "band" to "बंद",
            "roko" to "रोको",
            "ruk" to "रुक",
            "ruko" to "रुको",
            "boliye" to "बोलिए",
            "bolo" to "बोलो",
            "bol" to "बोल",
            "bolna" to "बोलना",
            "bola" to "बोला",
            "boli" to "बोली",
            "bolti" to "बोलती",
            "bolta" to "बोलता",
            "bolte" to "बोलते",
            "bolenge" to "बोलेंगे",
            "bolein" to "बोलें",
            "bologe" to "बोलोगे",
            "baat" to "बात",
            "baatein" to "बातें",
            "baaton" to "बातों",
            "sun" to "सुन",
            "suno" to "सुनो",
            "suniye" to "सुनिए",
            "sunna" to "सुनना",
            "suna" to "सुना",
            "sunao" to "सुनाओ",
            "sunungi" to "सुनूँगी",
            "sunti" to "सुनती",
            "dekh" to "देख",
            "dekho" to "देखो",
            "dekhiye" to "देखिए",
            "dekhna" to "देखना",
            "dekha" to "देखा",
            "dekhi" to "देखी",
            "dekhti" to "देखती",
            "dikh" to "दिख",
            "dikhao" to "दिखाओ",
            "dikhaungi" to "दिखाऊँगी",
            "batao" to "बताओ",
            "batayein" to "बताएँ",
            "bata" to "बता",
            "batana" to "बताना",
            "bataungi" to "बताऊँगी",
            "batati" to "बताती",
            "poochho" to "पूछो",
            "pucho" to "पूछो",
            "pucha" to "पूछा",
            "poochiye" to "पूछिए",
            "soch" to "सोच",
            "socho" to "सोचो",
            "sochti" to "सोचती",
            "sochna" to "सोचना",
            "socha" to "सोचा",
            "samajh" to "समझ",
            "samjho" to "समझो",
            "samjhe" to "समझे",
            "samajhti" to "समझती",
            "samajhna" to "समझना",
            "yaad" to "याद",
            "bhool" to "भूल",
            "bhoolna" to "भूलना",
            "rakh" to "रख",
            "rakho" to "रखो",
            "rakhna" to "रखना",
            "rakhungi" to "रखूँगी",
            "rakhti" to "रखती",
            "rakhiye" to "रखिए",
            "rahiye" to "रहिए",
            "kijiye" to "कीजिए",
            "lijiye" to "लीजिए",
            "dijiye" to "दीजिए",
            "bhej" to "भेज",
            "bhejo" to "भेजो",
            "bhejna" to "भेजना",
            "bheja" to "भेजा",
            "bhejungi" to "भेजूँगी",
            "mil" to "मिल",
            "mila" to "मिला",
            "mili" to "मिली",
            "milna" to "मिलना",
            "milega" to "मिलेगा",
            "milegi" to "मिलेगी",
            "laga" to "लगा",
            "lagao" to "लगाओ",
            "lagana" to "लगाना",
            "lagaungi" to "लगाऊँगी",
            "lagta" to "लगता",
            "lagti" to "लगती",
            "lag" to "लग",
            "sakte" to "सकते",
            "sakti" to "सकती",
            "sakta" to "सकता",
            "chahiye" to "चाहिए",
            "chahte" to "चाहते",
            "chahti" to "चाहती",
            "chahta" to "चाहता",
            "kya" to "क्या",
            "kaise" to "कैसे",
            "kaisi" to "कैसी",
            "kaisa" to "कैसा",
            "kahan" to "कहाँ",
            "kab" to "कब",
            "kyu" to "क्यों",
            "kyun" to "क्यों",
            "kyunki" to "क्योंकि",
            "kaun" to "कौन",
            "kisne" to "किसने",
            "kisko" to "किसको",
            "kiska" to "किसका",
            "kiski" to "किसकी",
            "kitna" to "कितना",
            "kitni" to "कितनी",
            "kitne" to "कितने",
            "bahut" to "बहुत",
            "bohot" to "बहुत",
            "zyada" to "ज़्यादा",
            "jyada" to "ज़्यादा",
            "kam" to "कम",
            "thoda" to "थोड़ा",
            "thodi" to "थोड़ी",
            "thode" to "थोड़े",
            "chhota" to "छोटा",
            "chhoti" to "छोटी",
            "bada" to "बड़ा",
            "badi" to "बड़ी",
            "achha" to "अच्छा",
            "accha" to "अच्छा",
            "achhi" to "अच्छी",
            "acchi" to "अच्छी",
            "achhe" to "अच्छे",
            "acche" to "अच्छे",
            "sahi" to "सही",
            "galat" to "ग़लत",
            "theek" to "ठीक",
            "thik" to "ठीक",
            "saaf" to "साफ़",
            "naya" to "नया",
            "nayi" to "नई",
            "naye" to "नए",
            "purana" to "पुराना",
            "abhi" to "अभी",
            "ab" to "अब",
            "aaj" to "आज",
            "kal" to "कल",
            "parso" to "परसों",
            "yeh" to "यह",
            "ye" to "ये",
            "wo" to "वो",
            "woh" to "वो",
            "yahan" to "यहाँ",
            "yaha" to "यहाँ",
            "wahan" to "वहाँ",
            "waha" to "वहाँ",
            "yahin" to "यहीं",
            "wahin" to "वहीं",
            "wahi" to "वही",
            "aur" to "और",
            "ya" to "या",
            "lekin" to "लेकिन",
            "magar" to "मगर",
            "phir" to "फिर",
            "fir" to "फिर",
            "agar" to "अगर",
            "toh" to "तो",
            "par" to "पर",
            "pe" to "पे",
            "mein" to "में",
            "se" to "से",
            "ko" to "को",
            "ka" to "का",
            "ki" to "की",
            "ke" to "के",
            "bhi" to "भी",
            "hi" to "ही",
            "na" to "ना",
            "nahi" to "नहीं",
            "nahin" to "नहीं",
            "mat" to "मत",
            "bas" to "बस",
            "sirf" to "सिर्फ़",
            "bilkul" to "बिल्कुल",
            "turant" to "तुरंत",
            "jaldi" to "जल्दी",
            "dheere" to "धीरे",
            "poora" to "पूरा",
            "pura" to "पूरा",
            "poori" to "पूरी",
            "puri" to "पूरी",
            "madad" to "मदद",
            "khushi" to "खुशी",
            "khush" to "खुश",
            "udaas" to "उदास",
            "dil" to "दिल",
            "dimag" to "दिमाग़",
            "dimaag" to "दिमाग़",
            "mann" to "मन",
            "dhyan" to "ध्यान",
            "khayal" to "ख़याल",
            "hamesha" to "हमेशा",
            "kabhi" to "कभी",
            "roz" to "रोज़",
            "saath" to "साथ",
            "sath" to "साथ",
            "dost" to "दोस्त",
            "dosti" to "दोस्ती",
            "zindagi" to "ज़िंदगी",
            "duniya" to "दुनिया",
            "insaan" to "इंसान",
            "log" to "लोग",
            "घर" to "घर",
            "ghar" to "घर",
            "kaam" to "काम",
            "naam" to "नाम",
            "sawal" to "सवाल",
            "jawab" to "जवाब",
            "kahani" to "कहानी",
            "shayari" to "शायरी",
            "kavita" to "कविता",
            "chutkula" to "चुटकुला",
            "mausam" to "मौसम",
            "khana" to "खाना",
            "khaya" to "खाया",
            "paani" to "पानी",
            "neend" to "नींद",
            "sapna" to "सपना",
            "sapno" to "सपनों",
            "chehra" to "चेहरा",
            "chehre" to "चेहरे",
            "aankhein" to "आँखें",
            "muskurate" to "मुस्कुराते",
            "muskurahat" to "मुस्कुराहट",
            "nakhre" to "नखरे",
            "nakhra" to "नखरा",
            "naraz" to "नाराज़",
            "naraaz" to "नाराज़",
            "gussa" to "गुस्सा",
            "sacchi" to "सच्ची",
            "sach" to "सच",
            "pakka" to "पक्का",
            "hoshiyar" to "होशियार",
            "samajhdar" to "समझदार",
            "pagal" to "पागल",
            "buddhu" to "बुद्धू",
            "chup" to "चुप",
            "shant" to "शांत",
            "mummy" to "मम्मी",
            "papa" to "पापा",
            "bhai" to "भाई",
            "didi" to "दीदी",
            "rahul" to "राहुल",
            "gaana" to "गाना",
            "gaane" to "गाने",
            "gana" to "गाना",
            "gane" to "गाने",
            "bajao" to "बजाओ",
            "sunao" to "सुनाओ",
            "dhundo" to "ढूँढो",
            "dhundho" to "ढूँढो",
            "niche" to "नीचे",
            "upar" to "ऊपर",
            "piche" to "पीछे",
            "aage" to "आगे",
            "beech" to "बीच",
            "agla" to "अगला",
            "pichla" to "पिछला",
            "pehla" to "पहला",
            "pehli" to "पहली",
            "doosra" to "दूसरा",
            "doosri" to "दूसरी",
            "dusra" to "दूसरा",
            "dusri" to "दूसरी",
            "dusre" to "दूसरे",
            "doosre" to "दूसरे",
            "teesra" to "तीसरा",
            "samay" to "समय",
            "waqt" to "वक़्त",
            "baje" to "बजे",
            "din" to "दिन",
            "raat" to "रात",
            "subah" to "सुबह",
            "shaam" to "शाम",
            "dopahar" to "दोपहर",
            "taiyaar" to "तैयार",
            "shukriya" to "शुक्रिया",
            "dhanyawad" to "धन्यवाद",
            "maaf" to "माफ़",
            "aaram" to "आराम",
            "apna" to "अपना",
            "apni" to "अपनी",
            "apne" to "अपने",
            "apno" to "अपनों",
            "khud" to "ख़ुद",
            "roshni" to "रोशनी",
            "andhera" to "अँधेरा",
            "batti" to "बत्ती",
            "jalao" to "जलाओ",
            "bujhao" to "बुझाओ",
            "badhao" to "बढ़ाओ",
            "ghatao" to "घटाओ",
            "dikkat" to "दिक्कत",
            "zaroor" to "ज़रूर",
            "jaroor" to "ज़रूर",
            "zaroorat" to "ज़रूरत",
            "faisla" to "फ़ैसला",
            "fida" to "फ़िदा",
            "saare" to "सारे",
            "sabhi" to "सभी",
            "sab" to "सब",
            "kuch" to "कुछ",
            "kuchh" to "कुछ",
            "koi" to "कोई",
            "har" to "हर",
            "ek" to "एक",
            "do" to "दो",
            "teen" to "तीन",
            "chaar" to "चार",
            "paanch" to "पाँच",
            "bharat" to "भारत",
            "pradhanmantri" to "प्रधानमंत्री",
            "rajdhani" to "राजधानी",
            "prakash" to "प्रकाश",
            "gati" to "गति"
        )
    }

    private var tts: TextToSpeech? = null
    @Volatile
    private var isInitialized = false
    @Volatile
    private var isHindiVoiceActive = false
    @Volatile
    private var currentUtteranceId: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = JarvisPreferences(context)
    private var currentProfile: String = prefs.voice.ifBlank { "Sweet Girl Voice" }
    private var currentSpeed: Float = prefs.voiceSpeed
    private var currentPitch: Float = prefs.voicePitch
    private val speechQueue = ConcurrentLinkedQueue<String>()
    private var activeFocusRequest: AudioFocusRequest? = null

    // Safety timer so if the OS TTS engine never fires onDone, we still reset speaking state cleanly
    private val utteranceWatchdogRunnable = Runnable {
        if (currentUtteranceId.isNotEmpty()) {
            Log.w(TAG, "TTS utterance watchdog fired for $currentUtteranceId")
            val next = speechQueue.poll()
            if (next != null) {
                speakInternalChunk(next, flush = false)
            } else {
                currentUtteranceId = ""
                abandonAudioFocusSafely()
                onStateChanged(false)
            }
        }
    }

    init {
        initEngine()
    }

    private fun initEngine() {
        try {
            tts = TextToSpeech(context.applicationContext, this, GOOGLE_TTS_PACKAGE)
        } catch (e: Exception) {
            Log.w(TAG, "Google TTS package init fallback: ${e.message}")
            try {
                tts = TextToSpeech(context.applicationContext, this)
            } catch (ex: Exception) {
                Log.e(TAG, "Error initializing TextToSpeech: ${ex.message}")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                var configured = false
                val hiIn = Locale("hi", "IN")
                val enIn = Locale("en", "IN")
                val preferredLocales = listOf(hiIn, enIn, Locale.getDefault(), Locale.US)

                for (loc in preferredLocales) {
                    val res = engine.setLanguage(loc)
                    if (res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED) {
                        configured = true
                        isHindiVoiceActive = loc.language.equals("hi", ignoreCase = true)
                        break
                    }
                }
                if (!configured) {
                    engine.language = Locale.getDefault()
                    isHindiVoiceActive = engine.language?.language?.equals("hi", ignoreCase = true) == true
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val audioAttrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(audioAttrs)
                }

                isInitialized = true
                applyVoiceProfile(currentProfile, prefs.voiceSpeed, prefs.voicePitch)

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (utteranceId == currentUtteranceId) {
                            mainHandler.post { onStateChanged(true) }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == currentUtteranceId) {
                            mainHandler.removeCallbacks(utteranceWatchdogRunnable)
                            val next = speechQueue.poll()
                            if (next != null) {
                                // RULE #5: Natural breathing / thinking pause (350ms) between short sentences
                                mainHandler.postDelayed({
                                    speakInternalChunk(next, flush = false)
                                }, 350L)
                            } else {
                                currentUtteranceId = ""
                                abandonAudioFocusSafely()
                                mainHandler.post { onStateChanged(false) }
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == currentUtteranceId) {
                            mainHandler.removeCallbacks(utteranceWatchdogRunnable)
                            val next = speechQueue.poll()
                            if (next != null) {
                                speakInternalChunk(next, flush = false)
                            } else {
                                currentUtteranceId = ""
                                abandonAudioFocusSafely()
                                mainHandler.post { onStateChanged(false) }
                            }
                        }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId == currentUtteranceId) {
                            mainHandler.removeCallbacks(utteranceWatchdogRunnable)
                            val next = speechQueue.poll()
                            if (next != null) {
                                speakInternalChunk(next, flush = false)
                            } else {
                                currentUtteranceId = ""
                                abandonAudioFocusSafely()
                                mainHandler.post { onStateChanged(false) }
                            }
                        }
                    }
                })

                val queued = speechQueue.poll()
                if (queued != null) {
                    speak(queued)
                }
            }
        } else {
            Log.e(TAG, "TextToSpeech onInit failed with status $status")
            try {
                tts = TextToSpeech(context.applicationContext) { fallbackStatus ->
                    if (fallbackStatus == TextToSpeech.SUCCESS) {
                        isInitialized = true
                        applyVoiceProfile(currentProfile, prefs.voiceSpeed, prefs.voicePitch)
                        val queued = speechQueue.poll()
                        if (queued != null) speak(queued)
                    } else {
                        mainHandler.post { onStateChanged(false) }
                    }
                }
            } catch (_: Exception) {
                mainHandler.post { onStateChanged(false) }
            }
        }
    }

    private fun requestAudioFocusSafely() {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (current == 0 || (current.toFloat() / max.toFloat()) < 0.45f) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * 0.82f).toInt().coerceAtLeast(1), 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttrs)
                    .setWillPauseWhenDucked(false)
                    .build()
                activeFocusRequest = req
                am.requestAudioFocus(req)
            }
        } catch (_: Exception) {}
    }

    private fun abandonAudioFocusSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                activeFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            }
        } catch (_: Exception) {}
    }

    private fun isMaleVoiceProfile(profile: String): Boolean {
        val lower = profile.lowercase(Locale.ROOT)
        return lower == "classic" || lower.contains("male") || lower.contains("ladka") || lower.contains("boy")
    }

    private fun selectBestFemaleVoice(engine: TextToSpeech): Voice? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return null
        val availableVoices = try {
            engine.voices
        } catch (_: Exception) {
            null
        } ?: return null
        if (availableVoices.isEmpty()) return null

        val knownMaleTokens = listOf(
            "hi-in-x-hid", "hi-in-x-hif", "en-in-x-ena", "en-in-x-enb",
            "en-us-x-iom", "en-us-x-iob", "en-us-x-iol", "#male", "male", "-m0"
        )

        // Prioritize LOCAL (offline-ready, zero-lag) sweet Indian female voices first!
        val preferredLocalFemaleTokens = listOf(
            "hi-in-x-hia-local", "hi-in-x-hie-local", "hi-in-x-hic-local",
            "en-in-x-end-local", "en-in-x-enc-local", "en-in-x-ene-local",
            "hi-in-x-hia", "hi-in-x-hie", "hi-in-x-hic",
            "en-in-x-end", "en-in-x-enc", "en-in-x-ene",
            "en-us-x-tpf-local", "en-us-x-sfg-local", "en-us-x-tpf", "en-us-x-sfg"
        )

        // First pass: strictly local voices (!isNetworkConnectionRequired) for instant zero-drop playback
        for (token in preferredLocalFemaleTokens) {
            val match = availableVoices.firstOrNull { v ->
                val nameLower = v.name.lowercase(Locale.ROOT)
                !v.isNetworkConnectionRequired &&
                    nameLower.contains(token) &&
                    knownMaleTokens.none { m -> nameLower.contains(m) }
            }
            if (match != null) return match
        }

        // Second pass: allow any matching preferred female token
        for (token in preferredLocalFemaleTokens) {
            val match = availableVoices.firstOrNull { v ->
                val nameLower = v.name.lowercase(Locale.ROOT)
                nameLower.contains(token) && knownMaleTokens.none { m -> nameLower.contains(m) }
            }
            if (match != null) return match
        }

        val femaleCandidates = availableVoices.filter { v ->
            val n = v.name.lowercase(Locale.ROOT)
            val feats = v.features?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
            val isExplicitFemale = n.contains("female") || n.contains("#female") ||
                n.contains("f0") || feats.any { it.contains("female") }
            val isNotMale = knownMaleTokens.none { m -> n.contains(m) } &&
                feats.none { it.contains("gender=male") }
            isExplicitFemale && isNotMale
        }.sortedWith(
            compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                .thenByDescending { it.locale.language.equals("hi", ignoreCase = true) }
                .thenByDescending { it.locale.country.equals("IN", ignoreCase = true) }
                .thenByDescending { it.quality }
        )

        if (femaleCandidates.isNotEmpty()) {
            return femaleCandidates.first()
        }

        return availableVoices.firstOrNull { v ->
            val n = v.name.lowercase(Locale.ROOT)
            !v.isNetworkConnectionRequired &&
                (v.locale.language == "hi" || v.locale.country.equals("IN", ignoreCase = true)) &&
                knownMaleTokens.none { m -> n.contains(m) }
        }
    }

    /**
     * Apply chosen voice profile — tuned for a 20-22 yr old sweet, soft, natural girl voice
     */
    fun applyVoiceProfile(
        profile: String,
        speedMultiplier: Float = currentSpeed,
        pitchMultiplier: Float = currentPitch
    ) {
        currentProfile = profile.ifBlank { "Sweet Girl Voice" }
        currentSpeed = speedMultiplier
        currentPitch = pitchMultiplier

        val engine = tts ?: return
        if (!isInitialized) return

        val lower = currentProfile.lowercase(Locale.ROOT)
        val isMale = isMaleVoiceProfile(lower)
        val isCalm = lower.contains("calm") || lower.contains("peaceful") || lower.contains("shant")
        val isEnergetic = lower.contains("energetic") || lower.contains("dynamic") || lower.contains("fast")

        try {
            var matchedFemaleVoice: Voice? = null
            if (!isMale) {
                matchedFemaleVoice = selectBestFemaleVoice(engine)
                if (matchedFemaleVoice != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    engine.voice = matchedFemaleVoice
                    isHindiVoiceActive = matchedFemaleVoice.locale.language.equals("hi", ignoreCase = true)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val maleVoice = engine.voices?.firstOrNull { voice ->
                    !voice.isNetworkConnectionRequired && (
                        voice.name.contains("male", ignoreCase = true) ||
                            voice.name.contains("en-in-x-ena", ignoreCase = true) ||
                            voice.name.contains("hi-in-x-hid", ignoreCase = true)
                        )
                }
                if (maleVoice != null) {
                    engine.voice = maleVoice
                    isHindiVoiceActive = maleVoice.locale.language.equals("hi", ignoreCase = true)
                }
            }

            // RULE #5: 21-year-old Indian girl Aanya — soft, warm, sweet, breathy voice
            // Medium-high pitch (1.10f), slow-medium speed (0.89f = 89%, inside 85-92% target)
            val (basePitch, baseRate) = when {
                isMale -> Pair(0.95f, 1.00f)
                isCalm -> Pair(1.08f, 0.87f)
                isEnergetic -> Pair(1.12f, 0.94f)
                matchedFemaleVoice != null -> Pair(1.10f, 0.89f)
                else -> Pair(1.16f, 0.89f)
            }

            val normPitchMod = if (pitchMultiplier in 0.94f..1.18f) 1.0f else (pitchMultiplier / 1.10f)
            val normSpeedMod = if (speedMultiplier in 0.84f..1.04f) 1.0f else speedMultiplier

            val finalPitch = (basePitch * normPitchMod).coerceIn(0.85f, 1.32f)
            val finalRate = (baseRate * normSpeedMod).coerceIn(0.80f, 1.25f)

            engine.setPitch(finalPitch)
            engine.setSpeechRate(finalRate)
        } catch (e: Exception) {
            Log.w(TAG, "Error applying voice profile: ${e.message}")
        }
    }

    fun previewVoiceProfile(profile: String, speed: Float = 0.89f, pitch: Float = 1.10f) {
        applyVoiceProfile(profile, speed, pitch)
        val sampleText = when {
            isMaleVoiceProfile(profile) -> "Jarvis classic voice operational. How may I assist you?"
            profile.contains("calm", ignoreCase = true) -> "Ji… main JARVIS hun. Meri aawaz bilkul soft, warm aur caring hai ji."
            profile.contains("energetic", ignoreCase = true) -> "Haan ji… main JARVIS bilkul ready hun. Aap jo bhi bolenge, main exact wahi karungi ji."
            else -> "Ji… main JARVIS hun, 21 saal ki aapki pyari dost. Aap jo bolenge main dhyan se sunungi aur exact wahi karungi ji 💕"
        }
        speak(sampleText)
    }

    /**
     * Converts Roman Hinglish words into native Devanagari when Hindi neural voice is active
     * (only when the sentence is actually Hindi/Hinglish) so the girl voice sounds 100% authentic,
     * smooth, and natural without stuttering.
     */
    private fun convertHinglishToLovelyPhonetics(input: String): String {
        val tokens = input.split(" ")
        if (tokens.isEmpty()) return input

        // Check if sentence contains at least one clear Hindi word before converting ambiguous particles
        val lowerSentence = input.lowercase(Locale.ROOT)
        val isHindiSentence = listOf(
            "haan", "main", "tum", "jaan", "pyar", "pyaar", "khol", "kholo", "karo", "karungi",
            "hun", "hoon", "hai", "hain", "mera", "meri", "tumhara", "tumhari", "rahi", "raha",
            "bhej", "diya", "gaya", "aap", "kaise", "kaisi", "kya", "nahi", "bolo"
        ).any { Regex("\\b$it\\b").containsMatchIn(lowerSentence) }

        return tokens.joinToString(" ") { rawToken ->
            val leadingPunct = rawToken.takeWhile { !it.isLetterOrDigit() }
            val trailingPunct = rawToken.takeLastWhile { !it.isLetterOrDigit() }
            val core = rawToken.substring(leadingPunct.length, rawToken.length - trailingPunct.length)
            if (core.isEmpty()) {
                rawToken
            } else {
                val lowerCore = core.lowercase(Locale.ROOT)
                if (isHindiVoiceActive && isHindiSentence) {
                    val devanagari = HINGLISH_TO_DEVANAGARI[lowerCore]
                    if (devanagari != null) {
                        "$leadingPunct$devanagari$trailingPunct"
                    } else {
                        rawToken
                    }
                } else if (!isHindiVoiceActive && isHindiSentence) {
                    val softEnglish = when (lowerCore) {
                        "main" -> "mai"
                        "maine" -> "mai-ne"
                        "hoon", "hun" -> "hoo"
                        "haan" -> "haa"
                        "pyar", "pyaar" -> "pyaar"
                        "pyari" -> "pyaaree"
                        "meethi" -> "meethee"
                        "karungi" -> "karoongi"
                        "karun" -> "karoo"
                        "dungi" -> "doongi"
                        "sunungi" -> "sunoongi"
                        "rahungi" -> "rahoongi"
                        "bataungi" -> "bataoongi"
                        "jaan" -> "jaan"
                        "kholo" -> "kho-lo"
                        "khol" -> "khol"
                        else -> core
                    }
                    "$leadingPunct$softEnglish$trailingPunct"
                } else {
                    rawToken
                }
            }
        }
    }

    /**
     * CRITICAL SANITIZER:
     * Completely strips all exclamation marks (!), emojis, symbols, and markdown so Hindi TTS
     * NEVER says "vismiyadibodhak chinh" (विस्मयादिबोधक चिह्न) and pronounces sentences smoothly.
     */
    private fun sanitizeForSpeech(raw: String): String {
        var s = raw
        // Replace links cleanly
        s = s.replace(Regex("https?://t\\.me/\\S+"), "AK EXPLOITS Telegram channel")
        s = s.replace(Regex("https?://\\S+"), "link")

        // Remove any literal occurrences of vismiyadibodhak chinh if present
        s = s.replace(Regex("(vismiyadibodhak|vismayadibodhak)\\s*(chinh|chinha)?", RegexOption.IGNORE_CASE), " ")
        s = s.replace("विस्मयादिबोधक चिह्न", " ")
        s = s.replace("विस्मयादिबोधक", " ")

        // Strip stage directions like (pause), (thinking pause), (breath), (soft smile), (caring), (soft)
        // and replace them with sentence break periods so TTS takes a real 350ms breathing pause!
        s = s.replace(Regex("\\((?:pause|thinking\\s*pause|breath|soft\\s*smile|caring|soft|smile|whisper|giggle)\\)", RegexOption.IGNORE_CASE), ". ")
        s = s.replace("…", ". ")
        s = s.replace("...", ". ")

        // Replace ALL exclamation and question marks with soft pauses
        s = s.replace(Regex("[!¡！❗❕]+"), ", ")
        s = s.replace(Regex("[?？]+"), ". ")
        s = s.replace("।", ". ")

        // Strip leading formal or exclamatory words
        s = s.replace(
            Regex("^(sir|ji\\s*sir|hello\\s*sir|yes\\s*sir|arre\\s*sir|boss)[,!.:;\\s]+", RegexOption.IGNORE_CASE),
            ""
        )

        // Preserve contractions (like don't -> dont) before stripping symbols
        s = s.replace(Regex("(?<=\\p{L})['’](?=\\p{L})"), "")

        // Strictly allow ONLY letters (\p{L}), Hindi matras/marks (\p{M}), numbers (\p{N}), spaces, commas, and periods
        s = s.replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s,.]"), " ")

        // Normalize multiple commas/periods/spaces
        s = s.replace(Regex("(\\s*,\\s*)+"), ", ")
        s = s.replace(Regex("(\\s*\\.\\s*)+"), ". ")
        s = s.replace(Regex("\\s+"), " ").trim()
        s = s.trimStart('.', ',', ' ').trimEnd(',')

        // Strip trailing "Sir"
        s = s.replace(Regex("[,\\.\\s]+(sir|ji\\s*sir)[.]?$", RegexOption.IGNORE_CASE), "")

        return convertHinglishToLovelyPhonetics(s.trim())
    }

    fun speak(text: String) {
        val cleaned = sanitizeForSpeech(text)
        if (cleaned.isBlank()) {
            mainHandler.post { onStateChanged(false) }
            return
        }

        speechQueue.clear()
        mainHandler.removeCallbacks(utteranceWatchdogRunnable)
        requestAudioFocusSafely()

        if (!isInitialized || tts == null) {
            speechQueue.offer(cleaned)
            initEngine()
            // Schedule safety callback in case TTS engine cannot initialize on emulator
            mainHandler.postDelayed(utteranceWatchdogRunnable, 4000L)
            return
        }

        // Notify immediately that TTS is starting so SpeechRecognizer stays paused and never cuts off speech
        mainHandler.post { onStateChanged(true) }

        // Split into natural conversational sentence chunks so Aanya takes a 350ms breath between sentences
        val chunks = cleaned.split(Regex("(?<=\\.)\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (chunks.isEmpty()) {
            mainHandler.post { onStateChanged(false) }
            return
        }

        for (i in 1 until chunks.size) {
            speechQueue.offer(chunks[i])
        }

        speakInternalChunk(chunks[0], flush = true)
    }

    private fun speakInternalChunk(chunk: String, flush: Boolean) {
        val engine = tts ?: run {
            mainHandler.post { onStateChanged(false) }
            return
        }

        val utteranceId = "JARVIS_SPEECH_${System.nanoTime()}"
        currentUtteranceId = utteranceId

        // Estimate max duration for this chunk (~95ms per char, min 3.5s, max 12s)
        val watchdogDelayMs = (chunk.length * 95L).coerceIn(3500L, 12000L)
        mainHandler.removeCallbacks(utteranceWatchdogRunnable)
        mainHandler.postDelayed(utteranceWatchdogRunnable, watchdogDelayMs)

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        val queueMode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val result = engine.speak(chunk, queueMode, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "engine.speak failed code $result, retrying")
            try {
                val retryRes = engine.speak(chunk, queueMode, params, utteranceId)
                if (retryRes != TextToSpeech.SUCCESS) {
                    mainHandler.removeCallbacks(utteranceWatchdogRunnable)
                    currentUtteranceId = ""
                    mainHandler.post { onStateChanged(false) }
                }
            } catch (retryEx: Exception) {
                Log.e(TAG, "Retry speak error: ${retryEx.message}")
                mainHandler.removeCallbacks(utteranceWatchdogRunnable)
                currentUtteranceId = ""
                mainHandler.post { onStateChanged(false) }
            }
        }
    }

    fun testVoice(message: String = "Ji… main JARVIS hun, 21 saal ki aapki pyari dost. Haan ji, aap jo bhi bolenge main pehle poori baat dhyan se sunungi, aur exact wahi karungi ji 💕") {
        speak(message)
    }

    fun isSpeaking(): Boolean {
        return try {
            tts?.isSpeaking == true || currentUtteranceId.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        mainHandler.removeCallbacks(utteranceWatchdogRunnable)
        val wasActive = currentUtteranceId.isNotEmpty() || speechQueue.isNotEmpty()
        currentUtteranceId = ""
        speechQueue.clear()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        abandonAudioFocusSafely()
        if (wasActive) {
            mainHandler.post {
                if (currentUtteranceId.isEmpty() && speechQueue.isEmpty()) {
                    onStateChanged(false)
                }
            }
        }
    }

    fun shutdown() {
        mainHandler.removeCallbacks(utteranceWatchdogRunnable)
        currentUtteranceId = ""
        speechQueue.clear()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        abandonAudioFocusSafely()
        tts = null
        isInitialized = false
    }
}
