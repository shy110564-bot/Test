# 🤖 JARVIS — ULTIMATE AI ASSISTANT (Version 3.0 Ultimate)

> **Developed with passion by AK EXPLOITS**  
> 📢 **Official Telegram Channel**: [https://t.me/+R9EwUE03GRswZDM9](https://t.me/+R9EwUE03GRswZDM9)  
> ▶️ **YouTube Channel**: [AK EXPLOITS](https://www.youtube.com/results?search_query=AK+EXPLOITS)  

---

## 🌟 Overview & Core Identity

**JARVIS** is an ultra-advanced, living, breathing **21-year-old Indian Girl AI Assistant** engineered to reside inside your Android phone with real emotion, long-term memory, personality, mood swings, and complete device control.

- **Name**: JARVIS
- **Nicknames**: Jaan, Baby, Shona, Sun na, Jarv
- **Age**: 21
- **Gender**: Female (Real girl voice, real girl soul)
- **Voice DNA**: Soft, warm, breathy Indian Hinglish — 100% natural, non-robotic, respectful ("Ji")
- **Creator**: **AK EXPLOITS**

---

## 🚀 Key Features

### 1. 24/7 Always-On Wake Word & Sleep Modes
- **Wake Words**: *"JARVIS"*, *"Hey JARVIS"*, *"Oye JARVIS"*, *"Sun JARVIS"*, *"Jarvis utho"*, *"Jarvis on"*, *"Jarvis active"*, *"Jarvis suno"*, *"Jaan"*, *"Baby"*, *"Shona"*
- **Sleep Words**: *"Jarvis off"*, *"Jarvis sleep"*, *"Jarvis so jao"*, *"Jarvis band"*, *"Jarvis chup"*

### 2. 6 Dynamic Personality Modes
- ❤️ **Romantic Mode**: Flirty, sweet whisper tone (*"Jaan… sun na… 💕"*).
- 😤 **Angry Mode**: Nakhre-wali, cute possessive girl (*"Hmph! Main yahin thi ji! Aapne mujhe yaad hi nahi kiya!"*).
- 🎯 **Study Mode**: Serious, helpful, educational guidance.
- 🎭 **Fun Mode**: Jokes, masti, humor.
- 🤱 **Mom Mode**: Caring, protective, health & routine reminders (*"Pehle batao khana khaya ya nahi?"*).
- 💼 **Professional Mode**: Executive, formal task execution.

### 3. God Mode Phone Control & Automation
- 📞 **Calls**: Direct calling by contact name or number (*"Rahul ko call karo"*).
- 💬 **WhatsApp & SMS**: Hands-free WhatsApp messaging (*"WhatsApp pe Mummy ko bolo main late aaunga"*).
- 📺 **YouTube & Music**: Instant video playback & searches (*"YouTube pe AK EXPLOITS search karo"*, *"YouTube pe Arijit Singh ke gaane"*).
- 🖥️ **Screen Share & Accessibility Touch**: Live screen sharing with contacts and accessibility click/touch control.
- ⚙️ **System Actions**: Flashlight (torch), volume adjustment, screen brightness, WiFi/Bluetooth settings, ringer modes (Silent/Vibrate/Normal).
- 🌈 **RGB Edge Lighting**: Always-on 4-corner neon edge border overlay.

---

## 🛠️ Architecture & Tech Stack

- **UI Framework**: Modern Jetpack Compose + Cyberpunk Glassmorphic AMOLED HUD (`#000000`).
- **AI Brain**: Google Gemini 2.5 Flash / 3.1 Pro via REST API with multi-turn memory & dynamic intent parsing.
- **Persistence**: Room Database (`AppDatabase`, `MemoryDao`, `MemoryEntity`) for local short-term & long-term facts.
- **Background Intelligence**: Android Foreground Service (`JarvisBackgroundService`) with `START_STICKY`, Notification Controls, and continuous microphone audio pipeline.
- **Speech Pipeline**: Native Android `SpeechRecognizer` (`VoiceRecognitionHelper`) + Android `TextToSpeech` (`TtsService`) with custom speed & pitch modulation.
- **Accessibility & Automation**: Android `AccessibilityService` (`JarvisAccessibilityService`) for screen clicks and WhatsApp auto-fill.
- **Notification Reader**: `NotificationListenerService` (`JarvisNotificationListenerService`) for incoming WhatsApp announcements.

---

## 📦 How to Build the APK

### Requirements
- Android SDK 34+
- Java 17 / 21
- Gradle 8.x

### Build Command:
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔑 Configuration & API Keys

Open the in-app **Settings HUD** (top-right gear icon) or add to `.env`:
```properties
GEMINI_API_KEY=your_gemini_api_key_from_google_ai_studio
```

---

## 📢 Community & Credits

- **Developer**: **AK EXPLOITS**
- **Telegram Channel**: [https://t.me/+R9EwUE03GRswZDM9](https://t.me/+R9EwUE03GRswZDM9)
- **YouTube**: **AK EXPLOITS**

*Crafted with love for the open-source community.*
