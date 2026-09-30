package com.example.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import java.util.Locale

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val isSystem: Boolean
)

data class AppLaunchResult(
    val success: Boolean,
    val appName: String,
    val packageName: String? = null,
    val message: String
)

class AppManagerHelper(private val context: Context) {

    companion object {
        private const val TAG = "AppManagerHelper"

        private val DEVANAGARI_APP_MAP = mapOf(
            "यूट्यूब" to "youtube",
            "व्हाट्सएप" to "whatsapp",
            "व्हाट्सऐप" to "whatsapp",
            "वाट्सएप" to "whatsapp",
            "इंस्टाग्राम" to "instagram",
            "इंस्टा" to "instagram",
            "क्रोम" to "chrome",
            "गूगल" to "google",
            "कैमरा" to "camera",
            "सेटिंग्स" to "settings",
            "सेटिंग" to "settings",
            "कैलकुलेटर" to "calculator",
            "गैलरी" to "gallery",
            "फोटो" to "photos",
            "टेलीग्राम" to "telegram",
            "फेसबुक" to "facebook",
            "स्नैपचैट" to "snapchat",
            "स्पॉटिफाई" to "spotify",
            "फ्री फायर" to "free fire",
            "बीजीएमआई" to "bgmi",
            "पबजी" to "pubg",
            "प्ले स्टोर" to "play store",
            "मैप्स" to "maps",
            "जीमेल" to "gmail",
            "क्लॉक" to "clock",
            "घड़ी" to "clock",
            "फाइल्स" to "files",
            "पेटीएम" to "paytm",
            "फोनपे" to "phonepe",
            "गूगल पे" to "gpay"
        )
    }

    private val packageManager: PackageManager = context.packageManager
    private var cachedApps: List<InstalledAppInfo> = emptyList()
    private var lastScanTime: Long = 0

    init {
        scanInstalledApps()
    }

    fun scanInstalledApps(): List<InstalledAppInfo> {
        val now = System.currentTimeMillis()
        if (cachedApps.isNotEmpty() && (now - lastScanTime < 60_000)) {
            return cachedApps
        }

        val appList = mutableListOf<InstalledAppInfo>()
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)

            val seenPackages = mutableSetOf<String>()
            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pkg in seenPackages || pkg == context.packageName) continue
                seenPackages.add(pkg)

                val label = try {
                    info.loadLabel(packageManager).toString().trim()
                } catch (e: Exception) {
                    pkg
                }

                val isSystem = (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                appList.add(InstalledAppInfo(label, pkg, isSystem))
            }

            if (appList.isEmpty()) {
                val allInstalled = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                for (appInfo in allInstalled) {
                    val pkg = appInfo.packageName
                    if (pkg in seenPackages || pkg == context.packageName) continue
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        seenPackages.add(pkg)
                        val label = try {
                            packageManager.getApplicationLabel(appInfo).toString().trim()
                        } catch (e: Exception) {
                            pkg
                        }
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        appList.add(InstalledAppInfo(label, pkg, isSystem))
                    }
                }
            }

            cachedApps = appList.sortedBy { it.label.lowercase(Locale.ROOT) }
            lastScanTime = now
            Log.d(TAG, "Detected ${cachedApps.size} installed apps on device.")
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning apps: ${e.message}", e)
        }
        return cachedApps
    }

    fun findAndLaunchApp(query: String, isHindi: Boolean = true): AppLaunchResult {
        val cleanQuery = cleanAppName(query)
        if (cleanQuery.isBlank() || cleanQuery.length < 2) {
            return AppLaunchResult(
                success = false,
                appName = query,
                message = "Ji, kuch nahi mila… thoda saaf bolna?"
            )
        }

        handleSpecialSystemIntents(cleanQuery, isHindi)?.let { return it }

        val apps = scanInstalledApps()
        val alias = getKnownPackageAlias(cleanQuery)

        // 1. Check exact label match first
        var matchedApp = apps.firstOrNull {
            it.label.equals(cleanQuery, ignoreCase = true) ||
                cleanAppName(it.label).equals(cleanQuery, ignoreCase = true)
        }

        // 2. Check known package alias (e.g. YouTube, WhatsApp, Instagram, Chrome, Spotify, etc.)
        if (matchedApp == null && alias != null) {
            matchedApp = apps.firstOrNull { it.packageName.equals(alias, ignoreCase = true) }
            if (matchedApp == null) {
                val directResult = tryDirectPackageLaunchOrSpecial(alias, cleanQuery, isHindi)
                if (directResult != null) return directResult
            }
        }

        // 3. Strict whole-word or prefix label match (never loose substring matching on short words!)
        if (matchedApp == null && cleanQuery.length >= 3) {
            val queryTokens = cleanQuery.split(" ").filter { it.length >= 3 }
            matchedApp = apps.firstOrNull { app ->
                val appClean = cleanAppName(app.label)
                val appTokens = appClean.split(" ").filter { it.length >= 3 }
                appClean.isNotEmpty() && (
                    appClean == cleanQuery ||
                        (cleanQuery.length >= 4 && appClean.startsWith(cleanQuery)) ||
                        (queryTokens.isNotEmpty() && appTokens.isNotEmpty() && queryTokens.all { qt -> appTokens.any { at -> at == qt } })
                    )
            }
        }

        if (matchedApp != null) {
            return launchPackageDirectly(matchedApp.packageName, matchedApp.label, isHindi)
        }

        if (alias != null) {
            val directResult = tryDirectPackageLaunchOrSpecial(alias, cleanQuery, isHindi)
            if (directResult != null) return directResult
        }

        // Rule: Agar app installed nahi → bolo: "Ji, yeh app install nahi hai, Play Store se install karun?"
        return AppLaunchResult(
            success = false,
            appName = cleanQuery,
            packageName = null,
            message = "Ji, yeh app install nahi hai, Play Store se install karun?"
        )
    }

    fun openPlayStoreForApp(appName: String): String {
        return trySearchOnPlayStore(appName, true).message
    }

    private fun launchPackageDirectly(pkg: String, label: String, isHindi: Boolean): AppLaunchResult {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
                AppLaunchResult(
                    success = true,
                    appName = label,
                    packageName = pkg,
                    message = "Ji, $label khol diya hai jaan 💕"
                )
            } else {
                AppLaunchResult(
                    success = false,
                    appName = label,
                    packageName = null,
                    message = "Ji, yeh app install nahi hai, Play Store se install karun?"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $label: ${e.message}")
            AppLaunchResult(
                success = false,
                appName = label,
                packageName = pkg,
                message = "Ji, $label open karne mein thodi dikkat aayi."
            )
        }
    }

    private fun handleSpecialSystemIntents(cleanQuery: String, isHindi: Boolean): AppLaunchResult? {
        when {
            cleanQuery == "camera" || cleanQuery == "selfie camera" -> {
                return try {
                    val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Camera", null, "Ji, Camera khol diya hai jaan 💕")
                } catch (e: Exception) { null }
            }
            cleanQuery == "settings" || cleanQuery == "setting" -> {
                return try {
                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Settings", null, "Ji, Settings khol di hai jaan 💕")
                } catch (e: Exception) { null }
            }
            cleanQuery == "dialer" || cleanQuery == "dial pad" || cleanQuery == "phone dialer" -> {
                return try {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Phone Dialer", null, "Ji, Phone Dialer khol diya hai jaan 💕")
                } catch (e: Exception) { null }
            }
            cleanQuery == "play store" || cleanQuery == "playstore" -> {
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Play Store", null, "Ji, Play Store khol diya hai jaan 💕")
                } catch (e: Exception) { null }
            }
        }
        return null
    }

    private fun tryDirectPackageLaunchOrSpecial(alias: String, query: String, isHindi: Boolean): AppLaunchResult? {
        val launchIntent = packageManager.getLaunchIntentForPackage(alias)
        if (launchIntent != null) {
            return launchPackageDirectly(alias, query, isHindi)
        }
        // Web fallback for known core apps when opened on emulator
        when {
            alias.contains("youtube") -> {
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "YouTube", alias, "Ji, YouTube khol diya hai jaan 💕")
                } catch (e: Exception) { null }
            }
            alias.contains("chrome") -> {
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Chrome", alias, "Ji, Chrome khol diya hai jaan 💕")
                } catch (e: Exception) { null }
            }
            alias.contains("whatsapp") -> {
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "WhatsApp", alias, "Ji, WhatsApp khol diya hai jaan 💕")
                } catch (e: Exception) { null }
            }
            alias.contains("instagram") -> {
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AppLaunchResult(true, "Instagram", alias, "Ji, Instagram khol diya hai jaan 💕")
                } catch (e: Exception) { null }
            }
        }
        return null
    }

    private fun trySearchOnPlayStore(cleanQuery: String, isHindi: Boolean): AppLaunchResult {
        return try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=$cleanQuery")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            AppLaunchResult(
                success = true,
                appName = cleanQuery,
                message = "Ji, yeh raha jaan, Play Store pe $cleanQuery khol diya 💕"
            )
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=$cleanQuery&c=apps")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            AppLaunchResult(
                success = true,
                appName = cleanQuery,
                message = "Ji, yeh raha jaan, Play Store pe $cleanQuery khol diya 💕"
            )
        }
    }

    fun cleanAppName(name: String): String {
        var s = name.lowercase(Locale.ROOT).trim()
        for ((devanagari, roman) in DEVANAGARI_APP_MAP) {
            s = s.replace(devanagari, roman)
        }
        s = s.replace("खोलो", " kholo ")
            .replace("खोल दो", " khol do ")
            .replace("चलाओ", " chalao ")
            .replace("चालू करो", " chalu karo ")
            .replace("ओपन करो", " open karo ")
            .replace("ऐप", " app ")

        val prefixes = listOf(
            "phone pe ", "phone par ", "phone me ", "phone mein ", "mere phone me ", "mere phone pe ",
            "can you please open ", "can you open ", "please open ", "open ", "launch ", "start ", "run ",
            "kripya open karein ", "open kar do ", "open karo ", "chala do ", "chalao ", "chalu karo ",
            "chalu kar do ", "khol do ", "kholo ", "kholna hai ", "zara "
        )
        for (p in prefixes) {
            if (s.startsWith(p)) {
                s = s.substring(p.length).trim()
                break
            }
        }
        val suffixes = listOf(
            " app kholo", " app open karo", " app open", " pe kholo", " par kholo",
            " open kar do", " open karo", " khol do", " kholo", " chala do", " chalao", " chalu karo",
            " chalu kar do", " application open", " ko open karo",
            " ko kholo", " chalu", " laga do", " chalao please", " open", " please", " sir", " bhai",
            " kar do", " karo", " karna hai", " kholna", " jaan", " baby", " shona", " na"
        )
        for (sfx in suffixes) {
            if (s.endsWith(sfx)) {
                s = s.removeSuffix(sfx).trim()
                break
            }
        }
        s = s.replace(Regex("\\b(app|application|please|sir|bhai|mera|meri|mujhe|ko|ek|phone|pe|par|zara|jaan|baby|shona)\\b"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun getKnownPackageAlias(query: String): String? {
        val q = query.replace(" ", "").lowercase(Locale.ROOT)
        return when {
            q == "youtube" || q == "yt" -> "com.google.android.youtube"
            q == "whatsapp" || q == "wa" -> "com.whatsapp"
            q == "instagram" || q == "insta" || q == "ig" -> "com.instagram.android"
            q == "telegram" || q == "tg" -> "org.telegram.messenger"
            q == "freefire" || q == "ff" -> "com.dts.freefireth"
            q == "freefiremax" -> "com.dts.freefiremax"
            q == "bgmi" || q == "pubg" -> "com.pubg.imobile"
            q == "facebook" || q == "fb" -> "com.facebook.katana"
            q == "chrome" || q == "googlechrome" -> "com.android.chrome"
            q == "snapchat" || q == "snap" -> "com.snapchat.android"
            q == "spotify" -> "com.spotify.music"
            q == "calculator" || q == "calc" -> "com.google.android.calculator"
            q == "clock" || q == "ghadi" -> "com.google.android.deskclock"
            q == "gallery" || q == "photos" -> "com.google.android.apps.photos"
            q == "files" || q == "filemanager" -> "com.google.android.apps.nbu.files"
            q == "paytm" -> "net.one97.paytm"
            q == "phonepe" -> "com.phonepe.app"
            q == "gpay" || q == "googlepay" -> "com.google.android.apps.nbu.paisa.user"
            q == "maps" || q == "googlemaps" -> "com.google.android.apps.maps"
            q == "gmail" -> "com.google.android.gm"
            else -> null
        }
    }
}
