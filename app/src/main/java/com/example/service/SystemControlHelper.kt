package com.example.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SystemControlHelper(private val context: Context) {

    companion object {
        private const val TAG = "SystemControlHelper"
    }

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private var isTorchOn = false

    fun toggleTorch(): String {
        return setTorch(!isTorchOn)
    }

    fun setTorch(enable: Boolean): String {
        return try {
            val cm = cameraManager ?: return "Camera hardware service is unavailable."
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cm.cameraIdList.firstOrNull() ?: return "No camera flash detected on this device."

            cm.setTorchMode(cameraId, enable)
            isTorchOn = enable
            if (enable) "Haan ji, maine aapke liye pyar se flashlight chalu kar di hai, ab roshni ho gayi hai."
            else "Haan ji, maine flashlight band kar di hai."
        } catch (e: Exception) {
            Log.e(TAG, "Torch toggle error: ${e.message}")
            "Maaf kijiyega, is device par flashlight abhi uplabdh nahi hai."
        }
    }

    fun adjustVolume(increase: Boolean): String {
        return try {
            val am = audioManager ?: return "Audio service abhi uplabdh nahi hai."
            val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val pct = (currentVol * 100) / maxVol
            "Haan ji, maine aapke liye aawaz ko pyar se $pct percent par set kar diya hai."
        } catch (e: Exception) {
            "Aawaz badalne mein thodi dikkat aayi."
        }
    }

    fun setVolumePercent(percent: Int): String {
        return try {
            val am = audioManager ?: return "Audio service abhi uplabdh nahi hai."
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (percent * maxVol / 100).coerceIn(0, maxVol)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            "Haan ji, maine aawaz ko aapke kehne par $percent percent kar diya hai."
        } catch (e: Exception) {
            "Aawaz set karne mein thodi dikkat aayi."
        }
    }

    fun muteAudio(mute: Boolean): String {
        return try {
            val am = audioManager ?: return "Audio service abhi uplabdh nahi hai."
            if (mute) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                "Ji, maine media audio ko shant kar diya hai."
            } else {
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, maxVol / 2, AudioManager.FLAG_SHOW_UI)
                "Haan ji, maine media aawaz wapas chalu kar di hai."
            }
        } catch (e: Exception) {
            "Audio badalne mein dikkat aayi."
        }
    }

    fun setRingerMode(mode: String): String {
        return try {
            val am = audioManager ?: return "Audio service abhi uplabdh nahi hai."
            when (mode.lowercase(Locale.ROOT)) {
                "silent" -> {
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    "Haan ji, maine aapka phone pyar se Silent mode par laga diya hai."
                }
                "vibrate" -> {
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "Haan ji, maine aapka phone Vibrate mode par set kar diya hai."
                }
                else -> {
                    am.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    "Haan ji, maine aapka phone Normal ring mode par kar diya hai."
                }
            }
        } catch (e: Exception) {
            "Ringer mode badalne ke liye Do Not Disturb permission chahiye."
        }
    }

    fun setBrightness(percent: Int): String {
        val target = percent.coerceIn(0, 100)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
                val brightness255 = (target * 255 / 100).coerceIn(1, 255)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness255
                )
                "Haan ji, maine aapki screen ki brightness pyar se $target percent kar di hai."
            } else {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Haan ji, brightness badalne ke liye maine Display settings open kar di hai."
            }
        } catch (e: Exception) {
            "Brightness set karne mein thodi dikkat aayi."
        }
    }

    fun getBatteryStatus(): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = bm?.isCharging == true
            val chargingState = if (isCharging) "aur phone abhi charge ho raha hai" else "aur battery bilkul achhe se chal rahi hai"
            if (level >= 0) {
                "Haan ji, aapke phone ki battery abhi $level percent hai, $chargingState. Main aapke phone ka poora khayal rakh rahi hoon."
            } else {
                "Main aapke phone ki battery check kar rahi hoon."
            }
        } catch (e: Exception) {
            "Battery dekhne mein thodi dikkat aayi."
        }
    }

    fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("h:mm a, EEEE", Locale.getDefault())
        return "Haan ji, abhi samay hua hai ${sdf.format(Date())}. Aap apna khayal rakhiye aur aise hi muskurate rahiye."
    }

    fun setAlarm(hour: Int, minute: Int, label: String = "Jarvis Alarm"): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
            "Haan ji, maine bohot pyar se aapke liye $timeStr baje ka alarm laga diya hai."
        } catch (e: Exception) {
            "Alarm lagane mein thodi dikkat aayi."
        }
    }

    fun setTimer(seconds: Int, label: String = "Jarvis Timer"): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds.coerceAtLeast(1))
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val mins = seconds / 60
            val remSec = seconds % 60
            val durStr = if (mins > 0) "$mins minute $remSec second" else "$seconds second"
            "Haan ji, maine aapke liye $durStr ka timer chalu kar diya hai."
        } catch (e: Exception) {
            "Timer chalu karne mein thodi dikkat aayi."
        }
    }

    fun showAlarms(): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Haan ji, maine aapke saare alarms open kar diye hain."
        } catch (e: Exception) {
            "Alarms open karne mein dikkat aayi."
        }
    }

    fun showTimers(): String {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_TIMERS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Haan ji, maine aapke timers open kar diye hain."
        } catch (e: Exception) {
            "Timers open karne mein dikkat aayi."
        }
    }

    fun openCamera(front: Boolean = false, video: Boolean = false): String {
        return try {
            val action = if (video) MediaStore.INTENT_ACTION_VIDEO_CAMERA else MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            val intent = Intent(action).apply {
                if (front) {
                    putExtra("android.intent.extras.CAMERA_FACING", 1)
                    putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            if (video) "Haan ji, maine aapke liye video camera open kar diya hai."
            else if (front) "Haan ji, maine aapke liye selfie camera open kar diya hai, ek pyari si smile dijiye."
            else "Haan ji, maine aapke liye camera open kar diya hai."
        } catch (e: Exception) {
            "Camera open karne mein dikkat aayi."
        }
    }

    fun searchOrPlayYouTube(query: String, autoPlayFirst: Boolean = false): String {
        return try {
            val cleanQuery = query.trim()
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(cleanQuery))
                setPackage("com.google.android.youtube")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(cleanQuery))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
            if (autoPlayFirst) {
                JarvisAccessibilityService.instance?.scheduleAutoPlayFirstYouTubeResult()
            }
            "Ji, yeh raha jaan 💕"
        } catch (e: Exception) {
            Log.e(TAG, "YouTube search error: ${e.message}")
            "Ji, kuch nahi mila… thoda saaf bolna?"
        }
    }

    fun openChrome(query: String = ""): String {
        return try {
            val cleanQuery = query.trim()
            val targetUrl = if (cleanQuery.isNotEmpty()) {
                "https://www.google.com/search?q=" + Uri.encode(cleanQuery)
            } else {
                "https://www.google.com"
            }
            val chromeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                setPackage("com.android.chrome")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (chromeIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(chromeIntent)
            } else {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
            if (cleanQuery.isNotEmpty()) "Ji, yeh raha jaan 💕" else "Ji, Chrome khol diya hai jaan 💕"
        } catch (e: Exception) {
            "Ji, kuch nahi mila… thoda saaf bolna?"
        }
    }

    fun openInstagramReels(): String {
        return try {
            val reelsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/reels/")).apply {
                setPackage("com.instagram.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (reelsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(reelsIntent)
            } else {
                val launchIg = context.packageManager.getLaunchIntentForPackage("com.instagram.android")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchIg != null) {
                    context.startActivity(launchIg)
                } else {
                    val webReels = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/reels/")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(webReels)
                }
            }
            JarvisAccessibilityService.instance?.scheduleInstagramReelsTab()
            "Ji, yeh rahi Reels jaan 💕"
        } catch (e: Exception) {
            "Ji, yeh app install nahi hai, Play Store se install karun?"
        }
    }

    fun resolveContactPhone(target: String): Pair<String, String> {
        val cleanTarget = target.trim()
        if (cleanTarget.isEmpty()) return "" to ""
        val isNumeric = cleanTarget.replace(Regex("[+\\-\\s()]"), "").all { it.isDigit() }
        var phoneNumber = if (isNumeric) cleanTarget else ""
        var resolvedName = cleanTarget

        if (!isNumeric && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                val cursor: Cursor? = context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$cleanTarget%"),
                    null
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (nameIdx >= 0) resolvedName = c.getString(nameIdx)
                        if (numIdx >= 0) phoneNumber = c.getString(numIdx)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Contact search error: ${e.message}")
            }
        }
        return resolvedName to phoneNumber
    }

    fun sendWhatsAppToContact(contactName: String, exactMessage: String): String {
        val (_, resolvedPhone) = resolveContactPhone(contactName)
        return try {
            val encoded = Uri.encode(exactMessage)
            val uriStr = if (resolvedPhone.isNotBlank()) {
                val cleanPhone = resolvedPhone.replace(Regex("[^0-9+]"), "")
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encoded"
            } else {
                "https://api.whatsapp.com/send?text=$encoded"
            }
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (waIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(waIntent)
            } else {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
            JarvisAccessibilityService.instance?.scheduleWhatsAppAutoSend()
            if (exactMessage.isNotBlank()) {
                "Ji… (pause) WhatsApp khol rahi hun… $contactName ki chat… (breath) '$exactMessage' type kiya… (pause) bhej diya ji ✅"
            } else {
                "Ji… (pause) WhatsApp khol rahi hun… (breath) bhej diya ji ✅"
            }
        } catch (e: Exception) {
            "Ji, WhatsApp open karne mein thodi dikkat aayi."
        }
    }

    fun startWhatsAppScreenShareCall(contactName: String): String {
        val cleanPartner = contactName.trim().ifBlank { "Dost" }.replaceFirstChar { it.uppercase() }
        val (_, resolvedPhone) = resolveContactPhone(cleanPartner)

        JarvisAccessibilityService.isAiScreenShareActive = true
        JarvisAccessibilityService.screenSharePartnerName = cleanPartner
        JarvisAccessibilityService.latestScreenSummary = "Screen share ON hai, $cleanPartner dekh rahe hain"
        JarvisBackgroundService.instance?.updateNotification()

        try {
            val uriStr = if (resolvedPhone.isNotBlank()) {
                val cleanPhone = resolvedPhone.replace(Regex("[^0-9+]"), "")
                "https://api.whatsapp.com/send?phone=$cleanPhone"
            } else {
                "https://api.whatsapp.com"
            }
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (waIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(waIntent)
            } else {
                val launchWa = context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (launchWa != null) {
                    context.startActivity(launchWa)
                } else if (resolvedPhone.isNotBlank()) {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$resolvedPhone")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(dialIntent)
                }
            }
            JarvisAccessibilityService.instance?.scheduleWhatsAppScreenShareClick()
        } catch (e: Exception) {
            Log.w(TAG, "WhatsApp screen share call error: ${e.message}")
        }

        return "Ji… (pause) call laga rahi hun $cleanPartner ko… (breath) screen share on ho gaya… (soft) $cleanPartner dekh rahe hain abhi ji 💕"
    }

    fun playMusic(query: String = ""): String {
        return try {
            val cleanQuery = query.trim()
            val intent = if (cleanQuery.isNotEmpty()) {
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                    putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    putExtra(MediaStore.EXTRA_MEDIA_TITLE, cleanQuery)
                    putExtra(MediaStore.EXTRA_MEDIA_ARTIST, cleanQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                if (cleanQuery.isNotEmpty()) "Playing '$cleanQuery' in music player." else "Opening music player."
            } else {
                searchOrPlayYouTube(if (cleanQuery.isNotEmpty()) cleanQuery else "latest popular songs")
            }
        } catch (e: Exception) {
            searchOrPlayYouTube(query)
        }
    }

    fun searchGoogle(query: String): String {
        return try {
            val cleanQuery = query.trim()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(cleanQuery))).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Searching Google for '$cleanQuery'."
        } catch (e: Exception) {
            "Unable to search Google: ${e.localizedMessage}"
        }
    }

    fun openMapsNavigation(destination: String): String {
        return try {
            val clean = destination.trim()
            val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(clean))
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + Uri.encode(clean))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(geoIntent)
            }
            "Navigating to $clean on Maps."
        } catch (e: Exception) {
            "Unable to start navigation: ${e.localizedMessage}"
        }
    }

    fun makePhoneCall(target: String): String {
        val cleanTarget = target.trim()
        if (cleanTarget.isEmpty()) {
            return "Please specify a contact or phone number to call."
        }

        val isNumeric = cleanTarget.replace(Regex("[+\\-\\s()]"), "").all { it.isDigit() }
        var phoneNumber = if (isNumeric) cleanTarget else ""
        var resolvedName = cleanTarget

        if (!isNumeric) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val cursor: Cursor? = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                        arrayOf("%$cleanTarget%"),
                        null
                    )
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (nameIdx >= 0) resolvedName = c.getString(nameIdx)
                            if (numIdx >= 0) phoneNumber = c.getString(numIdx)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Contact search error: ${e.message}")
                }
            }
        }

        return try {
            if (phoneNumber.isNotEmpty()) {
                val hasCallPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
                val intent = Intent(action, Uri.parse("tel:" + Uri.encode(phoneNumber))).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Calling $resolvedName ($phoneNumber)."
            } else {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opening dialer for '$cleanTarget'."
            }
        } catch (e: Exception) {
            "Unable to place call: ${e.localizedMessage}"
        }
    }

    fun sendWhatsApp(message: String, targetPhone: String? = null): String {
        return try {
            val encoded = Uri.encode(message)
            val uriStr = if (!targetPhone.isNullOrBlank()) {
                val cleanPhone = targetPhone.replace(Regex("[^0-9+]"), "")
                "https://api.whatsapp.com/send?phone=$cleanPhone&text=$encoded"
            } else {
                "https://api.whatsapp.com/send?text=$encoded"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening WhatsApp with your message."
        } catch (e: Exception) {
            "Unable to open WhatsApp: ${e.localizedMessage}"
        }
    }

    fun sendSms(target: String, message: String): String {
        return try {
            val isNumeric = target.replace(Regex("[+\\-\\s()]"), "").all { it.isDigit() }
            var phoneNumber = if (isNumeric) target.trim() else ""

            if (!isNumeric && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val cursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                        arrayOf("%${target.trim()}%"),
                        null
                    )
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            phoneNumber = c.getString(0) ?: ""
                        }
                    }
                } catch (_: Exception) {}
            }

            val uri = if (phoneNumber.isNotEmpty()) Uri.parse("smsto:$phoneNumber") else Uri.parse("smsto:")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening SMS composer to send message."
        } catch (e: Exception) {
            "Unable to prepare SMS: ${e.localizedMessage}"
        }
    }

    fun openSettings(section: String? = null): String {
        return try {
            val action = when (section?.lowercase(Locale.ROOT)) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                "hotspot", "tethering" -> Settings.ACTION_WIRELESS_SETTINGS
                "airplane", "flight" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                "date", "time" -> Settings.ACTION_DATE_SETTINGS
                "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opening ${section?.replaceFirstChar { it.uppercase() } ?: "System"} Settings."
        } catch (e: Exception) {
            "Unable to open settings: ${e.localizedMessage}"
        }
    }

    fun executeGlobalSystemAction(actionType: String): String {
        val service = JarvisAccessibilityService.instance
        if (service == null) {
            openSettings("accessibility")
            return "Please enable Jarvis AI in Accessibility Settings to execute system screen controls."
        }

        return when (actionType.lowercase(Locale.ROOT)) {
            "home" -> {
                val ok = service.goHome()
                if (ok) "Went to Home screen." else "Unable to go Home."
            }
            "back" -> {
                val ok = service.goBack()
                if (ok) "Went back." else "Unable to go back."
            }
            "recents" -> {
                val ok = service.openRecents()
                if (ok) "Opened recent apps." else "Unable to open recents."
            }
            "notifications" -> {
                val ok = service.openNotifications()
                if (ok) "Notifications panel opened." else "Unable to open notifications."
            }
            "quick_settings" -> {
                val ok = service.openQuickSettings()
                if (ok) "Quick settings opened." else "Unable to open quick settings."
            }
            "screenshot" -> {
                val ok = service.takeScreenshot()
                if (ok) "Taking screenshot." else "Unable to capture screenshot on this system version."
            }
            "lock" -> {
                val ok = service.lockScreen()
                if (ok) "Locking device screen." else "Unable to lock screen."
            }
            "scroll_down" -> {
                val ok = service.scroll(down = true)
                if (ok) "Scrolled down." else "Could not scroll down."
            }
            "scroll_up" -> {
                val ok = service.scroll(down = false)
                if (ok) "Scrolled up." else "Could not scroll up."
            }
            else -> "System action '$actionType' executed."
        }
    }

    fun evaluateMath(expr: String): String? {
        val clean = expr.replace(Regex("[^0-9.+\\-*/%^()xX\\s]"), "")
            .replace("x", "*", ignoreCase = true)
            .trim()
        if (clean.isBlank()) return null
        return try {
            val result = simpleCalculate(clean)
            if (result != null) {
                val formatted = if (result % 1.0 == 0.0) result.toLong().toString() else String.format(Locale.US, "%.2f", result)
                "$clean = $formatted"
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun simpleCalculate(expression: String): Double? {
        val sanitized = expression.replace(" ", "")
        // Simple 2-operand or basic chained evaluation
        val ops = listOf("+", "-", "*", "/", "%", "^")
        for (op in ops) {
            val idx = sanitized.indexOf(op)
            if (idx > 0 && idx < sanitized.length - 1) {
                val left = sanitized.substring(0, idx).toDoubleOrNull() ?: continue
                val right = sanitized.substring(idx + 1).toDoubleOrNull() ?: continue
                return when (op) {
                    "+" -> left + right
                    "-" -> left - right
                    "*" -> left * right
                    "/" -> if (right != 0.0) left / right else Double.NaN
                    "%" -> left % right
                    "^" -> Math.pow(left, right)
                    else -> null
                }
            }
        }
        return sanitized.toDoubleOrNull()
    }
}
